package com.zhixu.kb.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 桌面版密钥自动供给（仅 desktop profile）：
 * 普通用户下载 exe 不应要求配置环境变量——首次启动自动生成
 * JWT_SECRET / CRYPTO_AES_KEY 并持久化到数据目录 config/secrets.properties，
 * 之后每次启动读取复用。用户显式设置过环境变量时尊重用户值（不覆盖）。
 * <p>
 * 通过 META-INF/spring.factories 注册；非 desktop profile 直接跳过，
 * 服务器版行为零变化。
 */
public class DesktopSecretsProvisioner implements EnvironmentPostProcessor, Ordered {

    private static final String JWT_SECRET_KEY = "JWT_SECRET";
    private static final String AES_KEY_KEY = "CRYPTO_AES_KEY";
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public int getOrder() {
        // 必须晚于配置文件加载（才能读到 app.data-dir），早于 Bean 初始化
        return ConfigDataEnvironmentPostProcessor.ORDER + 100;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        boolean desktop = environment.getActiveProfiles().length > 0
                && java.util.Arrays.asList(environment.getActiveProfiles()).contains("desktop");
        if (!desktop) {
            return;
        }
        try {
            String dataDir = environment.getProperty("app.data-dir",
                    System.getProperty("user.home") + "/.zhixukb");
            File configDir = new File(dataDir, "config");
            File secretsFile = new File(configDir, "secrets.properties");

            Properties stored = new Properties();
            if (secretsFile.isFile()) {
                try (InputStream in = new FileInputStream(secretsFile)) {
                    stored.load(in);
                }
            }

            Map<String, Object> provisioned = new HashMap<>();
            if (isBlank(environment.getProperty(JWT_SECRET_KEY))
                    && isBlank(stored.getProperty(JWT_SECRET_KEY))) {
                stored.setProperty(JWT_SECRET_KEY, randomAscii(64));
            }
            if (isBlank(environment.getProperty(AES_KEY_KEY))
                    && isBlank(stored.getProperty(AES_KEY_KEY))) {
                stored.setProperty(AES_KEY_KEY, randomAscii(32));
            }

            if (!isBlank(stored.getProperty(JWT_SECRET_KEY))) {
                provisioned.put(JWT_SECRET_KEY, stored.getProperty(JWT_SECRET_KEY));
            }
            if (!isBlank(stored.getProperty(AES_KEY_KEY))) {
                provisioned.put(AES_KEY_KEY, stored.getProperty(AES_KEY_KEY));
            }

            if (!provisioned.isEmpty()) {
                if (!configDir.exists() && !configDir.mkdirs()) {
                    throw new IOException("无法创建配置目录: " + configDir);
                }
                try (OutputStream out = new FileOutputStream(secretsFile)) {
                    stored.store(out, "ZhixuKB desktop secrets (auto-generated, do not share)");
                }
                MutablePropertySources sources = environment.getPropertySources();
                // addFirst：优先级高于 application.yml 的空默认值；但仅在用户未显式配置时才会进入这里
                sources.addFirst(new MapPropertySource("desktopSecrets", provisioned));
            }
        } catch (Exception ex) {
            // 密钥供给失败不静默吞掉：JwtUtils/CryptoService 会因缺失拒绝启动并给出原因
            System.err.println("[desktop] secrets provision failed: " + ex.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 生成纯 ASCII 随机串（UTF-8 编码下字节数与字符数一致，满足密钥长度校验） */
    private static String randomAscii(int length) {
        final String alphabet = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
