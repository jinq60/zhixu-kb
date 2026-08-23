package com.zhixu.kb.ai.model;

import lombok.Data;

/**
 * AI 端点视图（Key 脱敏 + 运行状态）。
 */
@Data
public class AiEndpointView {
    private Long id;
    private String baseUrl;
    private String apiKeyMasked;
    private String model;
    private String embeddingModel;
    private Boolean enabled;
    private String remark;
    private Boolean cooldown;
    private String status;
    private String statusText;
    private String lastUsed;
}
