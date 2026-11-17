package com.demo.track.ingest;

import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 固定窗口限流器（full 模式，集群级生效）。
 * 教学点：INCR + EXPIRE 组合；第一次计数时设置过期时间。
 */
@Profile("full")
@Component
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public boolean tryAcquire(String key, int limit, int seconds) {
        String redisKey = "track:rate:" + key;
        Long current = redis.opsForValue().increment(redisKey);
        if (current != null && current == 1L) {
            redis.expire(redisKey, Duration.ofSeconds(seconds));
        }
        return current != null && current <= limit;
    }
}
