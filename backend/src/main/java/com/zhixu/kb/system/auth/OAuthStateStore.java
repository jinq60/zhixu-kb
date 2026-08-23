package com.zhixu.kb.system.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * OAuth 流程一次性凭证存储：
 * <ul>
 *   <li>authorize 时生成的 state（防登录 CSRF），10 分钟有效，callback 时一次性消费；</li>
 *   <li>callback 后换发给前端的一次性 exchange code（60 秒有效），前端凭它换取 JWT，
 *       避免 JWT 明文出现在重定向 URL / 浏览器历史 / Referer / 网关日志中。</li>
 * </ul>
 * Redis 优先，故障时降级为 Caffeine 本地缓存（带 TTL 与容量上限）。
 */
@Component
public class OAuthStateStore {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final Duration TOKEN_TTL = Duration.ofSeconds(60);
    private static final String STATE_PATTERN = "^[a-f0-9]{64}$";

    private final Cache<String, String> stateCache;
    private final Cache<String, String> tokenCache;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthStateStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.stateCache = Caffeine.newBuilder()
                .expireAfterWrite(STATE_TTL)
                .maximumSize(100_000)
                .build();
        this.tokenCache = Caffeine.newBuilder()
                .expireAfterWrite(TOKEN_TTL)
                .maximumSize(100_000)
                .build();
    }

    public String createState() {
        String state = randomHex();
        stateCache.put(state, "1");
        redisSet("auth:oauth:state:" + state, "1", STATE_TTL);
        return state;
    }

    public boolean consumeState(String state) {
        if (!isValidHex(state)) {
            return false;
        }
        // Redis 无条件删除（本地命中也不能跳过）：state/code 必须保证全局一次性
        boolean local = stateCache.getIfPresent(state) != null;
        stateCache.invalidate(state);
        boolean remote = false;
        if (redisTemplate != null) {
            try {
                remote = Boolean.TRUE.equals(redisTemplate.delete("auth:oauth:state:" + state));
            } catch (Exception ignored) {
                // fallback to in-memory only
            }
        }
        return local || remote;
    }

    public String createToken(String token) {
        String code = randomHex();
        tokenCache.put(code, token);
        redisSet("auth:oauth:token:" + code, token, TOKEN_TTL);
        return code;
    }

    public String takeToken(String code) {
        if (!isValidHex(code)) {
            return null;
        }
        // 本地与 Redis 副本都要删除，防止 code 被二次兑换（双重 JWT 发放）
        String local = tokenCache.getIfPresent(code);
        tokenCache.invalidate(code);
        String remote = null;
        if (redisTemplate != null) {
            try {
                remote = redisTemplate.opsForValue().get("auth:oauth:token:" + code);
                redisTemplate.delete("auth:oauth:token:" + code);
            } catch (Exception ignored) {
                // fallback to in-memory only
            }
        }
        return local != null ? local : remote;
    }

    private void redisSet(String key, String value, Duration ttl) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
        } catch (Exception ignored) {
            // fallback to in-memory only
        }
    }

    private String randomHex() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private boolean isValidHex(String value) {
        return value != null && value.matches(STATE_PATTERN);
    }
}
