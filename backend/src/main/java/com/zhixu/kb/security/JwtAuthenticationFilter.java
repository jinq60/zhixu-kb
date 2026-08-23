package com.zhixu.kb.security;

import com.zhixu.kb.common.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * JWT 认证过滤器：解析 Bearer Token，校验有效性 + 撤销状态后注入安全上下文。
 * <p>
 * 安全加固：
 * <ul>
 *   <li>query string 中的 token 长度受限，防止超长 token DoS。</li>
 *   <li>仅当 token 能通过基础格式校验并解析出用户名后，才进入撤销检查。</li>
 *   <li>撤销存储内部使用 token 摘要作为 key，不直接缓存原始 token。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final int MAX_QUERY_TOKEN_LENGTH = 4096;

    private final JwtUtils jwtUtils;
    private final TokenRevocationStore revocationStore;
    private final com.zhixu.kb.system.service.CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        String token = null;
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            token = header.substring(7);
        }

        // Support token in query string ONLY for <img src="..."> resource loading:
        // 仅 GET /api/files/*/content 且 Accept 为图片时才允许，避免 token 进入
        // 浏览器历史/Referer/网关日志等泄露面。
        if (!StringUtils.hasText(token) && isQueryTokenAllowed(request)) {
            String queryToken = request.getParameter("token");
            if (StringUtils.hasText(queryToken) && queryToken.length() <= MAX_QUERY_TOKEN_LENGTH) {
                token = queryToken;
            }
        }

        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                // 1) 先解析并校验 JWT 基本格式，无效 token 直接跳过，不进入撤销逻辑
                String username = jwtUtils.extractUsername(token);
                if (!StringUtils.hasText(username)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                // 2) 再检查撤销状态
                if (revocationStore.isRevoked(token)) {
                    filterChain.doFilter(request, response);
                    return;
                }

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (!userDetails.isEnabled()) {
                    filterChain.doFilter(request, response);
                    return;
                }
                if (jwtUtils.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (Exception ignored) {
                // Invalid token should not break the request pipeline.
            }
        }

        filterChain.doFilter(request, response);
    }

    private boolean isQueryTokenAllowed(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/files/") || !path.endsWith("/content")) {
            return false;
        }
        String accept = request.getHeader("Accept");
        return accept != null && accept.contains("image/");
    }
}
