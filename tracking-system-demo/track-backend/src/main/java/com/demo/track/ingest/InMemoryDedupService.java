package com.demo.track.ingest;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存幂等去重（local 模式，零依赖）。
 * 教学点：生产环境用 Redis SETNX（见 RedisDedupService），
 * 窗口给足（36h），覆盖前端重试 + 跨天延迟补发。
 */
@Profile("local")
@Component
public class InMemoryDedupService implements DedupService {

    private static final long TTL_MILLIS = Duration.ofHours(36).toMillis();

    /** eventId -> 首次出现时间 */
    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();

    @Override
    public boolean firstSeen(String eventId) {
        return seen.putIfAbsent(eventId, System.currentTimeMillis()) == null;
    }

    /** 定期清理过期幂等键，防止内存膨胀 */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        long deadline = System.currentTimeMillis() - TTL_MILLIS;
        seen.entrySet().removeIf(e -> e.getValue() < deadline);
    }
}
