package com.zhixu.kb.system.model;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class RegisterRequest {
    @NotBlank(message = "用户名不能为空")
    @Size(min = 2, max = 24, message = "用户名长度需在 2-24 个字符之间")
    @Pattern(regexp = "^[\\u4E00-\\u9FA5A-Za-z0-9_\\- ]+$", message = "用户名仅支持中文、字母、数字、下划线、短横线与空格")
    private String username;
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 个字符之间")
    private String password;
    @Email(message = "邮箱格式不正确")
    @Size(max = 128, message = "邮箱过长")
    private String email;
    private String adminBootstrapKey;
}
