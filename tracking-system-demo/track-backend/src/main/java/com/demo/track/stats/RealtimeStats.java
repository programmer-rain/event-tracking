package com.demo.track.stats;

import com.demo.track.model.RealtimeOverview;
import com.demo.track.model.TrackEvent;

import java.util.List;

/**
 * 实时指标统计抽象。
 * - local 模式：内存计数（InMemoryStats）
 * - full 模式：Redis INCR + HyperLogLog（RedisStats）
 */
public interface RealtimeStats {

    /** 批量消费后更新实时指标 */
    void record(List<TrackEvent> events);

    /** 查询某天概览：页面 PV / UV / 点击 / 曝光 */
    RealtimeOverview overview(String date);
}
