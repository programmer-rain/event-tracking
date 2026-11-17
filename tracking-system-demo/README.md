# 电商埋点系统教学 Demo

一个可以直接跑起来的埋点系统教学项目：**前端 Vue3 埋点 SDK + 后端 Spring Boot 接收链路 + 实时 PV/UV 看板**，覆盖埋点系统最核心的设计决策——不丢、不重、不卡、算得准。

配套文章：《电商埋点系统实战：10 万 QPS 下如何做到不丢、不重、PV/UV 秒级出数，附完整代码》。

更多项目请关注公众号：Rain的Java大神之路

## 一、架构总览

![架构总览.png](%E6%9E%B6%E6%9E%84%E6%80%BB%E8%A7%88.png)

## 二、两种运行模式

| | local 模式（默认） | full 模式 |
|---|---|---|
| 外部依赖 | **无**，开箱即跑 | Docker：Redis + Kafka + ClickHouse |
| 缓冲层 | 内存有界队列 + 后台攒批线程 | Kafka |
| 幂等去重 | 内存 Map（36h TTL） | Redis SETNX |
| 明细存储 | 内存环形缓冲（10 万条） | ClickHouse（ReplacingMergeTree + 物化视图） |
| 实时指标 | 内存计数 | Redis INCR + HyperLogLog |
| 适合 | 课堂演示、理解链路 | 体验完整生产架构 |

两种模式共用同一套接口协议和接收层代码（校验 / 限流 / 幂等 / 补字段），切换只需一个 profile 参数——这本身就是"面向接口分层"的教学示范。

## 三、目录结构

```
tracking-system-demo/
├── docker-compose.yml              # full 模式：Redis + Kafka + ClickHouse
├── docker/
│   └── clickhouse-init.sql         # 建库建表 + 物化视图（含大量教学注释）
├── scripts/
│   └── load_gen.py                 # 压测脚本（验证限流 / 幂等去重 / 背压）
├── track-frontend/                 # Vue3 + Vite + TS
│   └── src/
│       ├── track/
│       │   ├── tracker.ts          # ★ SDK 核心：攒批 / 降级链 / 弱网兜底 / 一致性采样
│       │   └── plugin.ts           # ★ v-track 指令 + 全局事件委托 + 曝光观察器
│       ├── views/
│       │   ├── ProductPage.vue     # 商品页（声明式 + 编程式埋点示例）
│       │   └── DashboardPage.vue   # 实时看板（PV/UV/排行/接入状态）
│       ├── components/
│       │   └── TrackDebugPanel.vue # ★ SDK 调试面板（观察队列 / 攒批 / 重试）
│       ├── router.ts               # 路由自动 PV（afterEach）
│       └── main.ts                 # SDK 初始化与插件装配
└── track-backend/                  # Spring Boot 3 + Java 17
    └── src/main/java/com/demo/track/
        ├── ingest/                 # ★ 接收层：限流 / 幂等 / 有界队列 / Kafka 投递
        │   ├── TrackController.java    # 接收接口（铁律：立刻返回，绝不查库）
        │   ├── LocalRateLimiter.java   # 单机固定窗口限流
        │   ├── RedisRateLimiter.java   # Redis 固定窗口限流
        │   ├── InMemoryDedupService.java / RedisDedupService.java  # 幂等去重
        │   ├── InMemoryPipeline.java   # ★ 有界队列 + 背压 + 攒批（local）
        │   └── KafkaPipeline.java      # 异步投递 Kafka（full）
        ├── consume/TrackConsumer.java  # 批量消费 + 清洗 + 手动 ack（full）
        ├── store/                    # 明细存储：内存 / ClickHouse
        ├── stats/                    # 实时指标：内存 / Redis HLL
        ├── api/AnalyticsController.java # 看板查询接口
        └── config/KafkaConfig.java      # 批量消费容器配置
```

## 四、快速开始

### 环境要求

- JDK 17+、Maven 3.6+
- Node.js 18+
- Docker（仅 full 模式需要）

### 第 1 步：启动后端（local 模式，零依赖）

```bash
cd track-backend
mvn spring-boot:run
```

### 第 2 步：启动前端

```bash
cd track-frontend
npm install
npm run dev
```

浏览器打开 `http://localhost:5173`，完成。

### 第 3 步（可选）：切换 full 模式体验完整架构

```bash
# 1. 启动中间件（Redis + Kafka + ClickHouse，首次会自动建表）
docker compose up -d

# 2. 以 full profile 启动后端
cd track-backend
mvn spring-boot:run -Dspring-boot.run.profiles=full
```

full 模式下数据流向：接收接口 → Kafka → 批量消费者 → ClickHouse 明细 + Redis 实时指标。

