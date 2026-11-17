package com.demo.track.model;

/**
 * 上报响应。
 * 教学点：带上 accepted / rejected 计数，前端能感知服务端的校验与去重结果。
 */
public record TrackResponse(int accepted, int rejected, long serverTime) {
}
