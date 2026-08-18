package com.zhixu.kb.system.model;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 用户账号资料：包含基本信息与已绑定的登录方式。
 */
@Data
@AllArgsConstructor
public class UserProfileResponse {

    private Long id;
    private String username;
    private String email;
    private String avatar;
    private List<String> roles;

    /** 是否已设置账号密码 */
    private boolean hasPassword;

    /** 已绑定的认证方式列表，如 password / email_code / github */
    private List<String> bindings;
}
