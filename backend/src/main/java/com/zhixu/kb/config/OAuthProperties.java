package com.zhixu.kb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OAuth / 短信等第三方登录配置。
 */
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    private String frontendCallback = "http://localhost:5173/oauth-callback";

    private Provider github = new Provider();
    private Provider google = new Provider();
    private Provider qq = new Provider();

    public String getFrontendCallback() {
        return frontendCallback;
    }

    public void setFrontendCallback(String frontendCallback) {
        this.frontendCallback = frontendCallback;
    }

    public Provider getGithub() {
        return github;
    }

    public void setGithub(Provider github) {
        this.github = github;
    }

    public Provider getGoogle() {
        return google;
    }

    public void setGoogle(Provider google) {
        this.google = google;
    }

    public Provider getQq() {
        return qq;
    }

    public void setQq(Provider qq) {
        this.qq = qq;
    }

    public static class Provider {
        private boolean enabled = false;
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }
    }
}
