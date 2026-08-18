package com.zhixu.kb.ai.controller;

import com.zhixu.kb.ai.model.AiConfigTestRequest;
import com.zhixu.kb.ai.model.AiUserConfig;
import com.zhixu.kb.ai.model.AiUserConfigSaveRequest;
import com.zhixu.kb.ai.service.UserAiConfigService;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户 AI 配置接口：多厂商 API Key 管理、连接测试。
 */
@RestController
@RequestMapping("/api/v1/ai/config")
@RequiredArgsConstructor
public class AiConfigController {

    private final UserAiConfigService configService;

    @GetMapping
    public Result<AiUserConfig> get() {
        return Result.success(configService.getView(SecurityUtils.getUserId()));
    }

    @PutMapping
    public Result<AiUserConfig> save(@RequestBody AiUserConfigSaveRequest request) {
        return Result.success("AI 配置已保存", configService.save(SecurityUtils.getUserId(), request));
    }

    @DeleteMapping
    public Result<Boolean> clear() {
        configService.delete(SecurityUtils.getUserId());
        return Result.success("已清除 AI 配置，将使用默认模型", Boolean.TRUE);
    }

    @PostMapping("/test")
    public Result<Map<String, Object>> test(@RequestBody AiConfigTestRequest request) {
        return Result.success(configService.testConnection(request.getBaseUrl(), request.getApiKey(), request.getModel()));
    }
}
