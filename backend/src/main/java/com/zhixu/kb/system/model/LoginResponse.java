package com.zhixu.kb.system.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 登录响应：只返回用户摘要，会话 JWT 改由 HttpOnly Cookie 下发，
 * 响应体不再携带 token（防止 XSS 从 JS 内存/响应中窃取持久凭证）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private Long userId;
    private String username;
    private List<String> roles;
}
