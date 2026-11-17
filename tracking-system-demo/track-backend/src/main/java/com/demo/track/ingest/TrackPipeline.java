package com.demo.track.ingest;

import com.demo.track.model.TrackEvent;

/**
 * 埋点数据管道抽象：接收层校验完毕后把事件交给管道。
 * - local 模式：内存有界队列 + 后台攒批写入（InMemoryPipeline）
 * - full 模式：投递 Kafka，由消费者攒批处理（KafkaPipeline）
 *
 * 教学点：接收层铁律 —— 立刻返回，绝不查库、绝不调下游。
 */
public interface TrackPipeline {

    /** 提交一条已通过校验与去重的事件 */
    void submit(TrackEvent event);

    /** 待处理事件数（可观测性用；Kafka 模式下返回 0） */
    long pending();
}
