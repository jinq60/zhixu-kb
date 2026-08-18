package com.zhixu.kb.note.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Data
@ConfigurationProperties(prefix = "file.upload")
public class FileStorageProperties {
    private String path;
    private List<String> allowedTypes;
    private Long maxSize;
}
