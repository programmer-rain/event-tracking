package com.demo.track.stats;

import com.demo.track.model.RealtimeOverview;
import com.demo.track.model.TrackEvent;
import com.demo.track.util.Dates;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Redis 实时指标（full 模式）。
 *
 * 教学点：
 *  - PV 用 INCR 计数器，O(1)
 *  - UV 用 HyperLogLog（PFADD）：每个 key 固定 12KB，误差 0.81%，
 *    对比 Set 存 3000 万用户约 720MB，这就是"用精度换内存"的经典案例
 */
@Profile("full")
@Component
public class RedisStats implements RealtimeStats {

    private final StringRedisTemplate redis;

    public RedisStats(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void record(List<TrackEvent> events) {
        for (TrackEvent e : events) {
            if (e.getServerTs() == null || e.getEventType() == null) {
                continue;
            }
            String date = Dates.dayOf(e.getServerTs());
            switch (e.getEventType()) {
                case "page_view" -> {
                    redis.opsForValue().increment("track:pv:" + date);
                    redis.opsForHyperLogLog().add("track:uv:" + date, e.distinctId());
                }
                case "click" -> redis.opsForValue().increment("track:click:" + date);
                case "exposure" -> redis.opsForValue().increment("track:expose:" + date);
                default -> {
                }
            }
        }
    }

    @Override
    public RealtimeOverview overview(String date) {
        return new RealtimeOverview(date,
                readLong("track:pv:" + date),
                Optional.ofNullable(redis.opsForHyperLogLog().size("track:uv:" + date)).orElse(0L),
                readLong("track:click:" + date),
                readLong("track:expose:" + date));
    }

    private long readLong(String key) {
        String v = redis.opsForValue().get(key);
        return v == null ? 0 : Long.parseLong(v);
    }
}
