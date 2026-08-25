package com.zhixu.kb.ai;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.ai.entity.AiEndpointEntity;
import com.zhixu.kb.config.AiProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 平台 AI 端点池：轮询调用 + 失败冷却自动切换。
 * - 端点来源：数据库（管理后台维护）优先；数据库为空时回退到 AI_ENDPOINTS / 单组全局配置
 * - 调用按轮询顺序选择未冷却的端点；失败（429/5xx/超时）冷却 60 秒后切换
 */
@Slf4j
@Component
public class AiApiPool {

    private static final long COOLDOWN_MS = 60_000L;

    private final List<Endpoint> endpoints = new CopyOnWriteArrayList<>();
    private final AtomicInteger cursor = new AtomicInteger(0);

    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;

    public AiApiPool(AiProperties aiProperties, ObjectMapper objectMapper) {
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        loadFromStaticConfig();
    }

    /**
     * 从数据库加载端点（管理后台维护）。数据库为空（全部删除/停用）时
     * 回退到静态配置端点：确保管理后台停用/下线泄露 Key 的操作能真正生效，
     * 且系统始终有可用端点来源。
     */
    public synchronized void refreshFromDb(List<AiEndpointEntity> enabledEntities) {
        if (enabledEntities != null && !enabledEntities.isEmpty()) {
            endpoints.clear();
            cursor.set(0);
            for (AiEndpointEntity entity : enabledEntities) {
                endpoints.add(new Endpoint(entity.getId(), entity.getBaseUrl().trim(),
                        entity.getApiKey(), entity.getModel(),
                        entity.getEmbeddingModel() == null ? null : entity.getEmbeddingModel().trim()));
            }
            log.info("AiApiPool refreshed from DB: {} endpoint(s)", endpoints.size());
        } else {
            endpoints.clear();
            cursor.set(0);
            loadFromStaticConfig();
            log.info("AiApiPool DB endpoint list empty, fallback to static config: {} endpoint(s)", endpoints.size());
        }
    }