## 五、课堂演示脚本（建议按顺序操作）

1. **观察攒批**：打开商品页，点两下按钮，看右下角调试面板——事件先进"内存队列"，攒够 10 条或 3 秒后才变成"已发送批次"。
2. **观察曝光口径**：快速滚动页面再滚回来，对比"有效曝光总数"——停留不足 800ms 的不算曝光。
3. **观察弱网兜底**：调试面板点「模拟弱网」，继续点击页面。观察"失败批次"和"本地暂存待补发"增长、退避间隔翻倍；点「恢复网络」后数据自动补发。
4. **观察页面关闭兜底**：点「立即 flush」旁的逻辑之外，直接关闭/刷新标签页——SDK 会用 `sendBeacon` 把队列里的事件兜底发出（"sendBeacon 兜底次数"增长）。
5. **观察幂等与限流**：运行 `python scripts/load_gen.py 5000 8`，打开看板的"接入层状态"——`rejected` 增长（重复事件被去重）；把并发调到 50 以上可触发 429 限流。
6. **（full 模式）观察 Kafka 链路**：`docker compose logs -f track-kafka` 看消息流入；用 `clickhouse client` 查询 `SELECT count() FROM mall_track.track_events` 看明细落库。

## 六、教学点 → 代码映射表

| 教学点 | 位置 |
|---|---|
| eventId 全链路幂等 | `tracker.ts` 生成 → `TrackController` SETNX 去重 → Kafka 分区键 → CK 排序键兜底 |
| 双阈值攒批（数量 + 时间） | `tracker.ts` `track()/flush()`、`InMemoryPipeline.drainLoop()` |
| sendBeacon 的坑与降级链 | `tracker.ts` `flush()`（Blob + text/plain 规避 CORS 预检） |
| 页面关闭不丢点 | `tracker.ts` `bindLifecycle()`（pagehide / visibilitychange） |
| 弱网兜底：落盘 + 指数退避 + 抖动 | `tracker.ts` `savePending()/restorePending()` |
| 一致性采样（防 UV 失真） | `tracker.ts` `sampled()/hash()` |
| 捕获阶段事件委托 | `plugin.ts` `install()` |
| 有效曝光 = 面积 50% + 停留 800ms | `plugin.ts` IntersectionObserver |
| 路由自动 PV + 重复上报踩坑 | `router.ts` `setupRouterTrack()` |
| 接收层铁律：立刻返回不查库 | `TrackController.collect()` |
| 固定窗口限流及其临界突刺 | `LocalRateLimiter` / `RedisRateLimiter` |
| 有界队列背压：宁可丢新不拖垮库 | `InMemoryPipeline.submit()` |
| Kafka 削峰 + 分区键选择 | `KafkaPipeline`、`application-full.yml` |
| 批量消费 + 手动 ack + 爬虫清洗 | `TrackConsumer`、`KafkaConfig` |
| HLL：12KB 扛亿级 UV（误差 0.81%） | `RedisStats`（对比 `InMemoryStats` 的 Set） |
| distinctId 口径：u:/d: 前缀防碰撞 | `TrackEvent.distinctId()` |
| ClickHouse 表设计五件套 | `docker/clickhouse-init.sql` |
| 可观测性：accepted/rejected/dropped | `IngestMetrics` + 看板"接入层状态" |

## 七、常见问题

**Q：前端请求 404 / 连不上后端？**
确认后端跑在 8080 端口；开发环境靠 `vite.config.ts` 的 proxy 转发 `/api`。

**Q：full 模式启动报 Kafka 连接失败？**
`docker compose ps` 确认三个容器都是 running；Kafka 首次启动需要几秒初始化。

**Q：ClickHouse 里查不到数据？**
确认 `docker/clickhouse-init.sql` 已执行（`SHOW TABLES FROM mall_track`），再看后端日志有无写入异常。

**Q：本机只有 JDK 8？**
项目要求 JDK 17+（Spring Boot 3）。可用 `JAVA_HOME` 指向 17 再运行 Maven。

**Q：想清空演示数据？**
local 模式重启后端即可；full 模式执行 `TRUNCATE TABLE mall_track.track_events` + `FLUSHALL`（Redis）。

## 八、延伸方向

- 把 `InMemoryStats` 换成手写一个简化版 HyperLogLog，理解概率计数原理
- 给 `TrackController` 加 Lua 版滑动窗口限流，对比固定窗口的临界突刺
- 接入 Flink：消费同一个 Kafka topic，做分钟级窗口 PV/UV，体会 Lambda 架构
- 给 SDK 加 `navigator.connection` 感知，弱网自动降低采样率
