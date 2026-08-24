package com.zhixu.kb.system.auth;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 短信验证码服务：生成、发送、校验。
 * 优先使用 Redis，Redis 故障时降级为内存存储（带 TTL 与惰性清理）。
 * 安全加固：SecureRandom 生成、恒定时间比对、单验证码失败次数上限、日志脱敏。
 */
@Slf4j
@Service
public class SmsCodeService {

    private static final String REDIS_PREFIX = "auth:sms:code:";
    private static final String REDIS_SEND_PREFIX = "auth:sms:send:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int CODE_LENGTH = 6;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int MEMORY_CLEANUP_THRESHOLD = 1000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final Map<String, CodeEntry> memoryStore = new ConcurrentHashMap<>();
    private final Map<String, Long> memorySendTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> verifyAttempts = new ConcurrentHashMap<>();

    private static final class CodeEntry {
        private final String code;
        private final long expiresAt;

        CodeEntry(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    public SmsCodeService(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public void send(String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "手机号格式不正确");
        }
        checkResend(phone);
        String code = generateCode();
        // 新验证码发放即重置失败计数，避免旧码的错误尝试把新码也锁死
        verifyAttempts.remove(phone);
        store(phone, code);
        long now = System.currentTimeMillis();
        if (!redisSet(REDIS_SEND_PREFIX + phone, String.valueOf(now), RESEND_INTERVAL)) {
            memorySendTime.put(phone, now);
        }
        // 实际项目中替换为短信网关调用
        log.info("短信验证码已生成 phone={}", maskPhone(phone));
    }

    public void verify(String phone, String code) {
        if (phone == null || code == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "手机号或验证码不能为空");
        }
        AtomicInteger attempts = verifyAttempts.get(phone);
        if (attempts != null && attempts.get() >= MAX_VERIFY_ATTEMPTS) {
            remove(phone);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码错误次数过多，请重新获取");
        }
        String stored = fetch(phone);
        if (stored == null) {
            // 未发放过验证码时不创建失败计数条目：防止攻击者用随机手机号灌大计数表、
            // 触发全量清理后重置目标手机号的计数（绕过单码 5 次上限）
            throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码已过期，请重新获取");
        }
        if (!constantTimeEquals(stored, code)) {
            // 仅在真实比对失败时才创建/递增计数条目
            verifyAttempts.computeIfAbsent(phone, k -> new AtomicInteger(0)).incrementAndGet();
            throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码错误");
        }
        verifyAttempts.remove(phone);
        remove(phone);
    }

    private void checkResend(String phone) {
        // 注意：业务限流异常必须抛在 try/catch 之外——BusinessException 是 RuntimeException，
        // 若在 try 内抛出会被下面的 catch(Exception) 吞掉，导致 Redis 在线时限流完全失效（可被短信轰炸）
        Boolean exists = null;
        if (redisTemplate != null) {
            try {
                exists = redisTemplate.hasKey(REDIS_SEND_PREFIX + phone);
            } catch (Exception e) {
                log.warn("Redis 检查发送频率失败，降级内存判断");
            }
        }
        if (Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "发送过于频繁，请稍后再试");
        }
        Long last = memorySendTime.get(phone);
        if (last != null && System.currentTimeMillis() - last < RESEND_INTERVAL.toMillis()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "发送过于频繁，请稍后再试");
        }
    }

    private void store(String phone, String code) {
        if (redisSet(REDIS_PREFIX + phone, code, CODE_TTL)) {
            return;
        }
        cleanupIfNeeded();
        memoryStore.put(phone, new CodeEntry(code, System.currentTimeMillis() + CODE_TTL.toMillis()));
    }

    private String fetch(String phone) {
        if (redisTemplate != null) {
            String value = redisGet(REDIS_PREFIX + phone);
            if (value != null) {
                return value;
            }
        }
        CodeEntry entry = memoryStore.get(phone);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAt) {
            memoryStore.remove(phone);
            return null;
        }
        return entry.code;
    }

    private void remove(String phone) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(REDIS_PREFIX + phone);
            } catch (Exception ignored) {
            }
        }
        memoryStore.remove(phone);
    }

    private boolean redisSet(String key, String value, Duration ttl) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            redisTemplate.opsForValue().set(key, value, ttl);
            return true;
        } catch (Exception e) {
            log.warn("Redis 写入失败，验证码降级为内存存储");
            return false;
        }
    }

    private String redisGet(String key) {
        if (redisTemplate == null) {
            return null;
        }
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.warn("Redis 读取失败，验证码降级为内存存储");
            return null;
        }
    }

    private void cleanupIfNeeded() {
        if (memoryStore.size() <= MEMORY_CLEANUP_THRESHOLD) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CodeEntry>> it = memoryStore.entrySet().iterator();
        while (it.hasNext()) {
            if (now > it.next().getValue().expiresAt) {
                it.remove();
            }
        }
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(SECURE_RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
