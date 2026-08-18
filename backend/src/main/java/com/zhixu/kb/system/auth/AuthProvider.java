package com.zhixu.kb.system.auth;

import java.util.Map;

/**
 * 普通认证适配器接口（账号密码、邮箱/短信验证码等）。
 */
public interface AuthProvider {

    /**
     * 返回认证方式标识，如 {@link AuthMethod#PASSWORD}。
     */
    String method();

    /**
     * 执行认证，返回认证成功的用户。
     *
     * @param params 前端传入的参数映射
     */
    AuthResult authenticate(Map<String, Object> params);
}
