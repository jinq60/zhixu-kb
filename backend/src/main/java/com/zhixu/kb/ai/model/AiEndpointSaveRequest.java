package com.zhixu.kb.ai.model;

import lombok.Data;

/**
 * AI 端点保存请求。
 */
@Data
public class AiEndpointSaveRequest {
    private Long id;
    private String baseUrl;
    private String apiKey;
    private String model;
    /** 向量化模型（可选） */
    private String embeddingModel;
    private Boolean enabled;
    private String remark;
}
