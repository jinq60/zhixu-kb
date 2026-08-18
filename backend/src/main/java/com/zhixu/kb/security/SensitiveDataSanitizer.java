package com.zhixu.kb.security;

import java.util.regex.Pattern;

/**
 * 敏感数据脱敏：日志打印前屏蔽手机号、邮箱、身份证、长 token 等。
 */
public final class SensitiveDataSanitizer {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d{9})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{6})(\\d{8})([0-9Xx]{4})(?!\\d)");
    private static final Pattern LONG_TOKEN_PATTERN = Pattern.compile("(?<![A-Za-z0-9_\\-\\.])[A-Za-z0-9_\\-\\.]{24,}(?![A-Za-z0-9_\\-\\.])");

    private SensitiveDataSanitizer() {
    }

    public static String maskText(String text) {
        if (text == null || text.length() == 0) {
            return text;
        }
        String masked = text;
        masked = EMAIL_PATTERN.matcher(masked).replaceAll("[EMAIL]");
        masked = PHONE_PATTERN.matcher(masked).replaceAll("[PHONE]");
        masked = ID_CARD_PATTERN.matcher(masked).replaceAll("$1********$3");
        masked = LONG_TOKEN_PATTERN.matcher(masked).replaceAll("[TOKEN]");
        return masked;
    }
}
