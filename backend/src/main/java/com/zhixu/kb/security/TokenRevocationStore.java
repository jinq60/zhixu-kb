package com.zhixu.kb.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

/**
 * Token 撤销存储：Redis + 本地内存双写，登出后 token 立即失效。
 * <p>
 * 内存与 Redis 中均使用 token 的 SHA-256 摘要作为 key，避免原始 token 被长期缓存。
 */
@Component
public class TokenRevocationStore {

    private final Cache<String, Instant> revokedTokens;
    /** 用户级撤销时间点（修改密码等凭证变更时写入）：userId -> epoch ms */
    private final Cache<Long, Long> userRevokedAt;
    private final StringRedisTemplate redisTemplate;

    /**
     * 撤销记录保留时长：不短于 JWT 最大有效期（否则先撤销、后到期的 token 会"复活"）。
     * 取 max(3 天, jwt.expiration + 60s)。
     */
    private final Duration revokedTokenTtl;

    public TokenRevocationStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                @org.springframework.beans.factory.annotation.Value("${jwt.expiration:86400000}")
                                long jwtExpirationMs) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        long floor = Duration.ofDays(3).toMillis();
        this.revokedTokenTtl = Duration.ofMillis(Math.max(floor, jwtExpirationMs + 60_000L));
        this.revokedTokens = Caffeine.newBuilder()
                .expireAfterWrite(revokedTokenTtl)
                .maximumSize(100_000)
                .build();
        this.userRevokedAt = Caffeine.newBuilder()
                .expireAfterWrite(revokedTokenTtl)
                .maximumSize(50_000)
                .build();
    }

    public void revoke(String token) {
        if (!hasText(token)) {
            return;
        }
        String key = tokenKey(token);
        revokedTokens.put(key, Instant.now());
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(redisKey(key), "1", revokedTokenTtl);
            } catch (Exception ex) {
                // 本地缓存已记录（单实例部署下即全部生效），Redis 失败仅影响多实例场景，记录错误日志
                org.slf4j.LoggerFactory.getLogger(TokenRevocationStore.class)
                        .error("Token revocation Redis write failed: {}", ex.getMessage());
            }
        }
    }

    public boolean isRevoked(String token) {
        if (!hasText(token)) {
            return false;
        }
        String key = tokenKey(token);
        if (revokedTokens.getIfPresent(key) != null) {
            return true;
        }
        if (redisTemplate != null) {
            try {
                return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey(key)));
            } catch (Exception ex) {
                // 单实例部署下本地缓存即权威；Redis 故障时按"未撤销"处理，
                // 避免 Redis 抖动导致全部用户被误判登出（可用性优先），同时记录错误日志便于告警
                org.slf4j.LoggerFactory.getLogger(TokenRevocationStore.class)
                        .error("Token revocation Redis check failed: {}", ex.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * 用户级凭证撤销：记录"该时刻之前签发的全部 token 一并失效"。
     * 用于修改密码等凭证变更场景——无法枚举用户的所有活跃 token，
     * 以签发时间（JWT iat）为界批量作废。双写本地内存 + Redis（多实例生效）。
     */
    public void revokeUser(Long userId) {
        if (userId == null) {
            return;
        }
        long now = System.currentTimeMillis();
        userRevokedAt.put(userId, now);
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(userRevokedKey(userId), String.valueOf(now), revokedTokenTtl);
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(TokenRevocationStore.class)
                        .error("User revocation Redis write failed: {}", ex.getMessage());
            }
        }
    }

    /**
     * 查询用户凭证撤销时间点（epoch ms）；null 表示无用户级撤销。
     * JWT 签发时间早于该时间点的 token 应视为无效。
     */
    public Long getUserRevokedAt(Long userId) {
        if (userId == null) {
            return null;
        }
        Long local = userRevokedAt.getIfPresent(userId);
        if (local != null) {
            return local;
        }
        if (redisTemplate != null) {
            try {
                String value = redisTemplate.opsForValue().get(userRevokedKey(userId));
                if (value != null && !value.trim().isEmpty()) {
                    long revokedAt = Long.parseLong(value.trim());
                    // 回填本地，避免每次请求都打 Redis
                    userRevokedAt.put(userId, revokedAt);
                    return revokedAt;
                }
            } catch (Exception ex) {
                org.slf4j.LoggerFactory.getLogger(TokenRevocationStore.class)
                        .error("User revocation Redis check failed: {}", ex.getMessage());
            }
        }
        return null;
    }

    private String userRevokedKey(Long userId) {
        return "session:user-revoked:" + userId;
    }

    private String tokenKey(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            // SHA-256 is guaranteed in any Java runtime; fallback should never happen
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private String redisKey(String tokenKey) {
        return "session:revoked:" + tokenKey;
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty() && value.trim().length() > 0;
    }
}
