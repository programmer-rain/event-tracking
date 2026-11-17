package com.demo.track.ingest;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 幂等去重（full 模式）。
 * 教学点：SETNX + 过期时间。更严谨的写法是用 Lua 脚本保证
 * SETNX 与 EXPIRE 的原子性，避免极端情况下只设了键没设过期。
 */
@Profile("full")
@Component
public class RedisDedupService implements DedupService {

    private final StringRedisTemplate redis;

    public RedisDedupService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean firstSeen(String eventId) {
        Boolean ok = redis.opsForValue()
                .setIfAbsent("dup:" + eventId, "1", Duration.ofHours(36));
        return Boolean.TRUE.equals(ok);
    }
}
