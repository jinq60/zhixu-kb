package com.zhixu.kb.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixu.kb.ai.entity.AiUserConfigEntity;
import com.zhixu.kb.ai.mapper.AiUserConfigMapper;
import com.zhixu.kb.ai.model.AiUserConfig;
import com.zhixu.kb.ai.model.AiUserConfigSaveRequest;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.config.NonRedirectingSimpleClientHttpRequestFactory;
import com.zhixu.kb.common.utils.SafeUrlValidator;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.security.CryptoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 用户级 AI 配置：多厂商 API Key 加密存储、脱敏读取、连接测试。
 * 当前线程的用户配置由引擎适配器读取（优先用户配置 → 平台默认配置）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserAiConfigService {

    private static final String[] PROVIDERS = {"deepseek", "openai", "moonshot", "zhipu", "qwen", "siliconflow", "custom"};

    private final AiUserConfigMapper configMapper;
    private final CryptoService cryptoService;
    private final RestTemplate restTemplate;

    /**
     * 读取当前线程用户的 AI 配置（未配置返回 null）。
     */
    public AiUserConfigEntity getCurrentUserConfig() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            return null;
        }
        return getByUserId(userId);
    }

    public AiUserConfigEntity getByUserId(Long userId) {
        if (userId == null) {
            return null;
        }
        AiUserConfigEntity entity = configMapper.selectOne(new LambdaQueryWrapper<AiUserConfigEntity>()
                .eq(AiUserConfigEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (entity == null) {
            return null;
        }
        return entity;
    }

    /**
     * 当前线程用户的可用 API 配置（仅当配置了可用的云端 API 时返回）。
     */
    public Optional<ResolvedApiConfig> resolveApiConfig() {
        AiUserConfigEntity entity = getCurrentUserConfig();
        if (entity != null
                && Boolean.TRUE.equals(entity.getEnabled() == null || entity.getEnabled() == 1)
                && StringUtils.hasText(entity.getApiKey())
                && StringUtils.hasText(entity.getBaseUrl())) {
            return Optional.of(new ResolvedApiConfig(
                    entity.getBaseUrl(),
                    cryptoService.decrypt(entity.getApiKey()),
                    StringUtils.hasText(entity.getModel()) ? entity.getModel() : "deepseek-chat"));
        }
        return Optional.empty();
    }

    /**
     * 当前线程用户的向量化配置（用户配置了独立 embedding 端点时返回，否则回落平台端点池）。
     */
    public Optional<ResolvedApiConfig> resolveEmbeddingConfig() {
        AiUserConfigEntity entity = getCurrentUserConfig();
        if (entity != null
                && Boolean.TRUE.equals(entity.getEnabled() == null || entity.getEnabled() == 1)
                && StringUtils.hasText(entity.getEmbeddingApiKey())
                && StringUtils.hasText(entity.getEmbeddingBaseUrl())) {
            return Optional.of(new ResolvedApiConfig(
                    entity.getEmbeddingBaseUrl(),
                    cryptoService.decrypt(entity.getEmbeddingApiKey()),
                    StringUtils.hasText(entity.getEmbeddingModel()) ? entity.getEmbeddingModel() : "text-embedding-3-small"));
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public AiUserConfig getView(Long userId) {
        AiUserConfigEntity entity = getByUserId(userId);
        AiUserConfig view = new AiUserConfig();
        if (entity == null) {
            view.setConfigured(false);
            view.setEnabled(true);
            return view;
        }
        view.setConfigured(true);
        view.setProvider(entity.getProvider());
        view.setBaseUrl(entity.getBaseUrl());
        view.setApiKeyMasked(maskKey(cryptoService.decrypt(entity.getApiKey())));
        view.setModel(entity.getModel());
        view.setEmbeddingBaseUrl(entity.getEmbeddingBaseUrl());
        view.setEmbeddingApiKeyMasked(entity.getEmbeddingApiKey() == null
                ? null : maskKey(cryptoService.decrypt(entity.getEmbeddingApiKey())));
        view.setEmbeddingModel(entity.getEmbeddingModel());
        view.setEnabled(entity.getEnabled() == null || entity.getEnabled() == 1);
        return view;
    }

    @Transactional
    public AiUserConfig save(Long userId, AiUserConfigSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "配置不能为空");
        }
        String provider = normalizeProvider(request.getProvider());

        String baseUrl = trimToNull(request.getBaseUrl());
        String model = trimToNull(request.getModel());
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接口地址不能为空");
        }
        SafeUrlValidator.validateOrThrow(baseUrl);
        if (!StringUtils.hasText(model)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "模型名称不能为空");
        }

        AiUserConfigEntity entity = getByUserId(userId);
        if (entity == null) {
            entity = new AiUserConfigEntity();
            entity.setUserId(userId);
            entity.setCreateTime(LocalDateTime.now());
        }
        entity.setProvider(provider);
        entity.setBaseUrl(baseUrl);
        entity.setModel(model);
        // 传入新 key 时更新；未传则保留原 key
        String newKey = trimToNull(request.getApiKey());
        if (StringUtils.hasText(newKey)) {
            entity.setApiKey(cryptoService.encrypt(newKey));
        } else if (entity.getApiKey() == null || !StringUtils.hasText(cryptoService.decrypt(entity.getApiKey()))) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "API Key 不能为空（未配置过 Key 时必须填写）");
        }
        // 向量化端点（可选）：手动配置时校验保存；
        // 未填写时：新配置自动探测是否支持向量化；已有配置保留原值（不得清空用户已配的 embedding 端点）
        String embBaseUrl = trimToNull(request.getEmbeddingBaseUrl());
        String embKey = trimToNull(request.getEmbeddingApiKey());
        String embModel = trimToNull(request.getEmbeddingModel());
        if (StringUtils.hasText(embBaseUrl) || StringUtils.hasText(embKey)) {
            if (!StringUtils.hasText(embBaseUrl) || !StringUtils.hasText(embKey)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "向量化接口地址与 API Key 需同时填写");
            }
            SafeUrlValidator.validateOrThrow(embBaseUrl);
            entity.setEmbeddingBaseUrl(embBaseUrl);
            entity.setEmbeddingApiKey(cryptoService.encrypt(embKey));
            entity.setEmbeddingModel(StringUtils.hasText(embModel) ? embModel : "text-embedding-3-small");
        } else if (entity.getId() == null) {
            autoDetectEmbedding(entity);
        }
        // enabled：更新时未传则保留原值，避免"编辑模型名"顺带把已停用的配置重新打开
        if (request.getEnabled() != null) {
            entity.setEnabled(request.getEnabled() ? 1 : 0);
        } else if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        entity.setUpdateTime(LocalDateTime.now());

        if (entity.getId() == null) {
            configMapper.insert(entity);
        } else {
            configMapper.updateById(entity);
        }
        return getView(userId);
    }

    @Transactional
    public void delete(Long userId) {
        configMapper.delete(new LambdaQueryWrapper<AiUserConfigEntity>()
                .eq(AiUserConfigEntity::getUserId, userId));
    }

    /**
     * 连接测试：调用 OpenAI 兼容 /models 接口验证 Key 与地址。
     */
    public Map<String, Object> testConnection(String baseUrl, String apiKey, String model) {
        String url = trimToNull(baseUrl);
        String key = trimToNull(apiKey);
        if (!StringUtils.hasText(url)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接口地址不能为空");
        }
        SafeUrlValidator.validateBeforeRequest(url);
        if (!StringUtils.hasText(key)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "API Key 不能为空");
        }
        String normalized = normalizeBaseUrl(url);

        Map<String, Object> result = new HashMap<>();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(key);
            headers.setContentType(MediaType.APPLICATION_JSON);
            // 连接测试使用独立短超时，避免被全局 300s 读超时阻塞
            RestTemplate testRestTemplate = createTestRestTemplate(5000, 15000);
            ResponseEntity<Map> response = testRestTemplate.exchange(
                    normalized + "/models",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);
            boolean ok = response.getStatusCode().is2xxSuccessful() && response.getBody() != null;
            result.put("success", ok);
            result.put("message", ok ? "连接成功" : "连接失败：HTTP " + response.getStatusCodeValue());
            if (StringUtils.hasText(model)) {
                result.put("model", model);
            }
        } catch (Exception ex) {
            result.put("success", false);
            result.put("message", "连接失败：" + ex.getMessage());
        }
        return result;
    }

    /**
     * 自动探测当前服务是否支持向量化（POST /embeddings）：
     * 支持 → 自动使用同一接口地址与 Key 配置向量化端点（用户无需额外配置）；
     * 不支持（如 DeepSeek 官方无 embedding 模型）→ 不配置，向量化回落到平台端点池。
     */
    private void autoDetectEmbedding(AiUserConfigEntity entity) {
        String[] candidates = {"text-embedding-3-small", "BAAI/bge-m3"};
        String decryptedKey;
        try {
            decryptedKey = cryptoService.decrypt(entity.getApiKey());
        } catch (Exception ex) {
            return;
        }
        String normalized = normalizeBaseUrl(entity.getBaseUrl());
        for (String model : candidates) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("model", model);
                payload.put("input", Collections.singletonList("test"));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.setBearerAuth(decryptedKey);
                RestTemplate probe = createTestRestTemplate(5000, 15000);
                ResponseEntity<Map> resp = probe.exchange(
                        normalized + "/embeddings",
                        HttpMethod.POST,
                        new HttpEntity<>(payload, headers),
                        Map.class);
                if (resp.getStatusCode().is2xxSuccessful()) {
                    entity.setEmbeddingBaseUrl(entity.getBaseUrl());
                    entity.setEmbeddingApiKey(entity.getApiKey());
                    entity.setEmbeddingModel(model);
                    log.info("Auto-detected embedding support: baseUrl={} model={}", entity.getBaseUrl(), model);
                    return;
                }
            } catch (Exception ignored) {
                // 该模型不支持，尝试下一个候选
            }
        }
        entity.setEmbeddingBaseUrl(null);
        entity.setEmbeddingApiKey(null);
        entity.setEmbeddingModel(null);
        log.info("Current AI service does not support embedding, vectorization will fall back to platform pool: baseUrl={}",
                entity.getBaseUrl());
    }

    private String normalizeBaseUrl(String url) {
        return url == null ? "" : (url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
    }

    private RestTemplate createTestRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        NonRedirectingSimpleClientHttpRequestFactory factory = new NonRedirectingSimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    private String normalizeProvider(String provider) {
        String value = provider == null ? "" : provider.trim().toLowerCase();
        if (value.length() == 0) {
            return "deepseek";
        }
        for (String p : PROVIDERS) {
            if (p.equals(value)) {
                return p;
            }
        }
        return "custom";
    }

    private String maskKey(String key) {
        if (!StringUtils.hasText(key)) {
            return "";
        }
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() == 0 ? null : trimmed;
    }

    /**
     * 解析后的云端 API 配置。
     */
    public static class ResolvedApiConfig {
        private final String baseUrl;
        private final String apiKey;
        private final String model;

        public ResolvedApiConfig(String baseUrl, String apiKey, String model) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
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
    }
}
