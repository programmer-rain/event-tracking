package com.demo.track.ingest;

/**
 * 限流器抽象。
 * local 模式用单机固定窗口，full 模式用 Redis 固定窗口。
 * 教学点：固定窗口实现最简单，但窗口临界点可能有流量突刺；
 * 生产环境建议 Redis + Lua 滑动窗口或令牌桶。
 */
public interface RateLimiter {

    /**
     * @param key     限流键，如 ip:127.0.0.1
     * @param limit   窗口内允许的最大请求数
     * @param seconds 窗口大小（秒）
     * @return true 放行，false 限流
     */
    boolean tryAcquire(String key, int limit, int seconds);
}
