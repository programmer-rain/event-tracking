package com.demo.track;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 埋点系统教学 Demo 启动类。
 *
 * 两种运行模式：
 *  - local（默认）：零外部依赖，全内存链路，适合课堂演示
 *  - full：Kafka + Redis + ClickHouse，完整生产架构
 */
@SpringBootApplication
@EnableScheduling
public class TrackApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrackApplication.class, args);
        System.out.println("" +
                "╔════════════════════════════════════════════════════════════╗\n" +
                "║                                                            ║\n" +
                "║   (♥◠‿◠)ﾉﾞ  Rain Boot 启动成功!  ლ(´ڡ`ლ)ﾞ                  ║\n" +
                "║                                                            ║\n" +
                "║   ┌────────────────────────────────────────────────────┐   ║\n" +
                "║   │  📱 微信公众号 : Rain的Java大神之路                    │  ║\n" +
                "║   │  🌐 技术博客   : javadashen.com                     │   ║\n" +
                "║   │  📖 专注Java技术分享与实战经验                         │  ║\n" +
                "║   └────────────────────────────────────────────────────┘  ║\n" +
                "║                                                           ║\n" +
                "║   ☕ Code is poetry, Java is art.                         ║\n" +
                "║   ✨ May the source be with you!                          ║\n" +
                "║                                                            ║\n" +
                "╚════════════════════════════════════════════════════════════╝");
    }
}
