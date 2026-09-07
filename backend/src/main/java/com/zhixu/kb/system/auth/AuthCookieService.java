package com.zhixu.kb.system.auth;

import com.zhixu.kb.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 会话 Cookie 签发与解析：JWT 存 HttpOnly Cookie（JS 不可读），替代 localStorage。
 * <ul>
 *   <li>写入一律走 Set-Cookie 响应头手拼（javax Cookie 无 SameSite 属性）：{@code Path=/; HttpOnly; SameSite=Lax}，
 *       生产 HTTPS 下追加 {@code Secure}（{@code app.auth.cookie-secure}）。</li>
 *   <li>读取优先级：Cookie → Authorization Bearer（兼容非浏览器 API 调用）；
 *       旧登录响应体不再返回 token，前端不得再存储。</li>
 *   <li>token 字符白名单校验：JWT 为 base64url，只含 {@code [A-Za-z0-9-_.]}，
 *       含其它字符（{@code ;,} 空格等）直接拒绝，防止响应头注入。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AuthCookieService {

    private final AppProperties appProperties;

    @Value("${jwt.expiration:86400000}")
    private long jwtExpirationMs;

    /**
     * 登录成功后签发会话 Cookie。
     */
    public void addSessionCookie(HttpServletResponse response, String token) {
        assertSafeToken(token);
        String name = cookieName();
        long maxAge = Math.max(60, jwtExpirationMs / 1000);
        StringBuilder sb = new StringBuilder();
        sb.append(name).append('=').append(token)
                .append("; Path=/")
                .append("; Max-Age=").append(maxAge)
                .append("; HttpOnly")
                .append("; SameSite=").append(sameSite());
        if (Boolean.TRUE.equals(appProperties.getAuth().getCookieSecure())) {
            sb.append("; Secure");
        }
        response.addHeader("Set-Cookie", sb.toString());
    }

    /**
     * 登出时清除会话 Cookie（Max-Age=0，属性与签发时一致，否则浏览器不认）。
     */
    public void clearSessionCookie(HttpServletResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append(cookieName()).append("=deleted; Path=/; Max-Age=0; HttpOnly")
                .append("; SameSite=").append(sameSite());
        if (Boolean.TRUE.equals(appProperties.getAuth().getCookieSecure())) {
            sb.append("; Secure");
        }
        response.addHeader("Set-Cookie", sb.toString());
    }

    /**
     * 解析当前请求携带的会话 token：Cookie 优先，缺失时回落 Authorization 头。
     */
    public String resolveToken(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String name = cookieName();
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie != null && name.equals(cookie.getName())
                        && StringUtils.hasText(cookie.getValue())) {
                    return cookie.getValue();
                }
            }
        }
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            String token = header.substring(7).trim();
            return StringUtils.hasText(token) ? token : null;
        }
        return null;
    }

    private String cookieName() {
        String name = appProperties.getAuth().getCookieName();
        if (!StringUtils.hasText(name) || !name.matches("[A-Za-z0-9_\\-]{1,64}")) {
            return "ZHIXU_SESSION";
        }
        return name;
    }

    private String sameSite() {
        String mode = appProperties.getAuth().getCookieSameSite();
        if ("Strict".equalsIgnoreCase(mode)) {
            return "Strict";
        }
        if ("None".equalsIgnoreCase(mode)
                && Boolean.TRUE.equals(appProperties.getAuth().getCookieSecure())) {
            // SameSite=None 必须配 Secure，否则浏览器直接拒收；未开 Secure 时回落 Lax
            return "None";
        }
        return "Lax";
    }

    private void assertSafeToken(String token) {
        if (!StringUtils.hasText(token) || token.length() > 4096
                || !token.matches("[A-Za-z0-9\\-_.]+")) {
            throw new IllegalArgumentException("非法会话 token（拒绝写入 Cookie）");
        }
    }
}
