package com.zhixu.kb.system.controller;

import com.zhixu.kb.ai.AIEngineAdapterRouter;
import com.zhixu.kb.common.AlertService;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查：/api/health 简单探活，/api/v1/health 对已登录用户返回 AI 引擎状态。
 * 该端点匿名可达（用于探活），详细信息对匿名调用者收敛，
 * 防止引擎类型/覆盖开关/告警数等部署细节被外部测绘。
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final AIEngineAdapterRouter adapterRouter;
    private final AlertService alertService;
    private final AiProperties aiProperties;

    @GetMapping("/api/health")
    public Result<String> health() {
        return Result.success("ok");
    }

    @GetMapping("/api/v1/health")
    public Result<Map<String, Object>> richHealth() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "UP");
        // 匿名调用只返回存活状态；引擎/告警细节仅对已认证用户可见
        if (com.zhixu.kb.common.utils.SecurityUtils.getUserId() == null) {
            return Result.success(data);
        }
        data.put("serverTime", LocalDateTime.now());
        data.put("aiEngineType", adapterRouter.engineType());
        data.put("aiEngineConfiguredType", adapterRouter.configuredEngineType());
        data.put("aiEngineOverrideEnabled", adapterRouter.overrideEnabled());
        data.put("aiEngineOverrideType", adapterRouter.overrideEngineType());
        data.put("aiHealthy", adapterRouter.current().isHealthy());
        // 平台默认云端 API（全局 AI_API_KEY）是否已配置：配置后所有用户免 Key 可用
        data.put("platformDefaultApi", StringUtils.hasText(aiProperties.getApi().getApiKey()));
        data.put("recentAlertCount", alertService.recent(5).size());
        return Result.success(data);
    }
}
