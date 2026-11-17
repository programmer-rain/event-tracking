package com.demo.track.ingest;

import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机固定窗口限流器（local 模式，零依赖）。
 * 教学点：窗口桶 = 当前秒 / 窗口大小，换桶即重置计数。
 */
@Profile("local")
@Component
public class LocalRateLimiter implements RateLimiter {

    private static final class Window {
        long bucket;
        int count;
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean tryAcquire(String key, int limit, int seconds) {
        long bucket = System.currentTimeMillis() / 1000 / Math.max(1, seconds);
        Window w = windows.computeIfAbsent(key, k -> {
            Window x = new Window();
            x.bucket = bucket;
            return x;
        });
        synchronized (w) {
            if (w.bucket != bucket) {
                w.bucket = bucket;
                w.count = 0;
            }
            return ++w.count <= limit;
        }
    }

    /** 定期清理限流窗口，防止演示久了内存缓慢增长 */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        if (windows.size() > 10_000) {
            windows.clear();
        }
    }
}
