package com.zhixu.kb.common;

import lombok.Data;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

/**
 * 系统告警事件。
 */
@Data
public class AlertEvent {
    private String id;
    private String type;
    private String severity;
    private String message;
    private Instant createdAt;
    private Map<String, Object> metadata = Collections.emptyMap();
}
