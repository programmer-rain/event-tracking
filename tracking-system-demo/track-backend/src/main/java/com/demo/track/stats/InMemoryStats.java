package com.demo.track.stats;

import com.demo.track.model.RealtimeOverview;
import com.demo.track.model.TrackEvent;
import com.demo.track.util.Dates;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 内存实时指标（local 模式，零依赖）。
 *
 * 教学点：这里为了演示简单，UV 用 Set 精确去重；
 * 生产环境海量 UV 必须用 HyperLogLog（12KB 扛亿级去重，误差 0.81%），
 * 见 RedisStats 的实现。
 */
@Profile("local")
@Component
public class InMemoryStats implements RealtimeStats {

    /** date -> PV 计数 */
    private final ConcurrentHashMap<String, LongAdder> pagePv = new ConcurrentHashMap<>();
    /** date -> UV 去重集合（distinctId） */
    private final ConcurrentHashMap<String, Set<String>> pageUv = new ConcurrentHashMap<>();
    /** date -> 点击计数 */
    private final ConcurrentHashMap<String, LongAdder> clicks = new ConcurrentHashMap<>();
    /** date -> 曝光计数 */
    private final ConcurrentHashMap<String, LongAdder> exposures = new ConcurrentHashMap<>();

    @Override
    public void record(List<TrackEvent> events) {
        for (TrackEvent e : events) {
            if (e.getServerTs() == null || e.getEventType() == null) {
                continue;
            }
            String date = Dates.dayOf(e.getServerTs());
            switch (e.getEventType()) {
                case "page_view" -> {
                    counter(pagePv, date).increment();
                    uvSet(date).add(e.distinctId());
                }
                case "click" -> counter(clicks, date).increment();
                case "exposure" -> counter(exposures, date).increment();
                default -> {
                }
            }
        }
    }

    @Override
    public RealtimeOverview overview(String date) {
        Set<String> uv = pageUv.get(date);
        return new RealtimeOverview(date,
                sum(pagePv, date),
                uv == null ? 0 : uv.size(),
                sum(clicks, date),
                sum(exposures, date));
    }

    private LongAdder counter(ConcurrentHashMap<String, LongAdder> map, String key) {
        return map.computeIfAbsent(key, k -> new LongAdder());
    }

    private Set<String> uvSet(String date) {
        return pageUv.computeIfAbsent(date, k -> ConcurrentHashMap.newKeySet());
    }

    private long sum(ConcurrentHashMap<String, LongAdder> map, String key) {
        LongAdder v = map.get(key);
        return v == null ? 0 : v.sum();
    }
}
