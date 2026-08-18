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

import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 邮箱验证码服务：生成、发送、校验。
 * 优先使用 Redis，未连接 Redis 时降级为内存存储。
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
    private static final Random RANDOM = new Random();

    private final StringRedisTemplate redisTemplate;
    private final JavaMailSender mailSender;
    private final String mailUsername;
    private final Map<String, CodeEntry> memoryStore = new ConcurrentHashMap<>();
    private final Map<String, Long> memorySendTime = new ConcurrentHashMap<>();
    private final Map<String, CodeEntry> bindMemoryStore = new ConcurrentHashMap<>();
    private final Map<String, Long> bindMemorySendTime = new ConcurrentHashMap<>();

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

    public String send(String email) {
        return doSend(email, REDIS_PREFIX, REDIS_SEND_PREFIX, memoryStore, memorySendTime,
                "知序智能知识库 - 登录验证码", "您的验证码是：%s，5 分钟内有效，请勿泄露给任何人。");
    }

    public String sendBindCode(String email) {
        return doSend(email, BIND_REDIS_PREFIX, BIND_REDIS_SEND_PREFIX, bindMemoryStore, bindMemorySendTime,
                "知序智能知识库 - 邮箱绑定验证码", "您的邮箱绑定验证码是：%s，5 分钟内有效，请勿泄露给任何人。");
    }

    public void verify(String email, String code) {
        doVerify(email, code, REDIS_PREFIX, memoryStore);
    }

    public void verifyBindCode(String email, String code) {
        doVerify(email, code, BIND_REDIS_PREFIX, bindMemoryStore);
    }

    private String doSend(String email, String redisPrefix, String redisSendPrefix,
                          Map<String, CodeEntry> storeMap, Map<String, Long> sendMap,
                          String subject, String bodyTemplate) {
        if (email == null || !email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱格式不正确");
        }
        checkResend(email, redisSendPrefix, sendMap);
        String code = generateCode();
        store(email, code, redisPrefix, storeMap);
        sendMap.put(email, System.currentTimeMillis());
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(redisSendPrefix + email, String.valueOf(System.currentTimeMillis()), RESEND_INTERVAL);
        }
        if (mailSender != null && StringUtils.hasText(mailUsername)) {
            sendRealEmail(email, code, subject, bodyTemplate);
        } else {
            log.info("邮箱验证码已生成（SMTP 未配置，仅日志输出）email={} code={}", email, code);
        }
        return code;
    }

    private void doVerify(String email, String code, String redisPrefix, Map<String, CodeEntry> storeMap) {
        if (email == null || code == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱或验证码不能为空");
        }
        String stored = fetch(email, redisPrefix, storeMap);
        if (stored == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码已过期，请重新获取");
        }
        if (!stored.equalsIgnoreCase(code)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "验证码错误");
        }
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
            log.info("邮件已发送 email={} subject={}", email, subject);
        } catch (MailException e) {
            log.error("发送邮件失败 email={}", email, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "邮件发送失败，请检查 SMTP 配置");
        }
    }

    private void checkResend(String email, String redisSendPrefix, Map<String, Long> sendMap) {
        if (redisTemplate != null) {
            Boolean exists = redisTemplate.hasKey(redisSendPrefix + email);
            if (Boolean.TRUE.equals(exists)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "发送过于频繁，请稍后再试");
            }
            return;
        }
        Long last = sendMap.get(email);
        if (last != null && System.currentTimeMillis() - last < RESEND_INTERVAL.toMillis()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "发送过于频繁，请稍后再试");
        }
    }

    private void store(String email, String code, String redisPrefix, Map<String, CodeEntry> storeMap) {
        if (redisTemplate != null) {
            redisTemplate.opsForValue().set(redisPrefix + email, code, CODE_TTL);
            return;
        }
        storeMap.put(email, new CodeEntry(code, System.currentTimeMillis() + CODE_TTL.toMillis()));
    }

    private String fetch(String email, String redisPrefix, Map<String, CodeEntry> storeMap) {
        if (redisTemplate != null) {
            return redisTemplate.opsForValue().get(redisPrefix + email);
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
            redisTemplate.delete(redisPrefix + email);
            return;
        }
        storeMap.remove(email);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }
}
