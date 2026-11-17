package com.demo.track.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** 时间工具：统一时区口径，避免跨时区统计错乱 */
public final class Dates {

    /** 统计口径统一用东八区 */
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private Dates() {
    }

    /** 毫秒时间戳 -> yyyyMMdd */
    public static String dayOf(long epochMillis) {
        return DAY.format(Instant.ofEpochMilli(epochMillis).atZone(ZONE));
    }

    /** 今天（东八区） */
    public static String today() {
        return DAY.format(LocalDate.now(ZONE));
    }
}
