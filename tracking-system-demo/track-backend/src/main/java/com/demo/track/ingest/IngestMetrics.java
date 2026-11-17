package com.demo.track.ingest;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.LongAdder;

/**
 * 接入层可观测性指标。
 * 教学点："数据少了不可怕，可怕的是没人知道" ——
 * accepted / rejected / dropped 全部计数暴露给看板。
 */
@Component
public class IngestMetrics {

    private final LongAdder accepted = new LongAdder();
    private final LongAdder rejected = new LongAdder();
    private final LongAdder dropped = new LongAdder();

    public void markAccepted(int n) {
        accepted.add(n);
    }

    public void markRejected(int n) {
        rejected.add(n);
    }

    /** 背压丢弃计数：有界队列满时触发 */
    public void markDropped() {
        dropped.increment();
    }

    public long accepted() {
        return accepted.sum();
    }

    public long rejected() {
        return rejected.sum();
    }

    public long dropped() {
        return dropped.sum();
    }
}
