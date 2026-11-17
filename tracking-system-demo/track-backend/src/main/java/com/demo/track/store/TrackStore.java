package com.demo.track.store;

import com.demo.track.model.TopItem;
import com.demo.track.model.TrackEvent;

import java.util.List;

/**
 * 明细存储抽象。
 * - local 模式：内存环形缓冲（InMemoryTrackStore）
 * - full 模式：ClickHouse（ClickHouseTrackStore）
 */
public interface TrackStore {

    /** 批量写入明细。教学点：列式存储单条 insert 是大忌，必须攒批 */
    void batchInsert(List<TrackEvent> events);

    /** 商品点击排行 */
    List<TopItem> topClickItems(int limit);
}
