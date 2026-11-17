package com.demo.track.consume;

import com.demo.track.model.TrackEvent;
import com.demo.track.stats.RealtimeStats;
import com.demo.track.store.TrackStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Kafka 批量消费者（full 模式）。
 *
 * 教学点：
 *  1. 批量消费：max.poll.records=500 + fetch.min.bytes 攒批拉取，吞吐上一个台阶
 *  2. 手动提交 offset：处理成功才 ack；失败不提交，触发重试，避免丢数据
 *  3. 消费前清洗：过滤爬虫 UA、解析失败的消息进日志而不是拖垮整批
 */
@Profile("full")
@Component
public class TrackConsumer {

    private static final Logger log = LoggerFactory.getLogger(TrackConsumer.class);

    private final ObjectMapper objectMapper;
    private final TrackStore store;
    private final RealtimeStats stats;

    public TrackConsumer(ObjectMapper objectMapper, TrackStore store, RealtimeStats stats) {
        this.objectMapper = objectMapper;
        this.store = store;
        this.stats = stats;
    }

    @KafkaListener(topics = "${track.kafka.topic:track_events}",
            groupId = "track-consumer",
            containerFactory = "batchFactory",
            properties = {
                    "max.poll.records=500",
                    "fetch.min.bytes=1048576",
                    "fetch.max.wait.ms=500"
            })
    public void onBatch(List<String> messages, Acknowledgment ack) {
        try {
            List<TrackEvent> events = new ArrayList<>(messages.size());
            for (String message : messages) {
                try {
                    events.add(objectMapper.readValue(message, TrackEvent.class));
                } catch (Exception ex) {
                    log.warn("parse message failed: {}", ex.getMessage());
                }
            }
            // 清洗：过滤爬虫
            events.removeIf(e -> isBot(e.getUa()));

            if (!events.isEmpty()) {
                store.batchInsert(events);   // 明细批量写 ClickHouse
                stats.record(events);        // 实时 PV/UV 写 Redis
            }
            ack.acknowledge();
            log.debug("consumed {} events", events.size());
        } catch (Exception e) {
            // 不提交 offset，下次重新拉取重试；生产环境重试超限应进死信队列
            log.error("batch consume failed, size={}", messages.size(), e);
        }
    }

    private boolean isBot(String ua) {
        if (ua == null) {
            return false;
        }
        String u = ua.toLowerCase();
        return u.contains("bot") || u.contains("spider") || u.contains("crawl");
    }
}
