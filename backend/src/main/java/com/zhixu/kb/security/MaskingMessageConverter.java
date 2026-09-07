package com.zhixu.kb.security;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

import java.util.regex.Pattern;

/**
 * logback 消息脱敏转换器：对日志消息统一脱敏（邮箱/手机号/身份证/长 token），
 * 通过 logback-spring.xml 的 conversionRule 接入 %maskedMsg。
 */
public class MaskingMessageConverter extends MessageConverter {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(\\d{8})([0-9Xx]{4})(?!\\d)");
    // 长 token：32+ 位字母数字/下划线/短横线（不含点，避免误伤异常堆栈中的类名）
    private static final Pattern LONG_TOKEN_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9_\\-])(?=[A-Za-z0-9_\\-]*[A-Za-z])[A-Za-z0-9_\\-]{32,}(?![A-Za-z0-9_\\-])");
    // P1 修复：Bearer/Authorization 头与 AK/SK（此前仅脱敏裸长 token，带前缀的同样泄漏）
    private static final Pattern BEARER_PATTERN =
            Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9_\\-\\.~\\+/=]{8,}");
    private static final Pattern AUTHORIZATION_PATTERN =
            Pattern.compile("(?i)Authorization(['\"]?\\s*[:=]\\s*['\"]?)[^'\"\\s,}]{8,}");
    private static final Pattern AK_SK_PATTERN =
            Pattern.compile("(?i)(?:api[_-]?key|secret|sk-|ak-)(['\"]?\\s*[:=]\\s*['\"]?)[A-Za-z0-9_\\-\\.~\\+/=]{8,}");

    @Override
    public String convert(ILoggingEvent event) {
        String message = super.convert(event);
        if (message == null || message.isEmpty()) {
            return message;
        }
        String masked = message;
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("[EMAIL]");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[PHONE]");
        masked = ID_CARD_PATTERN.matcher(masked).replaceAll("$1********$3");
        masked = BEARER_PATTERN.matcher(masked).replaceAll("Bearer [TOKEN]");
        masked = AUTHORIZATION_PATTERN.matcher(masked).replaceAll("Authorization$1[TOKEN]");
        masked = AK_SK_PATTERN.matcher(masked).replaceAll("$1[TOKEN]");
        masked = LONG_TOKEN_PATTERN.matcher(masked).replaceAll("[TOKEN]");
        return masked;
    }
}
