package com.zhixu.kb.ai.model;

import lombok.Data;

/**
 * 用户 AI 配置保存请求。
 */
@Data
public class AiUserConfigSaveRequest {
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Boolean enabled;
}
