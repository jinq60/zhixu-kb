package com.zhixu.kb.system.auth;

/**
 * 支持的认证方式常量。
 */
public final class AuthMethod {

    private AuthMethod() {
    }

    /** 账号密码 */
    public static final String PASSWORD = "password";

    /** 邮箱验证码 */
    public static final String EMAIL_CODE = "email_code";

    /** 短信验证码 */
    public static final String SMS_CODE = "sms_code";

    /** Google OAuth */
    public static final String GOOGLE = "google";

    /** GitHub OAuth */
    public static final String GITHUB = "github";

    /** QQ OAuth */
    public static final String QQ = "qq";
}
