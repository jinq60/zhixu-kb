package com.zhixu.kb.ai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixu.kb.ai.AiApiPool;
import com.zhixu.kb.ai.entity.AiEndpointEntity;
import com.zhixu.kb.ai.mapper.AiEndpointMapper;
import com.zhixu.kb.ai.model.AiEndpointSaveRequest;
import com.zhixu.kb.ai.model.AiEndpointView;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.config.NonRedirectingSimpleClientHttpRequestFactory;
import com.zhixu.kb.common.utils.SafeUrlValidator;
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

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 端点管理：数据库 CRUD（Key 加密存储）、连接测试、动态刷新端点池。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiEndpointService {

    private final AiEndpointMapper endpointMapper;
    private final CryptoService cryptoService;
    private final AiApiPool aiApiPool;
    private final RestTemplate restTemplate;

    /**
     * 启动时加载数据库维护的启用端点到端点池（否则重启后端点失效）。
     */
    @PostConstruct
    public void initPool() {
        refreshPool();
    }

    @Transactional(readOnly = true)
    public List<AiEndpointView> list() {
        List<AiEndpointEntity> entities = endpointMapper.selectList(new LambdaQueryWrapper<AiEndpointEntity>()
                .orderByAsc(AiEndpointEntity::getId));
        List<AiEndpointView> views = new ArrayList<>();
        for (AiEndpointEntity entity : entities) {
            views.add(toView(entity));
        }
        return views;
    }

    @Transactional
    public AiEndpointView save(AiEndpointSaveRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "端点参数不能为空");
        }
        String baseUrl = trimToNull(request.getBaseUrl());
        String model = trimToNull(request.getModel());
        if (!StringUtils.hasText(baseUrl)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接口地址不能为空");
        }
        SafeUrlValidator.validateOrThrow(baseUrl);
        if (!StringUtils.hasText(model)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "模型名称不能为空");
        }

        AiEndpointEntity entity;
        if (request.getId() != null) {
            entity = endpointMapper.selectById(request.getId());
            if (entity == null) {
                throw new BusinessException(ResultCode.NOT_FOUND, "端点不存在");
            }
        } else {
            entity = new AiEndpointEntity();
            entity.setCreateTime(LocalDateTime.now());
        }
        entity.setBaseUrl(baseUrl);
        entity.setModel(model);
        String newKey = trimToNull(request.getApiKey());
        if (StringUtils.hasText(newKey)) {
            entity.setApiKey(cryptoService.encrypt(newKey));
        } else if (!StringUtils.hasText(entity.getApiKey())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "API Key 不能为空（未配置过 Key 时必须填写）");
        }
        entity.setEnabled(request.getEnabled() == null || request.getEnabled() ? 1 : 0);
        entity.setRemark(trimToNull(request.getRemark()));
        entity.setUpdateTime(LocalDateTime.now());

        if (entity.getId() == null) {
            endpointMapper.insert(entity);
        } else {
            endpointMapper.updateById(entity);
        }
        refreshPool();
        return toView(entity);
    }

    @Transactional
    public void delete(Long id) {
        AiEndpointEntity entity = endpointMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "端点不存在");
        }
        endpointMapper.deleteById(id);
        refreshPool();
    }

    @Transactional
    public AiEndpointView toggle(Long id, Boolean enabled) {
        AiEndpointEntity entity = endpointMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "端点不存在");
        }
        entity.setEnabled(Boolean.TRUE.equals(enabled) ? 1 : 0);
        entity.setUpdateTime(LocalDateTime.now());
        endpointMapper.updateById(entity);
        refreshPool();
        return toView(entity);
    }

    /**
     * 连接测试：调用 OpenAI 兼容 /models 接口。
     */
    public Map<String, Object> test(Long id) {
        AiEndpointEntity entity = endpointMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "端点不存在");
        }
        String key = cryptoService.decrypt(entity.getApiKey());
        SafeUrlValidator.validateBeforeRequest(entity.getBaseUrl());
        String normalized = entity.getBaseUrl().endsWith("/")
                ? entity.getBaseUrl().substring(0, entity.getBaseUrl().length() - 1)
                : entity.getBaseUrl();

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
            result.put("model", entity.getModel());
        } catch (Exception ex) {
            result.put("success", false);
            result.put("message", "连接失败：" + ex.getMessage());
        }
        return result;
    }

    private RestTemplate createTestRestTemplate(int connectTimeoutMs, int readTimeoutMs) {
        NonRedirectingSimpleClientHttpRequestFactory factory = new NonRedirectingSimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        return new RestTemplate(factory);
    }

    private void refreshPool() {
        try {
            List<AiEndpointEntity> entities = listEnabledEntities();
            for (AiEndpointEntity entity : entities) {
                entity.setApiKey(cryptoService.decrypt(entity.getApiKey()));
            }
            aiApiPool.refreshFromDb(entities);
        } catch (Exception ex) {
            log.warn("refresh ai endpoint pool failed: {}", ex.getMessage());
        }
    }

    private List<AiEndpointEntity> listEnabledEntities() {
        return endpointMapper.selectList(new LambdaQueryWrapper<AiEndpointEntity>()
                .eq(AiEndpointEntity::getEnabled, 1)
                .orderByAsc(AiEndpointEntity::getId));
    }

    private AiEndpointView toView(AiEndpointEntity entity) {
        AiEndpointView view = new AiEndpointView();
        view.setId(entity.getId());
        view.setBaseUrl(entity.getBaseUrl());
        view.setApiKeyMasked(maskKey(cryptoService.decrypt(entity.getApiKey())));
        view.setModel(entity.getModel());
        view.setEnabled(entity.getEnabled() == null || entity.getEnabled() == 1);
        view.setRemark(entity.getRemark());
        // 运行状态
        AiApiPool.EndpointStatus status = aiApiPool.getStatus(entity.getId());
        view.setCooldown(status.isCooldown());
        view.setStatus(status.isCooldown() ? "cooldown" : (view.getEnabled() ? "ready" : "disabled"));
        view.setStatusText(status.isCooldown() ? "冷却中" : (view.getEnabled() ? "可用" : "已停用"));
        if (status.getLastUsedMs() > 0) {
            view.setLastUsed(java.time.Instant.ofEpochMilli(status.getLastUsedMs()).toString());
        }
        return view;
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
}
