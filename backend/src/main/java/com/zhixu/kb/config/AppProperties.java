package com.zhixu.kb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 应用级参数：接口限流、系统监控告警、管理员引导。
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private RateLimit rateLimit = new RateLimit();
    private Monitor monitor = new Monitor();
    private Admin admin = new Admin();

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
