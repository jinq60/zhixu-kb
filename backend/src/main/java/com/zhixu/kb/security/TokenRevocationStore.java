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
    private final StringRedisTemplate redisTemplate;

    private static final Duration REVOKED_TOKEN_TTL = Duration.ofDays(3);

    public TokenRevocationStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.revokedTokens = Caffeine.newBuilder()
                .expireAfterWrite(REVOKED_TOKEN_TTL)
                .maximumSize(100_000)
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
                redisTemplate.opsForValue().set(redisKey(key), "1", REVOKED_TOKEN_TTL);
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
