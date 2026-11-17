package com.demo.track.ingest;

import com.demo.track.model.TrackEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Kafka 管道（full 模式）：接收层只做一件事 —— 异步投递，立即返回。
 *
 * 教学点：Kafka 在埋点链路里的三重角色：
 *  1. 削峰填谷：洪峰先囤在 Kafka，消费端按自己的节奏拉
 *  2. 解耦：下游任何抖动不会反压到接收接口
 *  3. 多消费：实时统计、离线归档、风控可以各消费一份
 */
@Profile("full")
@Component
public class KafkaPipeline implements TrackPipeline {

    private static final Logger log = LoggerFactory.getLogger(KafkaPipeline.class);

    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;

    @Value("${track.kafka.topic:track_events}")
    private String topic;

    public KafkaPipeline(KafkaTemplate<String, String> kafka, ObjectMapper objectMapper) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
    }

    @Override
    public void submit(TrackEvent event) {
        try {
            // 用 eventId 做分区键：同一事件的重复上报落同一分区，方便下游按 key 去重
            kafka.send(topic, event.getEventId(), objectMapper.writeValueAsString(event))
                    .whenComplete((result, ex) -> {
                        if (ex != null) {
                            // 教学点：Kafka 发送失败要降级（本地补偿日志 / DLQ），别静默丢
                            log.warn("kafka send failed: {}", ex.getMessage());
                        }
                    });
        } catch (Exception e) {
            log.error("serialize track event failed", e);
        }
    }

    @Override
    public long pending() {
        // Kafka 缓冲对应用透明，这里返回 0
        return 0;
    }
}
