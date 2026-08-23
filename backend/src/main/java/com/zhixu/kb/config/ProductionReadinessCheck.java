package com.zhixu.kb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

/**
 * 生产环境启动自检：prod profile 下若仍使用弱默认凭据或缺少关键配置则拒绝启动；
 * 其他环境仅输出警告，不影响本地开发。
 */
@Slf4j
@Component
public class ProductionReadinessCheck {

    private static final List<String> WEAK_VALUES = Arrays.asList(
            "root", "password", "123456", "admin", "neo4j", "zhixu123456", "");

    private final Environment environment;

    public ProductionReadinessCheck(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void check() {
        boolean prod = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        checkCredential(prod, "DB_PASSWORD", "root", "数据库密码");
        checkCredential(prod, "DB_USERNAME", "root", "数据库用户名");
        if (isNeo4jEnabled()) {
            checkCredential(prod, "NEO4J_PASSWORD", "neo4j", "Neo4j 密码");
        }

        checkSecret(prod, "JWT_SECRET", 32, "JWT 密钥");
        checkSecret(prod, "CRYPTO_AES_KEY", 16, "AES 加密密钥");

        String smtpUsername = environment.getProperty("SMTP_USERNAME", "");
        if (!StringUtils.hasText(smtpUsername)) {
            warnOrFail(prod, "SMTP 未配置，邮箱验证码登录将不可用，请配置 SMTP_USERNAME/SMTP_PASSWORD");
        }

        String bootstrapKey = environment.getProperty("ADMIN_BOOTSTRAP_KEY", "");
        if (!StringUtils.hasText(bootstrapKey)) {
            warnOrFail(prod, "ADMIN_BOOTSTRAP_KEY 未配置，无法引导系统首个管理员账号");
        }
    }

    private boolean isNeo4jEnabled() {
        return !"false".equalsIgnoreCase(environment.getProperty("NEO4J_ENABLED", "true"));
    }

    private void checkCredential(boolean prod, String key, String defaultValue, String label) {
        String value = environment.getProperty(key, defaultValue);
        if (value == null || WEAK_VALUES.contains(value.trim().toLowerCase())) {
            warnOrFail(prod, label + " 使用弱默认值，请通过环境变量 " + key + " 设置强凭据");
        }
    }

    private void checkSecret(boolean prod, String key, int minBytes, String label) {
        String value = environment.getProperty(key, "");
        if (!StringUtils.hasText(value)) {
            warnOrFail(prod, label + " 未配置，请通过环境变量 " + key + " 设置");
            return;
        }
        String trimmed = value.trim();
        // 允许 Base64 编码的密钥：4/3 倍长度；兜底按字节数估算
        int effectiveBytes = trimmed.length() >= 64 ? trimmed.length() * 3 / 4 : trimmed.length();
        if (effectiveBytes < minBytes) {
            warnOrFail(prod, label + " 长度不足，建议至少 " + minBytes + " 字节（当前约 " + effectiveBytes + " 字节）");
        }
        if (WEAK_VALUES.contains(trimmed.toLowerCase())) {
            warnOrFail(prod, label + " 使用弱值，请更换为随机生成的强密钥");
        }
    }

    private void warnOrFail(boolean prod, String message) {
        if (prod) {
            throw new IllegalStateException("生产环境安全检查未通过：" + message);
        }
        log.warn("[配置提醒] {}", message);
    }
}
