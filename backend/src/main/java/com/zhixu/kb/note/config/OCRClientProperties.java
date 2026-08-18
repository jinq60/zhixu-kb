package com.zhixu.kb.note.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "ocr.service")
public class OCRClientProperties {
    private String url;
    private Integer timeout;
    private String engine;
    private String fallbackEngine;
}
