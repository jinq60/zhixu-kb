package com.zhixu.kb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 引擎配置：云端 OpenAI 兼容 API（多厂商）。
 */
@ConfigurationProperties(prefix = "ai")
public class AiProperties {

    private String engineType = "api";
    private String endpointsJson;
    private Api api = new Api();

    public String getEngineType() {
        return engineType;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }

    /**
     * 多端点配置（JSON 数组，如 AI_ENDPOINTS）：
     * [{"apiKey":"sk-xxx","baseUrl":"https://...","model":"m1"},
     *  {"apiKey":"sk-yyy","baseUrl":"https://...","model":"m2"}]
     * 未配置时回退到单组 api.apiKey / api.baseUrl / api.model。
     */
    public String getEndpointsJson() {
        return endpointsJson;
    }

    public void setEndpointsJson(String endpointsJson) {
        this.endpointsJson = endpointsJson;
    }

    public Api getApi() {
        return api;
    }

    public void setApi(Api api) {
        this.api = api;
    }

    public static class Api {
        private String baseUrl;
        private String apiKey;
        private String model;
        private Integer timeoutMs = 30000;
        private Integer maxRetries = 3;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Integer getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(Integer timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public Integer getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(Integer maxRetries) {
            this.maxRetries = maxRetries;
        }
    }
}
