package com.zhixu.kb.system.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class OAuthExchangeRequest {

    @NotBlank(message = "授权码不能为空")
    private String code;
}
