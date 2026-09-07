package com.zhixu.kb.system.auth;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 邮箱验证码服务：生成、发送、校验。
 * 优先使用 Redis，Redis 故障时降级为内存存储（带 TTL 与惰性清理）。
 * 安全加固：SecureRandom 生成、恒定时间比对、单验证码失败次数上限、日志脱敏。
 */
@Slf4j
@Service
public class EmailCodeService {

    private static final String REDIS_PREFIX = "auth:email:code:";
    private static final String REDIS_SEND_PREFIX = "auth:email:send:";
    private static final String BIND_REDIS_PREFIX = "auth:email:bind:code:";
    private static final String BIND_REDIS_SEND_PREFIX = "auth:email:bind:send:";
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int CODE_LENGTH = 6;
    private static final int MAX_VERIFY_ATTEMPTS = 5;
    private static final int MEMORY_CLEANUP_THRESHOLD = 1000;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final String mailUsername;
    private final Map<String, CodeEntry> memoryStore = new ConcurrentHashMap<>();
    private final Map<String, Long> memorySendTime = new ConcurrentHashMap<>();
    private final Map<String, CodeEntry> bindMemoryStore = new ConcurrentHashMap<>();
    private final Map<String, Long> bindMemorySendTime = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> verifyAttempts = new ConcurrentHashMap<>();

    private static final class CodeEntry {
        private final String code;
        private final long expiresAt;

        CodeEntry(String code, long expiresAt) {
            this.code = code;
            this.expiresAt = expiresAt;
        }
    }

