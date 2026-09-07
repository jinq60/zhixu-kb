package com.zhixu.kb.system.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 桌面版激活服务：与官网完成「浏览器验证」握手，
 * 管理 device-token 的持久化、在线校验与离线宽限。
 * <p>
 * 状态存储：{dataDir}/config/device-token.json
 * 激活判定：凭证存在 且 上次在线校验成功时间在离线宽限期内。
 */
@Slf4j
@Service
@org.springframework.context.annotation.Profile("desktop")
public class DesktopActivationService {

    private static final long OFFLINE_GRACE_MS_DEFAULT = 7L * 24 * 3600 * 1000L;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String dataDir;
    private final String portalUrl;
    private final String portalApiBase;
    private final long offlineGraceMs;
    private final String hmacKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RestTemplate restTemplate = createRestTemplate();

    /** 一次验证会话的防伪造状态（浏览器回跳时必须一致） */
    private volatile String pendingState;
    private volatile long pendingStateAt;

    public DesktopActivationService(
            @Value("${app.data-dir}") String dataDir,
            @Value("${app.desktop.portal-url:http://localhost:5173}") String portalUrl,
            @Value("${app.desktop.portal-api-base:http://localhost:5173}") String portalApiBase,
            @Value("${app.desktop.offline-grace-days:7}") long offlineGraceDays,
            @Value("${JWT_SECRET:}") String jwtSecret,
            @Value("${CRYPTO_AES_KEY:}") String aesKey) {
        this.dataDir = dataDir;
        this.portalUrl = portalUrl;
        this.portalApiBase = portalApiBase;
        this.offlineGraceMs = offlineGraceDays > 0
                ? offlineGraceDays * 24 * 3600 * 1000L : OFFLINE_GRACE_MS_DEFAULT;
        // 离线宽限文件 HMAC 密钥：与 JWT 签名密钥做域分离派生（一钥一用），避免弱 secret 连带 JWT 被破；
        // 无可用密钥时回退到 dataDir 派生（仍比明文强，但需尽快配置 JWT_SECRET）
        String raw = (jwtSecret != null && jwtSecret.length() >= 16) ? jwtSecret
                : (aesKey != null && aesKey.length() >= 16 ? aesKey : dataDir);
        this.hmacKey = sha256Hex("zhixu-desktop-file-hmac-v1|" + raw);
    }

    // ---------------- 状态 ----------------

    private File tokenFile() {
        return new File(dataDir, "config/device-token.json");
    }

