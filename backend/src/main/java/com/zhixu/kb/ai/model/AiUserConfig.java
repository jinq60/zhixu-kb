package com.zhixu.kb.ai.model;

import lombok.Data;

/**
 * 用户 AI 配置视图（API Key 脱敏返回）。
 */
@Data
public class AiUserConfig {
    private String provider;
    private String baseUrl;
    private String apiKeyMasked;
    private String model;
    private Boolean enabled;
    private Boolean configured;
}
