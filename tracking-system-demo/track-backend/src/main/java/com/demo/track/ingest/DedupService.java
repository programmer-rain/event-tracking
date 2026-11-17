package com.demo.track.ingest;

/**
 * 幂等去重服务。
 * 教学点：eventId 是全链路去重的题眼 ——
 * 前端重试、Kafka 重投、离线补发都会带来重复，全靠它挡住。
 */
public interface DedupService {

    /**
     * @param eventId 事件唯一 ID
     * @return true 首次出现（应保留）；false 重复（应丢弃）
     */
    boolean firstSeen(String eventId);
}
