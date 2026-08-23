package com.zhixu.kb.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhixu.kb.config.AiProperties;
import com.zhixu.kb.config.MilvusProperties;
import com.zhixu.kb.ai.service.UserAiConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 向量化服务：调用 OpenAI 兼容 /embeddings 端点。
 * 端点选择：用户级向量化配置 → 平台端点池（配置了 embedding 模型的端点优先；
 * 未配置的端点自动探测 embedding 模型——ai.embedding.model 配置的模型作为首选候选，
 * 其后依次尝试常见模型；探测结果按端点缓存）。
 * 全部不可用时抛异常，由调用方降级为纯关键词检索。
 */
@Slf4j
@Service
public class EmbeddingService {

    /** 常见 embedding 模型探测顺序（维度须与 milvus.dimension 匹配才会被选中） */
    private static final List<String> CANDIDATE_EMBEDDING_MODELS = Arrays.asList(
            "BAAI/bge-m3",            // 硅基流动 / OpenRouter 等（1024 维）
            "text-embedding-v3",      // 阿里云百炼（1024 维）
            "text-embedding-3-small"  // OpenAI（1536 维）
    );

    private final AiProperties aiProperties;
    private final AiApiPool aiApiPool;
    private final ObjectMapper objectMapper;
    private final UserAiConfigService userAiConfigService;
    private final MilvusProperties milvusProperties;

    /** 查询向量缓存（同问题重复检索不重复调用 embedding API，10 分钟 TTL） */
    private final Cache<String, float[]> queryVectorCache;
    /** 端点 -> 探测到的 embedding 模型（正缓存 10 分钟） */
    private final Cache<String, String> embeddingModelCache;
    /** 端点 -> 探测失败标记（负缓存 2 分钟，避免逐块重复探测） */
    private final Cache<String, Boolean> embeddingProbeFailedCache;

