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

/**
 * 登录防爆破：按 (IP + 用户名) 与 用户名 两个维度统计连续失败次数。
 * <p>
 - 双维度设计：仅按 IP 限制可被代理池绕过；仅按用户名锁定会被恶意
 *   第三方利用实施"锁死受害者账号"的 DoS。组合后：
 *   单 IP 对单账号 5 次/15 分钟即拦截（防撞库），
 *   全局对单账号 30 次/15 分钟才拦截（抬高分布式爆破成本，同时降低被恶意锁号的概率）。
 * <p>
 * Redis 优先（多实例共享），故障时降级 Caffeine 本地计数（带 TTL 与容量上限）。
 */
@Component
public class LoginAttemptLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_PER_IP_USER = 5;
    private static final int MAX_PER_USER_GLOBAL = 30;

    private final Cache<String, int[]> localCounters;
    private final StringRedisTemplate redisTemplate;

    public LoginAttemptLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.localCounters = Caffeine.newBuilder()
                .expireAfterWrite(WINDOW)
                .maximumSize(100_000)
                .build();
    }

    /** 登录尝试前检查：超限则拒绝，避免继续执行昂贵的 BCrypt 比对（CPU 放大面） */
    public void check(String ip, String username) {
        if (exceeded(ip + "|" + username, MAX_PER_IP_USER, "ip-user")
                || exceeded("u|" + username, MAX_PER_USER_GLOBAL, "user")) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS,
                    "失败次数过多，请 15 分钟后再试");
        }
    }

    /** 认证失败时记录一次 */
    public void recordFailure(String ip, String username) {
        increment(ip + "|" + username);
        increment("u|" + username);
    }

    /** 认证成功后清除该 IP+用户名的失败计数（全局维度保留，防止成功包夹探测） */
    public void recordSuccess(String ip, String username) {
        reset(ip + "|" + username);
    }

    private boolean exceeded(String key, int max, String scope) {
        return currentCount(key) >= max;
    }

    private long currentCount(String key) {
        if (redisTemplate != null) {
            try {
                String value = redisTemplate.opsForValue().get(redisKey(key));
                if (value != null) {
                    return Long.parseLong(value);
                }
            } catch (Exception ignored) {
                // fall through to local
            }
        }
        int[] local = localCounters.getIfPresent(key);
        return local == null ? 0 : local[0];
    }

    private void increment(String key) {
        if (redisTemplate != null) {
            try {
                Boolean created = redisTemplate.opsForValue().setIfAbsent(redisKey(key), "1", WINDOW);
                if (!Boolean.TRUE.equals(created)) {
                    Long value = redisTemplate.opsForValue().increment(redisKey(key));
                    if (value != null && value == 1L) {
                        redisTemplate.expire(redisKey(key), WINDOW);
                    }
                }
                return;
            } catch (Exception ignored) {
                // fall through to local
            }
        }
        int[] counter = localCounters.get(key, k -> new int[1]);
        synchronized (counter) {
            counter[0]++;
        }
    }

    private void reset(String key) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(redisKey(key));
            } catch (Exception ignored) {
            }
        }
        localCounters.invalidate(key);
    }

    private String redisKey(String key) {
        return "auth:login-fail:" + key;
    }
}