    public synchronized Map<String, Object> readTokenState() {
        Map<String, Object> state = new HashMap<>();
        File file = tokenFile();
        if (!file.isFile()) {
            return state;
        }
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            JsonNode node = objectMapper.readTree(content);
            if (node.hasNonNull("deviceToken")) {
                state.put("deviceToken", node.get("deviceToken").asText());
            }
            if (node.hasNonNull("deviceId")) {
                state.put("deviceId", node.get("deviceId").asText());
            }
            if (node.hasNonNull("validatedAt")) {
                state.put("validatedAt", node.get("validatedAt").asLong());
            }
            // 校验 HMAC 防篡改：sig 必须存在且校验通过，否则视为未激活（防删 sig 字段绕过）
            if (!node.hasNonNull("sig")) {
                log.warn("Device token file missing sig - treating as not activated, please re-verify");
                return new HashMap<>();
            }
            String sig = node.get("sig").asText();
            String deviceToken = (String) state.get("deviceToken");
            String deviceId = (String) state.get("deviceId");
            long validatedAt = state.get("validatedAt") == null ? 0L : (long) state.get("validatedAt");
            String expected = computeHmac(deviceToken, deviceId, validatedAt);
            if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                    sig.getBytes(StandardCharsets.UTF_8))) {
                log.warn("Device token HMAC mismatch - file may be tampered, treating as not activated");
                return new HashMap<>();
            }
        } catch (Exception ex) {
            log.warn("Read device token failed: {}", ex.getMessage());
        }
        return state;
    }

    private synchronized void writeTokenState(String deviceToken, String deviceId) {
        try {
            File file = tokenFile();
            file.getParentFile().mkdirs();
            long now = System.currentTimeMillis();
            String sig = computeHmac(deviceToken, deviceId, now);
            Map<String, Object> json = new HashMap<>();
            json.put("deviceToken", deviceToken);
            json.put("deviceId", deviceId);
            json.put("validatedAt", now);
            json.put("sig", sig);
            Files.writeString(file.toPath(), objectMapper.writeValueAsString(json), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("保存设备凭证失败: " + ex.getMessage(), ex);
        }
    }

    private static String sha256Hex(String input) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm not available", ex);
        }
    }

    private String computeHmac(String deviceToken, String deviceId, long validatedAt) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            String payload = (deviceToken == null ? "" : deviceToken) + "|" + (deviceId == null ? "" : deviceId) + "|" + validatedAt;
            byte[] h = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(h);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC compute failed", ex);
        }
    }

    public boolean isActivated() {
        Map<String, Object> state = readTokenState();
        if (!state.containsKey("deviceToken")) {
            return false;
        }
        long validatedAt = state.get("validatedAt") == null ? 0L : (long) state.get("validatedAt");
        return System.currentTimeMillis() - validatedAt <= offlineGraceMs;
    }

    public long graceRemainingMs() {
        Map<String, Object> state = readTokenState();
        if (!state.containsKey("deviceToken")) {
            return 0;
        }
        long validatedAt = state.get("validatedAt") == null ? 0L : (long) state.get("validatedAt");
        return Math.max(0, validatedAt + offlineGraceMs - System.currentTimeMillis());
    }

    // ---------------- 浏览器验证握手 ----------------

    /** 发起验证：生成一次性 state 并唤起系统浏览器打开官网验证页 */
    public String startVerify() {
        pendingState = randomHex(32);
        pendingStateAt = System.currentTimeMillis();
        String callback = "http://127.0.0.1:" + getLocalPort() + "/desktop/callback";
        String url = portalUrl + "/verify?state=" + pendingState + "&callback=" + callback;
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
                log.info("Browser opened for desktop verification");
            } else {
                log.info("Desktop browse unsupported, user should open manually: {}", url);
            }
        } catch (Exception ex) {
            log.warn("Open browser failed (use URL manually): {}", ex.getMessage());
        }
        return url;
    }

    /** 官网验证完成后的本地回跳处理 */
    public synchronized Map<String, Object> handleCallback(String bindCode, String state) {
        Map<String, Object> result = new HashMap<>();
        // state 5 分钟过期防重放
        if (pendingState == null || state == null || !pendingState.equals(state)
                || System.currentTimeMillis() - pendingStateAt > 5 * 60 * 1000L) {
            result.put("ok", false);
            result.put("message", "state 校验失败（会话不匹配或已过期），请重新发起验证");
            // 过期后清理，防止无限重试
            if (System.currentTimeMillis() - pendingStateAt > 5 * 60 * 1000L) {
                pendingState = null;
            }
            return result;
        }
        if (bindCode == null || bindCode.isBlank()) {
            result.put("ok", false);
            result.put("message", "缺少绑定码");
            return result;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("bindCode", bindCode);
            body.put("deviceName", "Windows · " + envOr("COMPUTERNAME", "PC"));
            var response = restTemplate.postForEntity(portalApiBase + "/api/device/exchange", body, JsonNode.class);
            JsonNode node = response.getBody();
            if (node == null || !node.hasNonNull("data") || !node.get("data").hasNonNull("deviceToken")) {
                String msg = node == null ? "空响应" : node.path("message").asText("兑换失败");
                result.put("ok", false);
                result.put("message", msg);
                return result;
            }
            String deviceToken = node.get("data").get("deviceToken").asText();
            String deviceId = node.get("data").path("deviceId").asText("");
            writeTokenState(deviceToken, deviceId);
            pendingState = null;
            result.put("ok", true);
            log.info("Desktop activated: deviceId={}", deviceId);
            return result;
        } catch (Exception ex) {
            result.put("ok", false);
            result.put("message", "连接官网失败：" + ex.getMessage());
            return result;
        }
    }

    /** 在线校验（启动时/定时调用）：成功刷新宽限窗口；被吊销则清除本地凭证 */
    public synchronized void validateOnline() {
        Map<String, Object> state = readTokenState();
        String token = (String) state.get("deviceToken");
        if (token == null) {
            return;
        }
        try {
            var headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + token);
            var entity = new org.springframework.http.HttpEntity<>(headers);
            var response = restTemplate.exchange(portalApiBase + "/api/device/validate",
                    org.springframework.http.HttpMethod.GET, entity, JsonNode.class);
            JsonNode node = response.getBody();
            boolean valid = node != null && node.path("data").path("valid").asBoolean(false);
            if (valid) {
                writeTokenState(token, (String) state.get("deviceId"));
                log.info("Device token validated online");
            } else {
                String reason = node == null ? "unknown" : node.path("data").path("reason").asText("unknown");
                log.warn("Device token invalid online ({}), clearing local credential", reason);
                tokenFile().delete();
            }
        } catch (Exception ex) {
            // 官网不可达：保留本地凭证，依赖离线宽限窗口
            log.warn("Online validation unreachable, offline grace continues: {}", ex.getMessage());
        }
    }

    // ---------------- 工具 ----------------

    private static RestTemplate createRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        return new RestTemplate(factory);
    }

    private String envOr(String key, String def) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? def : v;
    }

    private String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append("0123456789abcdef".charAt(RANDOM.nextInt(16)));
        }
        return sb.toString();
    }

    private int getLocalPort() {
        return 18230;
    }
}
