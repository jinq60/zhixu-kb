package com.zhixu.kb.system.model;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

@Data
public class UnifiedLoginRequest {

    @NotBlank(message = "认证方式不能为空")
    private String method;

    private Map<String, Object> params;
}
