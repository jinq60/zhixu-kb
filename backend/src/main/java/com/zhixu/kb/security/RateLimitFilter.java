package com.zhixu.kb.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.common.utils.ClientIpResolver;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 接口限流过滤器：按用户/IP 每分钟请求数限制，防止滥用。
 * /api/auth/login 等认证入口走独立且更严格的 IP 维度限流（防暴力破解/刷号）。
 *
 * 计数存储：优先 Redis 固定窗口（Lua 原子 INCR+PEXPIRE，多实例部署共享同一窗口）；
 * Redis 不可用时降级为本地 Caffeine 计数（单实例语义，与 TokenRevocationStore 一致的可用性优先策略）。
 */
@Slf4j
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

    /** Redis key 前缀与窗口 TTL：TTL 覆盖窗口边界，避免跨分钟残留计数 */
    private static final String REDIS_KEY_PREFIX = "ratelimit:";
    private static final long WINDOW_TTL_MS = 120_000L;

    /**
     * 原子固定窗口计数：INCR 后首次置 TTL。
     * 单独的 SET EX 不能保证原子性；Lua 保证"首次 INCR 才设置过期"，进程崩溃也不会留下永生 key。
     */
    private static final DefaultRedisScript<Long> INCR_WINDOW_SCRIPT = new DefaultRedisScript<>(
            "local c = redis.call('INCR', KEYS[1]) "
                    + "if c == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end "
                    + "return c",
            Long.class);

    private static class Counter {
        private long minuteWindow;
        private final AtomicInteger count = new AtomicInteger(0);
    }

    /** 本地降级计数器：2 分钟无访问自动过期，避免内存无限增长与手动清理开销 */
    private final Cache<String, Counter> counters = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(2))
            .maximumSize(50_000)
            .build();
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;
    private final StringRedisTemplate redisTemplate;

    public RateLimitFilter(AppProperties appProperties, ObjectMapper objectMapper,
                           ClientIpResolver clientIpResolver,
                           ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
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
        if (!tryAcquire(identity, currentMinute, threshold)) {
            response.setStatus(429);
            response.setHeader("Retry-After", "60");
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            Result<Void> error = Result.error(429, "请求过于频繁，请稍后再试");
            response.getWriter().write(objectMapper.writeValueAsString(error));
            return;
        }

        filterChain.doFilter(request, response);
    }

    /** 获取一次计数许可：Redis 原子窗口优先，故障时降级本地计数 */
    private boolean tryAcquire(String identity, long currentMinute, int threshold) {
        if (redisTemplate != null) {
            try {
                Long count = redisTemplate.execute(
                        INCR_WINDOW_SCRIPT,
                        Collections.singletonList(REDIS_KEY_PREFIX + identity + ":" + currentMinute),
                        String.valueOf(WINDOW_TTL_MS));
                if (count != null) {
                    return count <= threshold;
                }
            } catch (Exception ex) {
                log.warn("Rate limit redis incr failed, fallback to local counter: {}", ex.getMessage());
            }
        }
        return localTryAcquire(identity, currentMinute, threshold);
    }

    private boolean localTryAcquire(String identity, long currentMinute, int threshold) {
        Counter counter = counters.get(identity, k -> new Counter());
        synchronized (counter) {
            if (counter.minuteWindow != currentMinute) {
                counter.minuteWindow = currentMinute;
                counter.count.set(0);
            }
            return counter.count.incrementAndGet() <= threshold;
        }
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
