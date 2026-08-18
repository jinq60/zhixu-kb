package com.zhixu.kb.admin;

import com.zhixu.kb.ai.AIEngineAdapterRouter;
import com.zhixu.kb.ai.AIEngineRuntimeSwitchService;
import com.zhixu.kb.common.AlertEvent;
import com.zhixu.kb.common.AlertService;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 系统总览（管理端）：AI 引擎状态/切换、告警、限流参数、知识图谱总览。
 */
@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminSystemController {

    private final AIEngineAdapterRouter adapterRouter;
    private final AIEngineRuntimeSwitchService runtimeSwitchService;
    private final AlertService alertService;
    private final AppProperties appProperties;

    @GetMapping("/overview")
    public Result<Map<String, Object>> overview(@RequestParam(value = "alertLimit", defaultValue = "20") Integer alertLimit) {
        Map<String, Object> data = new HashMap<>();
        data.put("serverTime", LocalDateTime.now());
        data.put("status", "UP");
        data.put("aiEngineType", adapterRouter.engineType());
        data.put("aiEngineConfiguredType", adapterRouter.configuredEngineType());
        data.put("aiEngineOverrideEnabled", adapterRouter.overrideEnabled());
        data.put("aiEngineOverrideType", adapterRouter.overrideEngineType());
        data.put("aiHealthy", adapterRouter.current().isHealthy());
        data.put("monitorEnabled", appProperties.getMonitor().getEnabled());
        data.put("rateLimitPerMinute", appProperties.getRateLimit().getPerMinute());

        List<AlertEvent> alerts = alertService.recent(Math.max(1, alertLimit));
        data.put("recentAlertCount", alerts.size());
        data.put("recentAlerts", alerts);
        return Result.success(data);
    }

    @GetMapping("/ai-engine")
    public Result<Map<String, Object>> aiEngineState() {
        return Result.success(buildAiEngineState());
    }

    @PostMapping("/ai-engine")
    public Result<Map<String, Object>> switchAiEngine(@Valid @RequestBody AdminAiEngineSwitchRequest request) {
        String normalized = runtimeSwitchService.setOverrideEngineType(request.getEngineType());
        return Result.success("AI engine switched to " + normalized, buildAiEngineState());
    }

    @PostMapping("/ai-engine/reset")
    public Result<Map<String, Object>> resetAiEngine() {
        runtimeSwitchService.clearOverride();
        return Result.success("AI engine restored to config mode", buildAiEngineState());
    }

    @PostMapping("/emit-test-alert")
    public Result<Boolean> emitTestAlert(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> metadata = body == null ? Collections.emptyMap() : body;
        alertService.emit("TEST_ALERT", "INFO", "manual test alert from admin panel", metadata);
        return Result.success("Test alert emitted", Boolean.TRUE);
    }

    private Map<String, Object> buildAiEngineState() {
        Map<String, Object> data = new HashMap<>();
        data.put("effectiveEngineType", adapterRouter.engineType());
        data.put("configuredEngineType", adapterRouter.configuredEngineType());
        data.put("overrideEnabled", adapterRouter.overrideEnabled());
        data.put("overrideEngineType", adapterRouter.overrideEngineType());
        data.put("healthy", adapterRouter.current().isHealthy());
        data.put("availableEngineTypes", Collections.singletonList("api"));
        return data;
    }
}
