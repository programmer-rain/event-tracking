package com.demo.track.model;

import lombok.Data;

import java.util.Map;

/**
 * 埋点事件模型：前后端共用同一份协议。
 * 教学点：字段设计第一天就要钉死，不然后面全是脏数据。
 */
@Data
public class TrackEvent {

    /** 事件唯一 ID，全链路幂等去重的题眼 */
    private String eventId;

    /** 应用标识，如 mall-web */
    private String appId;

    /** 事件类型：page_view / click / exposure / custom */
    private String eventType;

    /** 事件名，如 product_buy_click */
    private String eventName;

    /** 页面标识，如 product_detail */
    private String pageId;

    /** 商品 ID */
    private String itemId;

    /** 登录用户 ID */
    private String userId;

    /** 设备 ID，未登录时 UV 统计的锚点 */
    private String deviceId;

    /** 会话 ID，漏斗串联靠它 */
    private String sessionId;

    /** 客户端时间戳（仅参考，不可信） */
    private Long clientTs;

    /** 服务端时间戳，后端补齐，统计以此为准 */
    private Long serverTs;

    /** 客户端 IP，服务端补齐 */
    private String ip;

    /** User-Agent，服务端补齐，用于爬虫过滤 */
    private String ua;

    /** 业务扩展字段：业务加字段不用改表结构 */
    private Map<String, Object> props;

    /**
     * UV 去重身份：登录优先 userId，未登录用 deviceId。
     * 教学点：加 u:/d: 前缀防止两类 ID 撞车（用户 10001 和设备 10001）。
     */
    public String distinctId() {
        return (userId != null && !userId.isBlank()) ? "u:" + userId : "d:" + deviceId;
    }
}
