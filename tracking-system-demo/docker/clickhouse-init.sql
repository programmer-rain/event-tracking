-- ============================================================
-- ClickHouse 初始化脚本（docker 首次启动自动执行）
-- 教学点汇总：
--   1. LowCardinality：低基数字段字典编码，压缩率与查询速度双提升
--   2. PARTITION BY 按月分区：查询分区裁剪 + TTL 删历史零成本
--   3. ORDER BY 排序键：高频过滤维度放前面
--   4. ReplacingMergeTree：排序键含 event_id，后台 merge 去重兜底
--      （注意：merge 时机不可控，幂等主力仍是 eventId + Redis SETNX）
--   5. 物化视图预聚合：把"算"前置到写入时，看板查询永远不扫明细
-- ============================================================

CREATE DATABASE IF NOT EXISTS mall_track;

-- 明细表
CREATE TABLE IF NOT EXISTS mall_track.track_events
(
    event_id    String,
    app_id      LowCardinality(String),
    event_type  LowCardinality(String),
    event_name  LowCardinality(String),
    page_id     LowCardinality(String),
    item_id     String,
    user_id     String,
    device_id   String,
    session_id  String,
    client_ts   Int64,
    server_ts   Int64,
    event_time  DateTime64(3, 'Asia/Shanghai'),
    event_date  Date,
    ip          String,
    ua          String,
    props       String,
    ingest_time DateTime64(3) DEFAULT now64(3)
)
ENGINE = ReplacingMergeTree(ingest_time)
PARTITION BY toYYYYMM(event_date)
ORDER BY (page_id, event_type, event_time, event_id)
TTL event_date + INTERVAL 90 DAY;

-- 分钟级 PV/UV 物化视图：写入时自动预聚合
CREATE MATERIALIZED VIEW IF NOT EXISTS mall_track.mv_pv_uv_1m
ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(dt)
ORDER BY (dt, page_id, minute)
AS SELECT
    toStartOfMinute(event_time) AS minute,
    toDate(event_time)          AS dt,
    page_id,
    countState()                AS pv_state,
    uniqState(if(user_id != '', concat('u:', user_id), concat('d:', device_id))) AS uv_state
FROM mall_track.track_events
GROUP BY minute, dt, page_id;

-- 查询示例（毫秒级返回）：
-- SELECT sumMerge(pv_state) AS pv, uniqMerge(uv_state) AS uv
-- FROM mall_track.mv_pv_uv_1m
-- WHERE page_id = 'product_list' AND dt = today();
