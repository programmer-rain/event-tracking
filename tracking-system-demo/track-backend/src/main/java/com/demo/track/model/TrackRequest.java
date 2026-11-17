package com.demo.track.model;

import lombok.Data;

import java.util.List;

/** 批量上报请求体：{ appId, events: [...] } */
@Data
public class TrackRequest {

    private String appId;

    private List<TrackEvent> events;
}
