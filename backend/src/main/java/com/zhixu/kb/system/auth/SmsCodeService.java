package com.zhixu.kb.system.auth;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短信验证码服务：生成、发送、校验。
 * 当前环境未接入真实短信网关，仅打印验证码用于调试；优先使用 Redis，否则内存存储。
 */
@Slf4j
@Service
public class SmsCodeService {

    private static final String REDIS_PREFIX = "auth:sms:code:";
    private static final String REDIS_SEND_PREFIX = "auth:sms:send:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final Random RANDOM = new Random();

    private final StringRedisTemplate redisTemplate;
    private final Map<String, CodeEntry> memoryStore = new ConcurrentHashMap<>();
    private final Map<String, Long> memorySendTime = new ConcurrentHashMap<>();

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

    public String send(String phone) {
        if (phone == null || !phone.matches("^1[3-9]\\d{9}$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "手机号格式不正确");
        }
        checkResend(phone);
        String code = generateCode();
        store(phone, code);
        memorySendTime.put(phone, System.currentTimeMillis());
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(REDIS_SEND_PREFIX + phone,
                    String.valueOf(System.currentTimeMillis()), RESEND_INTERVAL);
        }
        // 实际项目中替换为短信网关调用
        log.info("短信验证码已生成 phone={} code={}", phone, code);
        return code;
    }

    public void verify(String phone, String code) {
        if (phone == null || code == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "手机号或验证码不能为空");
        }
        String stored = fetch(phone);
        if (stored == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码已过期，请重新获取");
        }
        if (!stored.equalsIgnoreCase(code)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码错误");
        }
        remove(phone);
    }

    private void checkResend(String phone) {
        if (redisTemplate != null) {
            Boolean exists = redisTemplate.hasKey(REDIS_SEND_PREFIX + phone);
            if (Boolean.TRUE.equals(exists)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "发送过于频繁，请稍后再试");
            }
            return;
        }
        Long last = memorySendTime.get(phone);
        if (last != null && System.currentTimeMillis() - last < RESEND_INTERVAL.toMillis()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "发送过于频繁，请稍后再试");
        }
    }

    private void store(String phone, String code) {
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(REDIS_PREFIX + phone, code, CODE_TTL);
            return;
        }
        memoryStore.put(phone, new CodeEntry(code, System.currentTimeMillis() + CODE_TTL.toMillis()));
    }

    private String fetch(String phone) {
        if (redisTemplate != null) {
            return redisTemplate.opsForValue().get(REDIS_PREFIX + phone);
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
            redisTemplate.delete(REDIS_PREFIX + phone);
            return;
        }
        memoryStore.remove(phone);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
