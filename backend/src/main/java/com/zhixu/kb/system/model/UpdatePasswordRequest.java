package com.zhixu.kb.system.model;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class UpdatePasswordRequest {

    /** 旧密码：未设置过密码时允许为空 */
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 32, message = "新密码长度必须在 6-32 位之间")
    private String newPassword;
}
