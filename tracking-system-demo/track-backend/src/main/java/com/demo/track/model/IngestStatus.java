package com.demo.track.model;

/**
 * 接入层运行状态。
 * 教学点：可观测性 —— accepted/rejected/dropped/pending 让"丢没丢数据"看得见。
 */
public record IngestStatus(String mode, long accepted, long rejected, long dropped, long pending) {
}
