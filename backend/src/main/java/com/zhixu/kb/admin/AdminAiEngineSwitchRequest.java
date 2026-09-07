package com.zhixu.kb.admin;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

/**
 * AI 引擎运行时切换请求。
 */
@Data
public class AdminAiEngineSwitchRequest {
    @NotBlank(message = "engineType不能为空")
    private String engineType;
}
