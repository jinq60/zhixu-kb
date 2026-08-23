package com.zhixu.kb.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.common.utils.ClientIpResolver;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.config.AppProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 接口限流过滤器：按用户/IP 每分钟请求数限制，防止滥用。
 * /api/auth/login 等认证入口走独立且更严格的 IP 维度限流（防暴力破解/刷号）。
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int AUTH_IP_PER_MINUTE = 20;

    private static final String[] AUTH_ENTRY_PREFIXES = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/email-code",
            "/api/auth/sms-code",
            "/api/auth/oauth"
    };

    private static class Counter {
        private long minuteWindow;
        private final AtomicInteger count = new AtomicInteger(0);
    }

    /** 限流计数器：2 分钟无访问自动过期，避免内存无限增长与手动清理开销 */
    private final Cache<String, Counter> counters = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(2))
            .maximumSize(50_000)
            .build();
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(AppProperties appProperties, ObjectMapper objectMapper, ClientIpResolver clientIpResolver) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
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

    private boolean isAuthEntry(String path) {
        for (String prefix : AUTH_ENTRY_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        boolean authEntry = isAuthEntry(path);

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
        Counter counter = counters.get(identity, k -> new Counter());
        synchronized (counter) {
            if (counter.minuteWindow != currentMinute) {
                counter.minuteWindow = currentMinute;
                counter.count.set(0);
            }
            int after = counter.count.incrementAndGet();
            if (after > threshold) {
                response.setStatus(429);
                response.setHeader("Retry-After", "60");
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                Result<Void> error = Result.error(429, "请求过于频繁，请稍后再试");
                response.getWriter().write(objectMapper.writeValueAsString(error));
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String resolveIdentity(HttpServletRequest request) {
        Long userId = SecurityUtils.getUserId();
        if (userId != null) {
            return "user:" + userId;
        }
        return "ip:" + resolveClientIp(request);
    }

    private String resolveClientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

}