    public EmailCodeService(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                            ObjectProvider<JavaMailSender> mailSenderProvider,
                            @Value("${spring.mail.username:}") String mailUsername) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.mailUsername = mailUsername;
    }

    public void send(String email) {
        doSend(email, REDIS_PREFIX, REDIS_SEND_PREFIX, memoryStore, memorySendTime,
                "login:" + email,
                "知序智能知识库 - 登录验证码", "您的验证码是：%s，5 分钟内有效，请勿泄露给任何人。");
    }

    public void sendBindCode(String email) {
        doSend(email, BIND_REDIS_PREFIX, BIND_REDIS_SEND_PREFIX, bindMemoryStore, bindMemorySendTime,
                "bind:" + email,
                "知序智能知识库 - 邮箱绑定验证码", "您的邮箱绑定验证码是：%s，5 分钟内有效，请勿泄露给任何人。");
    }

    public void verify(String email, String code) {
        doVerify(email, code, REDIS_PREFIX, memoryStore, "login:" + email);
    }

    public void verifyBindCode(String email, String code) {
        doVerify(email, code, BIND_REDIS_PREFIX, bindMemoryStore, "bind:" + email);
    }

    private void doSend(String email, String redisPrefix, String redisSendPrefix,
                          Map<String, CodeEntry> storeMap, Map<String, Long> sendMap,
                          String attemptsKey,
                          String subject, String bodyTemplate) {
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱格式不正确");
        }
        checkResend(email, redisSendPrefix, sendMap);
        if (mailSender == null || !StringUtils.hasText(mailUsername)) {
            log.warn("SMTP 未配置，邮箱验证码功能不可用 email={}", maskEmail(email));
            throw new BusinessException(ResultCode.SERVER_ERROR, "邮件服务未配置，请联系管理员");
        }
        String code = generateCode();
        // 新验证码发放即重置失败计数，避免旧码的错误尝试把新码也锁死
        verifyAttempts.remove(attemptsKey);
        redisClearAttempts(attemptsKey);
        store(email, code, redisPrefix, storeMap);
        long now = System.currentTimeMillis();
        if (!redisSet(redisSendPrefix + email, String.valueOf(now), RESEND_INTERVAL)) {
            sendMap.put(email, now);
        }
        sendRealEmail(email, code, subject, bodyTemplate);
        log.info("邮箱验证码已发送 email={}", maskEmail(email));
    }

    private void doVerify(String email, String code, String redisPrefix,
                          Map<String, CodeEntry> storeMap, String attemptsKey) {
        if (email == null || code == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱或验证码不能为空");
        }
        // P1-6 修复：计数器优先走 Redis（多实例共享），Redis 不可用才回落本地内存
        int sharedAttempts = redisAttempts(attemptsKey);
        if (sharedAttempts >= 0) {
            if (sharedAttempts >= MAX_VERIFY_ATTEMPTS) {
                remove(email, redisPrefix, storeMap);
                throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码错误次数过多，请重新获取");
            }
        } else {
            AtomicInteger attempts = verifyAttempts.get(attemptsKey);
            if (attempts != null && attempts.get() >= MAX_VERIFY_ATTEMPTS) {
                remove(email, redisPrefix, storeMap);
                throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码错误次数过多，请重新获取");
            }
        }
        String stored = fetch(email, redisPrefix, storeMap);
        if (stored == null) {
            // 未发放过验证码时不创建失败计数条目：防止攻击者用随机邮箱灌大计数表、
            // 触发全量清理后重置目标邮箱的计数（绕过单码 5 次上限）
            throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码已过期，请重新获取");
        }
        if (!constantTimeEquals(stored, code)) {
            // 仅在真实比对失败时才创建/递增计数条目（Redis 优先）
            if (!redisIncrAttempts(attemptsKey)) {
                verifyAttempts.computeIfAbsent(attemptsKey, k -> new AtomicInteger(0)).incrementAndGet();
            }
            throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码错误");
        }
        verifyAttempts.remove(attemptsKey);
        redisClearAttempts(attemptsKey);
        remove(email, redisPrefix, storeMap);
    }

    private void sendRealEmail(String email, String code, String subject, String bodyTemplate) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailUsername);
            message.setTo(email);
            message.setSubject(subject);
            message.setText(String.format(bodyTemplate, code));
            mailSender.send(message);
        } catch (MailException e) {
            log.error("发送邮件失败 email={}", maskEmail(email), e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "邮件发送失败，请检查 SMTP 配置");
        }
    }

    private void checkResend(String email, String redisSendPrefix, Map<String, Long> sendMap) {
        // 注意：业务限流异常必须抛在 try/catch 之外——BusinessException 是 RuntimeException，
        // 若在 try 内抛出会被下面的 catch(Exception) 吞掉，导致 Redis 在线时限流完全失效（可被邮件轰炸）
        Boolean exists = null;
        if (redisTemplate != null) {
            try {
                exists = redisTemplate.hasKey(redisSendPrefix + email);
            } catch (Exception e) {
                log.warn("Redis 检查发送频率失败，降级内存判断");
            }
        }
        if (Boolean.TRUE.equals(exists)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "发送过于频繁，请稍后再试");
        }
        Long last = sendMap.get(email);
        if (last != null && System.currentTimeMillis() - last < RESEND_INTERVAL.toMillis()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "发送过于频繁，请稍后再试");
        }
    }

    private void store(String email, String code, String redisPrefix, Map<String, CodeEntry> storeMap) {
        if (redisSet(redisPrefix + email, code, CODE_TTL)) {
            return;
        }
        cleanupIfNeeded(storeMap);
        storeMap.put(email, new CodeEntry(code, System.currentTimeMillis() + CODE_TTL.toMillis()));
    }

    private String fetch(String email, String redisPrefix, Map<String, CodeEntry> storeMap) {
        if (redisTemplate != null) {
            String value = redisGet(redisPrefix + email);
            if (value != null) {
                return value;
            }
        }
        CodeEntry entry = storeMap.get(email);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() > entry.expiresAt) {
            storeMap.remove(email);
            return null;
        }
        return entry.code;
    }

    private void remove(String email, String redisPrefix, Map<String, CodeEntry> storeMap) {
        if (redisTemplate != null) {
            try {
                redisTemplate.delete(redisPrefix + email);
            } catch (Exception ignored) {
            }
        }
        storeMap.remove(email);
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

    private void cleanupIfNeeded(Map<String, CodeEntry> storeMap) {
        if (storeMap.size() <= MEMORY_CLEANUP_THRESHOLD) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, CodeEntry>> it = storeMap.entrySet().iterator();
        while (it.hasNext()) {
            if (now > it.next().getValue().expiresAt) {
                it.remove();
            }
        }
    }

    /** Redis 共享失败计数：返回 >=0 为当前次数，-1 表示 Redis 不可用（调用方回落本地） */
    private int redisAttempts(String attemptsKey) {
        if (redisTemplate == null) {
            return -1;
        }
        try {
            String v = redisTemplate.opsForValue().get("auth:email:attempts:" + attemptsKey);
            if (v == null) {
                return 0;
            }
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            log.warn("Redis 读取验证码计数失败，降级本地计数");
            return -1;
        }
    }

    private boolean redisIncrAttempts(String attemptsKey) {
        if (redisTemplate == null) {
            return false;
        }
        try {
            String key = "auth:email:attempts:" + attemptsKey;
            Long v = redisTemplate.opsForValue().increment(key);
            if (v != null && v == 1) {
                redisTemplate.expire(key, CODE_TTL);
            }
            return true;
        } catch (Exception e) {
            log.warn("Redis 递增验证码计数失败，降级本地计数");
            return false;
        }
    }

    private void redisClearAttempts(String attemptsKey) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete("auth:email:attempts:" + attemptsKey);
        } catch (Exception ignored) {
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

    private String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return "***";
        }
        int at = email.indexOf('@');
        String local = email.substring(0, at);
        String domain = email.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.substring(0, 2) + "***" + domain;
    }
}
