package com.demo.track.store;

import com.demo.track.model.TopItem;
import com.demo.track.model.TrackEvent;
import com.demo.track.util.Dates;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * ClickHouse 明细存储（full 模式）。
 *
 * 教学点：
 *  1. 批量写入：一批数据一条 INSERT，性能比单条写高百倍
 *  2. 表设计见 docker/clickhouse-init.sql：
 *     LowCardinality 低基数列、按天分区、排序键、TTL 90 天、
 *     ReplacingMergeTree 按 event_id 合并去重兜底、物化视图预聚合
 */
@Profile("full")
@Repository
public class ClickHouseTrackStore implements TrackStore {

    private static final String INSERT_SQL = """
            INSERT INTO mall_track.track_events
            (event_id, app_id, event_type, event_name, page_id, item_id, user_id, device_id,
             session_id, client_ts, server_ts, event_time, event_date, ip, ua, props)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String TOP_ITEMS_SQL = """
            SELECT item_id, count() AS cnt
            FROM mall_track.track_events
            WHERE event_type = 'click' AND item_id != ''
              AND event_date >= today() - 7
            GROUP BY item_id
            ORDER BY cnt DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ClickHouseTrackStore(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Override
    public void batchInsert(List<TrackEvent> events) {
        jdbc.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                TrackEvent e = events.get(i);
                LocalDateTime time = Instant.ofEpochMilli(e.getServerTs())
                        .atZone(Dates.ZONE).toLocalDateTime();
                ps.setString(1, e.getEventId());
                ps.setString(2, e.getAppId());
                ps.setString(3, e.getEventType());
                ps.setString(4, e.getEventName());
                ps.setString(5, e.getPageId());
                ps.setString(6, e.getItemId());
                ps.setString(7, e.getUserId());
                ps.setString(8, e.getDeviceId());
                ps.setString(9, e.getSessionId());
                ps.setLong(10, e.getClientTs() == null ? 0L : e.getClientTs());
                ps.setLong(11, e.getServerTs());
                ps.setTimestamp(12, Timestamp.valueOf(time));
                ps.setDate(13, Date.valueOf(time.toLocalDate()));
                ps.setString(14, e.getIp());
                ps.setString(15, e.getUa());
                ps.setString(16, propsJson(e));
            }

            @Override
            public int getBatchSize() {
                return events.size();
            }
        });
    }

    @Override
    public List<TopItem> topClickItems(int limit) {
        return jdbc.query(TOP_ITEMS_SQL,
                (rs, rowNum) -> new TopItem(rs.getString(1), rs.getLong(2)),
                limit);
    }

    private String propsJson(TrackEvent e) {
        try {
            return objectMapper.writeValueAsString(e.getProps() == null ? Map.of() : e.getProps());
        } catch (Exception ex) {
            return "{}";
        }
    }
}
