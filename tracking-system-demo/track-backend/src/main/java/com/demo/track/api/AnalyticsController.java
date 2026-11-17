package com.demo.track.api;

import com.demo.track.ingest.IngestMetrics;
import com.demo.track.ingest.TrackPipeline;
import com.demo.track.model.IngestStatus;
import com.demo.track.model.RealtimeOverview;
import com.demo.track.model.TopItem;
import com.demo.track.stats.RealtimeStats;
import com.demo.track.store.TrackStore;
import com.demo.track.util.Dates;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分析查询接口：供前端实时看板轮询。
 * 教学点：查询走预聚合指标（Redis / 物化视图），永远不扫明细表。
 */
@RestController
@RequestMapping("/api/analytics")
@CrossOrigin(origins = "*")
public class AnalyticsController {

    private final RealtimeStats stats;
    private final TrackStore store;
    private final IngestMetrics metrics;
    private final TrackPipeline pipeline;
    private final Environment env;

    public AnalyticsController(RealtimeStats stats,
                               TrackStore store,
                               IngestMetrics metrics,
                               TrackPipeline pipeline,
                               Environment env) {
        this.stats = stats;
        this.store = store;
        this.metrics = metrics;
        this.pipeline = pipeline;
        this.env = env;
    }

    /** 实时指标概览：页面 PV / UV / 点击 / 曝光 */
    @GetMapping("/overview")
    public RealtimeOverview overview(@RequestParam(required = false) String date) {
        String day = (date == null || date.isBlank()) ? Dates.today() : date;
        return stats.overview(day);
    }

    /** 商品点击排行 */
    @GetMapping("/top-items")
    public List<TopItem> topItems(@RequestParam(defaultValue = "10") int limit) {
        return store.topClickItems(limit);
    }

    /** 接入状态：观察限流、去重、背压丢弃的实时窗口 */
    @GetMapping("/ingest-status")
    public IngestStatus ingestStatus() {
        String mode = env.acceptsProfiles(Profiles.of("full"))
                ? "full（Kafka + Redis + ClickHouse）"
                : "local（内存链路，零依赖）";
        return new IngestStatus(mode,
                metrics.accepted(), metrics.rejected(), metrics.dropped(), pipeline.pending());
    }
}
