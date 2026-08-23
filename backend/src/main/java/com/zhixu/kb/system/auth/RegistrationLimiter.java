package com.zhixu.kb.system.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 注册限流：单 IP 每日自动注册次数上限，防止“登录即注册”被滥用批量建号。
 * Redis 优先，故障时降级为 Caffeine 本地计数（带 TTL 与容量上限）。
 */
@Component
public class RegistrationLimiter {

    private static final Duration WINDOW = Duration.ofHours(24);

    private final Cache<String, AtomicInteger> localCounters;
    private final StringRedisTemplate redisTemplate;
    private final int maxPerDay;

    public RegistrationLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                               @Value("${app.registration.max-per-day:10}") int maxPerDay) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.maxPerDay = Math.max(1, maxPerDay);
        this.localCounters = Caffeine.newBuilder()
                .expireAfterWrite(WINDOW)
                .maximumSize(100_000)
                .build();
    }

    public void checkAndIncrement(String ip) {
        int count = increment(ip);
        if (count > maxPerDay) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "今日注册次数已达上限，请明天再试");
        }
    }

    private int increment(String ip) {
        if (redisTemplate != null) {
            try {
                String key = "auth:reg:count:" + ip;
                // 原子初始化：仅当 key 不存在时以 TTL 创建 0 值；
                // 即使进程在初始化与自增之间崩溃，key 也会在窗口期后自动过期，不会造成永久封禁
                redisTemplate.opsForValue().setIfAbsent(key, "0", WINDOW);
                Long value = redisTemplate.opsForValue().increment(key);
                if (value != null) {
                    // 兜底：历史脏数据无 TTL 时补过期时间
                    Long ttl = redisTemplate.getExpire(key);
                    if (ttl != null && ttl < 0) {
                        redisTemplate.expire(key, WINDOW);
                    }
                    return value.intValue();
                }
            } catch (Exception ignored) {
                // fallback to in-memory only
            }
        }
        AtomicInteger local = localCounters.get(ip, k -> new AtomicInteger(0));
        return local.incrementAndGet();
    }
}
