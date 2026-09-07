package com.zhixu.kb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 应用级参数：接口限流、系统监控告警、管理员引导。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private RateLimit rateLimit = new RateLimit();
    private Monitor monitor = new Monitor();
    private Admin admin = new Admin();
    private ClientIp clientIp = new ClientIp();
    private Auth auth = new Auth();

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimit rateLimit) {
        this.rateLimit = rateLimit;
    }

    public Monitor getMonitor() {
        return monitor;
    }

    public void setMonitor(Monitor monitor) {
        this.monitor = monitor;
    }

    public Admin getAdmin() {
        return admin;
    }

    public void setAdmin(Admin admin) {
        this.admin = admin;
    }

    public ClientIp getClientIp() {
        return clientIp;
    }

    public void setClientIp(ClientIp clientIp) {
        this.clientIp = clientIp;
    }

    public Auth getAuth() {
        return auth;
    }

    public void setAuth(Auth auth) {
        this.auth = auth;
    }

    /**
     * 会话 Cookie 参数：JWT 改存 HttpOnly Cookie，JS 不可读，XSS 无法持久窃取。
     * SameSite=Lax 阻断跨站 POST 携带（防 CSRF）；生产 HTTPS 必须开启 cookieSecure。
     */
    public static class Auth {
        private String cookieName = "ZHIXU_SESSION";
        private Boolean cookieSecure = false;
        private String cookieSameSite = "Lax";

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public Boolean getCookieSecure() {
            return cookieSecure;
        }

        public void setCookieSecure(Boolean cookieSecure) {
            this.cookieSecure = cookieSecure;
        }

        public String getCookieSameSite() {
            return cookieSameSite;
        }

        public void setCookieSameSite(String cookieSameSite) {
            this.cookieSameSite = cookieSameSite;
        }
    }

    /**
     * 可信代理网段：仅当请求来源（remoteAddr）命中这些 IP/CIDR 时才信任
     * X-Forwarded-For / X-Real-IP。默认覆盖回环与 RFC1918 私网/Docker 网桥，
     * 公网直连后端端口时 XFF 一律不信任，防止伪造绕过限流。
     */
    public static class ClientIp {
        private List<String> trustedProxies = new ArrayList<>(Arrays.asList(
                "127.0.0.1", "::1",
                "10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16",
                "fc00::/7"));

        public List<String> getTrustedProxies() {
            return trustedProxies;
        }

        public void setTrustedProxies(List<String> trustedProxies) {
            this.trustedProxies = trustedProxies;
        }
    }

    public static class RateLimit {
        private Integer perMinute = 120;

        public Integer getPerMinute() {
            return perMinute;
        }

        public void setPerMinute(Integer perMinute) {
            this.perMinute = perMinute;
        }
    }

    public static class Monitor {
        private Boolean enabled = true;
        private Integer slowRequestMs = 1000;
        private Integer resourceThresholdPercent = 80;
        private Long resourceCheckIntervalMs = 30000L;
        private Long alertCooldownMs = 300000L;
        private Integer alertRetention = 200;

        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }

        public Integer getSlowRequestMs() {
            return slowRequestMs;
        }

        public void setSlowRequestMs(Integer slowRequestMs) {
            this.slowRequestMs = slowRequestMs;
        }

        public Integer getResourceThresholdPercent() {
            return resourceThresholdPercent;
        }

        public void setResourceThresholdPercent(Integer resourceThresholdPercent) {
            this.resourceThresholdPercent = resourceThresholdPercent;
        }

        public Long getResourceCheckIntervalMs() {
            return resourceCheckIntervalMs;
        }

        public void setResourceCheckIntervalMs(Long resourceCheckIntervalMs) {
            this.resourceCheckIntervalMs = resourceCheckIntervalMs;
        }

        public Long getAlertCooldownMs() {
            return alertCooldownMs;
        }

        public void setAlertCooldownMs(Long alertCooldownMs) {
            this.alertCooldownMs = alertCooldownMs;
        }

        public Integer getAlertRetention() {
            return alertRetention;
        }

        public void setAlertRetention(Integer alertRetention) {
            this.alertRetention = alertRetention;
        }
    }

    public static class Admin {
        private String bootstrapKey = "";
        private Boolean bootstrapFirstAdminOnly = true;

        public String getBootstrapKey() {
            return bootstrapKey;
        }

        public void setBootstrapKey(String bootstrapKey) {
            this.bootstrapKey = bootstrapKey;
        }

        public Boolean getBootstrapFirstAdminOnly() {
            return bootstrapFirstAdminOnly;
        }

        public void setBootstrapFirstAdminOnly(Boolean bootstrapFirstAdminOnly) {
            this.bootstrapFirstAdminOnly = bootstrapFirstAdminOnly;
        }
    }
}