    private void loadFromStaticConfig() {
        List<Endpoint> staticEndpoints = new CopyOnWriteArrayList<>();
        String json = aiProperties.getEndpointsJson();
        if (StringUtils.hasText(json)) {
            try {
                List<ConfigEntry> entries = objectMapper.readValue(json, new TypeReference<List<ConfigEntry>>() {
                });
                for (ConfigEntry entry : entries) {
                    if (entry == null || !StringUtils.hasText(entry.getApiKey()) || !StringUtils.hasText(entry.getBaseUrl())) {
                        continue;
                    }
                    staticEndpoints.add(new Endpoint(null, entry.getBaseUrl().trim(), entry.getApiKey().trim(),
                            StringUtils.hasText(entry.getModel()) ? entry.getModel().trim() : "deepseek-chat",
                            null));
                }
            } catch (Exception ex) {
                log.warn("AI_ENDPOINTS parse failed: {}", ex.getMessage());
            }
        }

        if (staticEndpoints.isEmpty()) {
            String baseUrl = aiProperties.getApi().getBaseUrl();
            String apiKey = aiProperties.getApi().getApiKey();
            String model = aiProperties.getApi().getModel();
            if (StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey)) {
                staticEndpoints.add(new Endpoint(null, baseUrl.trim(), apiKey.trim(),
                        StringUtils.hasText(model) ? model.trim() : "deepseek-chat", null));
            }
        }
        endpoints.addAll(staticEndpoints);
        log.info("AiApiPool initialized with {} static endpoint(s)", endpoints.size());
    }

    /**
     * 选择当前可用端点（轮询负载均衡）：
     * 从当前光标位置开始找未冷却端点，找到后移动光标，让不同请求/块分散到多个端点，
     * 提升整体吞吐并降低单个厂家被限流的概率。全部冷却时回退到第一个端点强制重试。
     */
    public Endpoint select() {
        // CopyOnWriteArrayList 每次 get 都重读底层数组：size() 与 get(idx) 若非同一快照，
        // refreshFromDb 清空重建期间可能取到越界索引抛 IOOBE（在适配器重试保护之外）。
        // 固定使用局部快照引用，保证 size 与 get 遍历同一数组。
        List<Endpoint> snapshot = this.endpoints;
        int size = snapshot.size();
        if (size == 0) {
            return null;
        }
        long now = System.currentTimeMillis();
        int start = Math.floorMod(cursor.getAndIncrement(), size);
        for (int i = 0; i < size; i++) {
            int idx = (start + i) % size;
            Endpoint endpoint = snapshot.get(idx);
            if (!endpoint.isCooldown(now)) {
                endpoint.touch(now);
                return endpoint;
            }
        }
        // 全部冷却：回退到第一个端点（强制重试）
        Endpoint fallback = snapshot.get(0);
        fallback.touch(now);
        return fallback;
    }

    public void markFailure(Endpoint endpoint) {
        if (endpoint == null) {
            return;
        }
        endpoint.markCooldown(System.currentTimeMillis());
        log.warn("ai endpoint failed, cooldown 60s: baseUrl={} model={}", endpoint.getBaseUrl(), endpoint.getModel());
    }

    public void markSuccess(Endpoint endpoint) {
        if (endpoint != null) {
            endpoint.clearCooldown();
        }
    }

    /**
     * 查询端点运行状态（供管理后台展示）。
     */
    public EndpointStatus getStatus(Long dbId) {
        EndpointStatus status = new EndpointStatus();
        status.setCooldown(false);
        status.setLastUsedMs(0L);
        if (dbId == null) {
            return status;
        }
        long now = System.currentTimeMillis();
        for (Endpoint endpoint : endpoints) {
            if (dbId.equals(endpoint.getDbId())) {
                status.setCooldown(endpoint.isCooldown(now));
                status.setLastUsedMs(endpoint.getLastUsedMs());
                break;
            }
        }
        return status;
    }

    public int size() {
        return endpoints.size();
    }

    /**
     * 当前端点快照（向量化服务按顺序探测各端点的 embedding 能力时使用）。
     */
    public List<Endpoint> listEndpoints() {
        return new java.util.ArrayList<>(endpoints);
    }

    /**
     * AI 端点。
     */
    public static class Endpoint {
        private final Long dbId;
        private final String baseUrl;
        private final String apiKey;
        private final String model;
        private final String embeddingModel;
        private volatile long cooldownUntilMs = 0L;
        private volatile long lastUsedMs = 0L;

        Endpoint(Long dbId, String baseUrl, String apiKey, String model, String embeddingModel) {
            this.dbId = dbId;
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.embeddingModel = embeddingModel;
        }

        boolean isCooldown(long now) {
            return now < cooldownUntilMs;
        }

        void markCooldown(long now) {
            this.cooldownUntilMs = now + COOLDOWN_MS;
        }

        void clearCooldown() {
            this.cooldownUntilMs = 0L;
        }

        void touch(long now) {
            this.lastUsedMs = now;
        }

        public Long getDbId() {
            return dbId;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getApiKey() {
            return apiKey;
        }

        public String getModel() {
            return model;
        }

        public String getEmbeddingModel() {
            return embeddingModel;
        }

        public long getLastUsedMs() {
            return lastUsedMs;
        }
    }

    public static class EndpointStatus {
        private boolean cooldown;
        private long lastUsedMs;

        public boolean isCooldown() {
            return cooldown;
        }

        public void setCooldown(boolean cooldown) {
            this.cooldown = cooldown;
        }

        public long getLastUsedMs() {
            return lastUsedMs;
        }

        public void setLastUsedMs(long lastUsedMs) {
            this.lastUsedMs = lastUsedMs;
        }
    }

    public static class ConfigEntry {
        private String apiKey;
        private String baseUrl;
        private String model;

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }
    }
}
