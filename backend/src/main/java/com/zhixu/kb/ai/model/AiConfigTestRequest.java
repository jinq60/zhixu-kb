package com.zhixu.kb.ai.model;

import lombok.Data;

/**
 * AI 连接测试请求。
 */
@Data
public class AiConfigTestRequest {
    private String baseUrl;
    private String apiKey;
    private String model;
}
