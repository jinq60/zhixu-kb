package com.zhixu.kb.system.auth;

import com.zhixu.kb.system.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 桌面版自动登录过滤器（仅 desktop profile，位于 JWT 过滤器之后）：
 * 请求未携带任何凭证时，自动以本地单用户身份执行——桌面版无登录概念，
 * 一切数据归属本机唯一用户。
 */
@Slf4j
@Component
@Profile("desktop")
@RequiredArgsConstructor
public class DesktopAutoLoginFilter extends OncePerRequestFilter {

    private final DesktopUserService desktopUserService;
    private final DesktopActivationService activationService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (SecurityContextHolder.getContext().getAuthentication() == null && uri.startsWith("/api/")) {
            // 激活门禁：未完成浏览器验证前，除应用探测外的业务 API 一律拒绝
            if (!isActivated() && !uri.startsWith("/api/app-config")) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":403,\"message\":\"桌面版尚未完成验证，请先激活\",\"data\":{\"needActivation\":true}}");
                return;
            }
            try {
                LoginUser loginUser = desktopUserService.getLoginUser();
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (Exception ex) {
                log.warn("Desktop auto login failed: {}", ex.getMessage());
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isActivated() {
        try {
            return activationService.isActivated();
        } catch (Exception ex) {
            return false;
        }
    }
}
