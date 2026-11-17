package com.demo.track.model;

/** 实时指标概览：页面 PV / UV / 点击数 / 曝光数 */
public record RealtimeOverview(String date, long pagePv, long pageUv, long clicks, long exposures) {
}