    public EmbeddingService(AiProperties aiProperties, AiApiPool aiApiPool, ObjectMapper objectMapper,
                            UserAiConfigService userAiConfigService, MilvusProperties milvusProperties) {
        this.aiProperties = aiProperties;
        this.aiApiPool = aiApiPool;
        this.objectMapper = objectMapper;
        this.userAiConfigService = userAiConfigService;
        this.milvusProperties = milvusProperties;
        this.queryVectorCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(2000)
                .build();
        this.embeddingModelCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(10))
                .maximumSize(200)
                .build();
        this.embeddingProbeFailedCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(2))
                .maximumSize(200)
                .build();
    }

    public boolean isEnabled() {
        return aiProperties.getEmbedding().isEnabled();
    }

    /**
     * 将文本截断到 embedding 输入上限（当前单块策略，超长文本取前 N 字符）。
     */
    public String truncateToMaxChars(String text) {
        int max = Math.max(200, aiProperties.getEmbedding().getMaxChunkChars());
        if (text != null && text.length() > max) {
            return text.substring(0, max);
        }
        return text;
    }

    /**
     * 将文本向量化（float32 数组），带 10 分钟查询缓存。
     *
     * @return float[] 向量；失败抛异常
     */
    public float[] embed(String text) {
        String key = truncateToMaxChars(text == null ? "" : text);
        float[] cached = queryVectorCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        float[] vector = embedBatch(Collections.singletonList(key)).get(0);
        if (vector != null && vector.length > 0) {
            queryVectorCache.put(key, vector);
        }
        return vector;
    }

    /**
     * 批量向量化。
     * 端点选择：用户配置的向量化端点优先（失败回落平台端点池）→ 平台端点池
     * （配置了 embedding 模型的端点优先；否则自动探测常见 embedding 模型，按端点缓存）。
     */
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.Optional<UserAiConfigService.ResolvedApiConfig> userEmb = userAiConfigService.resolveEmbeddingConfig();
        if (userEmb.isPresent()) {
            UserAiConfigService.ResolvedApiConfig cfg = userEmb.get();
            try {
                return embedWithDimensionCheck(cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModel(), texts, null);
            } catch (Exception ex) {
                log.warn("user embedding config failed, fallback to platform pool: baseUrl={} err={}",
                        cfg.getBaseUrl(), ex.getMessage());
            }
        }
        List<AiApiPool.Endpoint> endpoints = aiApiPool.listEndpoints();
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalStateException("no ai endpoint available for embedding");
        }
        // 1) 管理后台显式配置了 embedding 模型的端点优先
        for (AiApiPool.Endpoint endpoint : endpoints) {
            if (!StringUtils.hasText(endpoint.getApiKey()) || !StringUtils.hasText(endpoint.getEmbeddingModel())) {
                continue;
            }
            try {
                return embedWithDimensionCheck(endpoint.getBaseUrl(), endpoint.getApiKey(),
                        endpoint.getEmbeddingModel(), texts, endpoint);
            } catch (Exception ex) {
                log.warn("embedding endpoint failed (configured model): baseUrl={} model={} err={}",
                        endpoint.getBaseUrl(), endpoint.getEmbeddingModel(), ex.getMessage());
            }
        }
        // 2) 未配置 embedding 模型的端点：自动探测（探测结果按端点缓存，维度须与 Milvus 一致）
        for (AiApiPool.Endpoint endpoint : endpoints) {
            if (!StringUtils.hasText(endpoint.getApiKey())) {
                continue;
            }
            String detected = detectEmbeddingModel(endpoint);
            if (detected == null) {
                continue;
            }
            try {
                return embedWithDimensionCheck(endpoint.getBaseUrl(), endpoint.getApiKey(), detected, texts, endpoint);
            } catch (Exception ex) {
                log.warn("embedding endpoint failed (detected model): baseUrl={} model={} err={}",
                        endpoint.getBaseUrl(), detected, ex.getMessage());
            }
        }
        throw new IllegalStateException("no embedding-capable endpoint available");
    }

    /**
     * 探测端点可用的 embedding 模型（候选列表按顺序试，维度须与 Milvus 配置一致）。
     * 结果按端点缓存：正缓存 10 分钟，负缓存 2 分钟（避免逐块重复探测）。
     */
    private String detectEmbeddingModel(AiApiPool.Endpoint endpoint) {
        String cacheKey = endpoint.getBaseUrl() + "|" + (endpoint.getDbId() == null ? "static" : endpoint.getDbId());
        String cached = embeddingModelCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }
        if (Boolean.TRUE.equals(embeddingProbeFailedCache.getIfPresent(cacheKey))) {
            return null;
        }
        for (String candidate : candidateModels()) {
            try {
                List<float[]> probe = embedWith(endpoint.getBaseUrl(), endpoint.getApiKey(), candidate,
                        Collections.singletonList("test"), null);
                if (probe == null || probe.isEmpty() || probe.get(0) == null || probe.get(0).length == 0) {
                    continue;
                }
                if (probe.get(0).length != milvusProperties.getDimension()) {
                    // 维度不匹配的模型跳过（如 OpenAI 1536 维 vs bge-m3 1024 维）
                    continue;
                }
                embeddingModelCache.put(cacheKey, candidate);
                log.info("auto-detected embedding model: baseUrl={} model={} dim={}",
                        endpoint.getBaseUrl(), candidate, probe.get(0).length);
                return candidate;
            } catch (Exception ignored) {
                // 该候选模型不可用，尝试下一个
            }
        }
        embeddingProbeFailedCache.put(cacheKey, Boolean.TRUE);
        log.warn("no usable embedding model detected on endpoint: baseUrl={}", endpoint.getBaseUrl());
        return null;
    }

    /**
     * 探测候选模型列表：ai.embedding.model 显式配置的模型优先（使配置真实生效），
     * 其后按常见模型顺序兜底；维度校验会自动跳过与 Milvus 配置不符的候选。
     */
    private List<String> candidateModels() {
        String configured = aiProperties.getEmbedding().getModel();
        if (!StringUtils.hasText(configured)) {
            return CANDIDATE_EMBEDDING_MODELS;
        }
        List<String> candidates = new ArrayList<>(CANDIDATE_EMBEDDING_MODELS.size() + 1);
        candidates.add(configured.trim());
        for (String candidate : CANDIDATE_EMBEDDING_MODELS) {
            if (!candidate.equals(configured.trim())) {
                candidates.add(candidate);
            }
        }
        return candidates;
    }

    /**
     * 向量化并校验维度与 Milvus 配置一致（不一致给出可操作的错误提示）。
     */
    private List<float[]> embedWithDimensionCheck(String baseUrl, String apiKey, String model,
                                                  List<String> texts, AiApiPool.Endpoint poolEndpoint) {
        List<float[]> results = embedWith(baseUrl, apiKey, model, texts, poolEndpoint);
        if (results == null || results.isEmpty() || results.get(0) == null) {
            throw new IllegalStateException("embedding empty result");
        }
        int dim = results.get(0).length;
        if (dim != milvusProperties.getDimension()) {
            throw new IllegalStateException("embedding 维度 " + dim + " 与 Milvus 配置维度 "
                    + milvusProperties.getDimension() + " 不一致，请在 .env 设置 MILVUS_DIMENSION=" + dim + " 后重启");
        }
        return results;
    }

    private List<float[]> embedWith(String baseUrl, String apiKey, String model, List<String> texts,
                                    AiApiPool.Endpoint poolEndpoint) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("input", texts);

        try {
            ResponseEntity<Map> response = EMBEDDING_REST_TEMPLATE.exchange(
                    baseUrl + "/embeddings",
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Map.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                if (poolEndpoint != null) {
                    aiApiPool.markFailure(poolEndpoint);
                }
                throw new IllegalStateException("embedding http " + response.getStatusCodeValue());
            }
            JsonNode root = objectMapper.valueToTree(response.getBody());
            JsonNode data = root.path("data");
            if (!data.isArray() || data.size() != texts.size()) {
                if (poolEndpoint != null) {
                    aiApiPool.markFailure(poolEndpoint);
                }
                throw new IllegalStateException("embedding response size mismatch");
            }
            if (poolEndpoint != null) {
                aiApiPool.markSuccess(poolEndpoint);
            }
            List<float[]> results = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode vec = item.path("embedding");
                if (!vec.isArray()) {
                    throw new IllegalStateException("embedding field missing");
                }
                float[] floats = new float[vec.size()];
                for (int i = 0; i < vec.size(); i++) {
                    floats[i] = (float) vec.get(i).asDouble();
                }
                results.add(floats);
            }
            return results;
        } catch (Exception ex) {
            if (poolEndpoint != null) {
                aiApiPool.markFailure(poolEndpoint);
            }
            throw new IllegalStateException("embedding failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * float[] → BLOB 字节（float32 LE）。
     */
    public static byte[] toBytes(float[] vector) {
        if (vector == null) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : vector) {
            buffer.putFloat(v);
        }
        return buffer.array();
    }

    /**
     * BLOB 字节 → float[]。
     */
    public static float[] fromBytes(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length % 4 != 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / 4];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    /**
     * 余弦相似度（向量检索打分）。非法向量返回 0。
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0D;
        }
        double dot = 0D, normA = 0D, normB = 0D;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0D || normB == 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static final RestTemplate EMBEDDING_REST_TEMPLATE = createEmbeddingRestTemplate();

    private static RestTemplate createEmbeddingRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        return new RestTemplate(factory);
    }
}