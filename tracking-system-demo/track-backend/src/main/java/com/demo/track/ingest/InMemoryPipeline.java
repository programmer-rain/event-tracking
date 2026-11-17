package com.demo.track.ingest;

import com.demo.track.model.TrackEvent;
import com.demo.track.stats.RealtimeStats;
import com.demo.track.store.TrackStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 内存管道（local 模式）：有界队列 + 背压 + 后台攒批写入。
 *
 * 教学点 1：队列必须有界。无界队列会先撑爆 JVM，再拖死数据库。
 * 教学点 2：宁可丢新，不拖垮系统 —— 队列满时直接丢弃并计数告警，
 *           丢埋点数据可以接受，拖垮主库不可接受。
 * 教学点 3：批量写入。攒够 batch-size 条或等待 flush-ms 就落一批，
 *           单条写入是列式存储的大忌。
 */
@Profile("local")
@Component
public class InMemoryPipeline implements TrackPipeline, InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPipeline.class);

    private final BlockingQueue<TrackEvent> queue;
    private final TrackStore store;
    private final RealtimeStats stats;
    private final IngestMetrics metrics;
    private final int batchSize;
    private final long flushMs;

    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "track-writer");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean running = true;

    public InMemoryPipeline(TrackStore store,
                            RealtimeStats stats,
                            IngestMetrics metrics,
                            @Value("${track.ingest.queue-capacity:50000}") int queueCapacity,
                            @Value("${track.ingest.batch-size:500}") int batchSize,
                            @Value("${track.ingest.flush-ms:500}") long flushMs) {
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.store = store;
        this.stats = stats;
        this.metrics = metrics;
        this.batchSize = batchSize;
        this.flushMs = flushMs;
    }

    @Override
    public void submit(TrackEvent event) {
        // offer 不阻塞：满了直接丢 + 计数，这就是背压
        if (!queue.offer(event)) {
            metrics.markDropped();
        }
    }

    @Override
    public long pending() {
        return queue.size();
    }

    @Override
    public void afterPropertiesSet() {
        worker.execute(this::drainLoop);
    }

    private void drainLoop() {
        while (running) {
            try {
                List<TrackEvent> batch = new ArrayList<>(batchSize);
                // 先阻塞等第一条（最多等 flushMs），再一次性抽干队列
                TrackEvent first = queue.poll(flushMs, TimeUnit.MILLISECONDS);
                if (first != null) {
                    batch.add(first);
                    queue.drainTo(batch, batchSize - 1);
                }
                if (batch.isEmpty()) {
                    continue;
                }
                store.batchInsert(batch);   // 明细落库
                stats.record(batch);        // 实时指标
                log.debug("local pipeline flushed {} events", batch.size());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("local pipeline flush failed", e);
            }
        }
    }

    @Override
    public void destroy() {
        running = false;
        worker.shutdown();
    }
}
