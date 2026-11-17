package com.demo.track.ingest;

import com.demo.track.model.TrackEvent;
import com.demo.track.model.TrackRequest;
import com.demo.track.model.TrackResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 埋点接收接口。
 *
 * 接收层铁律：立刻返回，绝不查库、绝不调下游。
 * 接口只做轻量操作：限流 -> 解析 -> 校验 -> 幂等去重 -> 补服务端字段 -> 丢管道。
 *
 * 教学点：刻意用 String 接收请求体。前端用 text/plain 上报可以规避
 * 跨域 CORS 预检（sendBeacon 场景下 application/json 会触发 OPTIONS 预检，容易失败）。
 */
@RestController
@RequestMapping("/api/track")
@CrossOrigin(origins = "*")
public class TrackController {

    private static final Logger log = LoggerFactory.getLogger(TrackController.class);

    /** 事件类型白名单：教学点 —— 埋点治理从服务端枚举校验开始 */
    private static final Set<String> ALLOWED_EVENT_TYPES =
            Set.of("page_view", "click", "exposure", "custom");

    private final ObjectMapper objectMapper;
    private final RateLimiter rateLimiter;
    private final DedupService dedupService;
    private final TrackPipeline pipeline;
    private final IngestMetrics metrics;

    @Value("${track.ingest.max-batch-per-request:100}")
    private int maxBatchPerRequest;

    @Value("${track.ingest.qps-limit:200}")
    private int qpsLimit;

    @Value("${track.ingest.clock-skew-minutes:10}")
    private long clockSkewMinutes;

    public TrackController(ObjectMapper objectMapper,
                           RateLimiter rateLimiter,
                           DedupService dedupService,
                           TrackPipeline pipeline,
                           IngestMetrics metrics) {
        this.objectMapper = objectMapper;
        this.rateLimiter = rateLimiter;
        this.dedupService = dedupService;
        this.pipeline = pipeline;
        this.metrics = metrics;
    }

    @PostMapping(value = "/collect",
            consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.TEXT_PLAIN_VALUE})
    public TrackResponse collect(@RequestBody String body, HttpServletRequest request) {
        long now = System.currentTimeMillis();
        String ip = getClientIp(request);

        // ① 限流：单 IP 超限直接 429，保护下游
        if (!rateLimiter.tryAcquire("ip:" + ip, qpsLimit, 1)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "rate limited");
        }

        // ② 解析：请求体是 JSON 文本（Content-Type 为 text/plain 也能收到）
        TrackRequest req;
        try {
            req = objectMapper.readValue(body, TrackRequest.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "bad request body");
        }
        if (req == null || req.getEvents() == null || req.getEvents().isEmpty()) {
            return new TrackResponse(0, 0, now);
        }

        // ③ 防恶意超大包
        if (req.getEvents().size() > maxBatchPerRequest) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "batch too large");
        }

        String ua = request.getHeader("User-Agent");
        int accepted = 0;
        int rejected = 0;

        for (TrackEvent e : req.getEvents()) {
            // ④ 校验：必填字段 + 事件类型白名单 + 时钟偏差
            if (!valid(e, now)) {
                rejected++;
                continue;
            }
            if (!StringUtils.hasText(e.getEventId())) {
                e.setEventId(UUID.randomUUID().toString());
            }
            // ⑤ 幂等去重：重复事件直接吞掉
            if (!dedupService.firstSeen(e.getEventId())) {
                rejected++;
                continue;
            }
            // ⑥ 服务端补字段：不信任前端
            e.setAppId(req.getAppId());
            e.setServerTs(now);
            e.setIp(ip);
            e.setUa(ua);

            pipeline.submit(e);
            accepted++;
        }

        metrics.markAccepted(accepted);
        metrics.markRejected(rejected);
        // ⑦ 立即返回，不等任何落库。RT 目标 < 10ms
        return new TrackResponse(accepted, rejected, now);
    }

    private boolean valid(TrackEvent e, long now) {
        if (e == null) {
            return false;
        }
        if (!StringUtils.hasText(e.getEventName()) || !StringUtils.hasText(e.getDeviceId())) {
            return false;
        }
        if (e.getEventType() == null || !ALLOWED_EVENT_TYPES.contains(e.getEventType())) {
            return false;
        }
        if (e.getClientTs() == null) {
            return false;
        }
        // 前端时钟不可信：偏差过大直接丢，避免污染历史分区
        return Math.abs(now - e.getClientTs()) <= TimeUnit.MINUTES.toMillis(clockSkewMinutes);
    }

    /** 取真实客户端 IP：优先 X-Forwarded-For 第一段 */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (!StringUtils.hasText(ip) || "unknown".equalsIgnoreCase(ip)) {
            return request.getRemoteAddr();
        }
        return ip.split(",")[0].trim();
    }
}
