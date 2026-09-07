package com.zhixu.kb.system.auth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Duration;

/**
 * OAuth state 存储（防登录 CSRF）：authorize 时生成，10 分钟有效，callback 时一次性消费。
 * Cookie 会话模式下登录态直接写 Set-Cookie，不再需要 exchange code 中转，相关方法已删除。
 * Redis 优先，故障时降级为 Caffeine 本地缓存（带 TTL 与容量上限）。
 */
@Component
public class OAuthStateStore {

    private static final Duration STATE_TTL = Duration.ofMinutes(10);
    private static final String STATE_PATTERN = "^[a-f0-9]{64}$";

    private final Cache<String, String> stateCache;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public OAuthStateStore(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.stateCache = Caffeine.newBuilder()
                .expireAfterWrite(STATE_TTL)
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
        // Redis 为唯一事实源：原子 DEL 返回值即"是否首次消费"，
        // 消除多实例部署下本地副本导致的重放窗口
        if (redisTemplate != null) {
            try {
                boolean consumed = Boolean.TRUE.equals(redisTemplate.delete("auth:oauth:state:" + state));
                stateCache.invalidate(state);
                return consumed;
            } catch (Exception ignored) {
                // Redis 故障：降级为本地一次性消费
            }
        }
        // asMap().remove 为原子取删，保证降级模式下同样只能消费一次
        return stateCache.asMap().remove(state) != null;
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
