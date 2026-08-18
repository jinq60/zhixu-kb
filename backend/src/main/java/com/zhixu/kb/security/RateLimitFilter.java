package com.zhixu.kb.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 接口限流过滤器：按用户/IP 每分钟请求数限制，防止滥用。
 * /api/auth/login 与 /api/auth/guest 走独立且更严格的 IP 维度限流（防暴力破解/刷号）。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_IP_PER_MINUTE = 20;

    private static class Counter {
        private long minuteWindow;
        private final AtomicInteger count = new AtomicInteger(0);
    }

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(AppProperties appProperties, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/health")
                || path.startsWith("/api/v1/health")
                || path.startsWith("/actuator")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean authEntry = path.startsWith("/api/auth/login") || path.startsWith("/api/auth/guest");

        String identity;
        int threshold;
        if (authEntry) {
            identity = "authip:" + resolveClientIp(request);
            threshold = AUTH_IP_PER_MINUTE;
        } else {
            identity = resolveIdentity(request);
            threshold = Math.max(1, appProperties.getRateLimit().getPerMinute());
        }

        long currentMinute = Instant.now().getEpochSecond() / 60;
        Counter counter = counters.computeIfAbsent(identity, k -> new Counter());
        synchronized (counter) {
            if (counter.minuteWindow != currentMinute) {
                counter.minuteWindow = currentMinute;
                counter.count.set(0);
            }
            int after = counter.count.incrementAndGet();
            if (after > threshold) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                Result<Void> error = Result.error(429, "请求过于频繁，请稍后再试");
                response.getWriter().write(objectMapper.writeValueAsString(error));
                return;
            }
        }

        cleanupExpired();

        filterChain.doFilter(request, response);
    }

    private String resolveIdentity(HttpServletRequest request) {
        Long userId = SecurityUtils.getUserId();
        if (userId != null) {
            return "user:" + userId;
        }
        return "ip:" + resolveClientIp(request);
    }

    /**
     * 获取客户端真实 IP：仅在请求来自本机代理时信任 X-Forwarded-For / X-Real-IP，
     * 避免外部请求伪造代理头绕过限流。
     */
    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        boolean localProxy = "127.0.0.1".equals(remoteAddr) || "::1".equals(remoteAddr);
        if (localProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
        }
        return remoteAddr;
    }

    /**
     * 惰性清理：仅当计数器规模过大时回收超过 2 分钟的旧条目，避免内存无限增长。
     */
    private void cleanupExpired() {
        if (counters.size() <= 4096) {
            return;
        }
        long nowMinute = Instant.now().getEpochSecond() / 60;
        Iterator<Map.Entry<String, Counter>> it = counters.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Counter> entry = it.next();
            if (nowMinute - entry.getValue().minuteWindow > 2) {
                it.remove();
            }
        }
    }
}
