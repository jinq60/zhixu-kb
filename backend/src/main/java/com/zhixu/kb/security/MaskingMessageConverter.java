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
    // 长 token：24+ 位字母数字/下划线/短横线（不含点，避免误伤异常堆栈中的类名如 com.zhixu.kb.Foo）；
    // JWT 含点场景由 JWT_PATTERN 单独覆盖
    private static final Pattern LONG_TOKEN_PATTERN =
            Pattern.compile("(?<![A-Za-z0-9_\\-])(?=[A-Za-z0-9_\\-]*[A-Za-z])[A-Za-z0-9_\\-]{24,}(?![A-Za-z0-9_\\-])");
    // JWT 特征：eyJ 开头三段式（载荷恒为 JSON 对象，Base64 后以 eyJ 开头）
    private static final Pattern JWT_PATTERN =
            Pattern.compile("eyJ[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-]+\\.[A-Za-z0-9_\\-\\.]+");

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
        masked = JWT_PATTERN.matcher(masked).replaceAll("[TOKEN]");
        masked = LONG_TOKEN_PATTERN.matcher(masked).replaceAll("[TOKEN]");
        return masked;
    }
}
