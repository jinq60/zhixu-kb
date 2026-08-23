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
    private Embedding embedding = new Embedding();

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

    public Embedding getEmbedding() {
        return embedding;
    }

    public void setEmbedding(Embedding embedding) {
        this.embedding = embedding;
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

    /**
     * 向量化（RAG embedding）配置：复用端点池的 baseUrl + apiKey 调 /embeddings。
     */
    public static class Embedding {
        private boolean enabled = true;
        private String model = "openai/text-embedding-3-small";
        private int maxChunkChars = 2000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public int getMaxChunkChars() {
            return maxChunkChars;
        }

        public void setMaxChunkChars(int maxChunkChars) {
            this.maxChunkChars = maxChunkChars;
        }
    }
}
