package com.demo.track.store;

import com.demo.track.model.TopItem;
import com.demo.track.model.TrackEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.LongAdder;

/**
 * 内存明细存储（local 模式，零依赖）。
 * 教学点：演示用只保留最近 10 万条明细；
 * 生产环境明细进 ClickHouse / Doris / StarRocks，不会放内存，更不会放 MySQL。
 */
@Profile("local")
@Component
public class InMemoryTrackStore implements TrackStore {

    private static final int MAX_DETAILS = 100_000;

    /** 明细环形缓冲：超出上限丢最旧 */
    private final Deque<TrackEvent> details = new ConcurrentLinkedDeque<>();

    /** 商品点击计数：itemId -> 次数 */
    private final ConcurrentHashMap<String, LongAdder> clickCounts = new ConcurrentHashMap<>();

    @Override
    public void batchInsert(List<TrackEvent> events) {
        for (TrackEvent e : events) {
            details.addLast(e);
            if (details.size() > MAX_DETAILS) {
                details.pollFirst();
            }
            if ("click".equals(e.getEventType()) && e.getItemId() != null) {
                clickCounts.computeIfAbsent(e.getItemId(), k -> new LongAdder()).increment();
            }
        }
    }

    @Override
    public List<TopItem> topClickItems(int limit) {
        return clickCounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, LongAdder>>comparingLong(en -> en.getValue().sum()).reversed())
                .limit(limit)
                .map(en -> new TopItem(en.getKey(), en.getValue().sum()))
                .toList();
    }

    /** 当前缓存明细条数（调试用） */
    public int detailSize() {
        return details.size();
    }
}
