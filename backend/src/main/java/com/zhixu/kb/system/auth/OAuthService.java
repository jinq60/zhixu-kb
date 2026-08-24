package com.zhixu.kb.system.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.config.OAuthProperties;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.service.AuthService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

/**
 * 第三方 OAuth 登录：GitHub / Google / QQ。
 * 统一 authorize 地址生成与 callback 处理，首次登录自动注册。
 * 安全加固：state 防登录 CSRF；回调不再携带 JWT，改为一次性 exchange code 换发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthService {

    private final OAuthProperties oAuthProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final IdentityService identityService;
    private final AuthService authService;
    private final OAuthStateStore oAuthStateStore;

    public String authorizeUrl(String provider) {
        OAuthProperties.Provider config = getConfig(provider);
        if (!config.isEnabled() || !StringUtils.hasText(config.getClientId())) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "该登录方式尚未配置");
        }
        String backendCallback = config.getRedirectUri();
        if (!StringUtils.hasText(backendCallback)) {
            backendCallback = defaultBackendCallback(provider);
        }
        String state = oAuthStateStore.createState();
        switch (provider) {
            case AuthMethod.GITHUB:
                return UriComponentsBuilder.fromHttpUrl("https://github.com/login/oauth/authorize")
                        .queryParam("client_id", config.getClientId())
                        .queryParam("redirect_uri", backendCallback)
                        .queryParam("scope", "user:email")
                        .queryParam("state", state)
                        .build().toUriString();
            case AuthMethod.GOOGLE:
                return UriComponentsBuilder.fromHttpUrl("https://accounts.google.com/o/oauth2/v2/auth")
                        .queryParam("client_id", config.getClientId())
                        .queryParam("redirect_uri", backendCallback)
                        .queryParam("response_type", "code")
                        .queryParam("scope", "openid email profile")
                        .queryParam("state", state)
                        .queryParam("access_type", "offline")
                        .build().toUriString();
            case AuthMethod.QQ:
                return UriComponentsBuilder.fromHttpUrl("https://graph.qq.com/oauth2.0/authorize")
                        .queryParam("response_type", "code")
                        .queryParam("client_id", config.getClientId())
                        .queryParam("redirect_uri", backendCallback)
                        .queryParam("state", state)
                        .queryParam("scope", "get_user_info")
                        .build().toUriString();
            default:
                throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的 OAuth 渠道: " + provider);
        }
    }

    public String callback(String provider, String code, String state) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "授权码为空");
        }
        if (!oAuthStateStore.consumeState(state)) {
            log.warn("OAuth state 校验失败 provider={}", provider);
            throw new BusinessException(ResultCode.UNAUTHORIZED, "OAuth state 校验失败，请重新发起授权");
        }
        OAuthUserInfo userInfo = fetchUserInfo(provider, code);
        SysUser user = findOrCreateUser(provider, userInfo);
        identityService.syncEmailIfEmpty(user.getId(), userInfo.getEmail());
        String token = authService.generateTokenForUser(user);
        String exchangeCode = oAuthStateStore.createToken(token);
        return validateFrontendCallback() + "?code=" + exchangeCode;
    }

    /**
     * 用一次性 exchange code 换取 JWT：code 60 秒有效且只能使用一次。
     */
    public String exchangeToken(String code) {
        String token = oAuthStateStore.takeToken(code);
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "授权码无效或已过期，请重新登录");
        }
        return token;
    }

    private String validateFrontendCallback() {
        String callback = oAuthProperties.getFrontendCallback();
        if (!StringUtils.hasText(callback)) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "前端回调地址未配置");
        }
        try {
            URI uri = new URI(callback);
            boolean validScheme = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!validScheme || !StringUtils.hasText(uri.getHost())) {
                throw new BusinessException(ResultCode.SERVER_ERROR, "前端回调地址配置不合法");
            }
            if (StringUtils.hasText(uri.getQuery()) || StringUtils.hasText(uri.getFragment())) {
                throw new BusinessException(ResultCode.SERVER_ERROR, "前端回调地址不允许携带 query/fragment");
            }
        } catch (URISyntaxException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "前端回调地址配置不合法");
        }
        return callback;
    }

    private OAuthUserInfo fetchUserInfo(String provider, String code) {
        OAuthProperties.Provider config = getConfig(provider);
        String backendCallback = StringUtils.hasText(config.getRedirectUri())
                ? config.getRedirectUri() : defaultBackendCallback(provider);
        switch (provider) {
            case AuthMethod.GITHUB:
                return fetchGithubUser(code, config.getClientId(), config.getClientSecret(), backendCallback);
            case AuthMethod.GOOGLE:
                return fetchGoogleUser(code, config.getClientId(), config.getClientSecret(), backendCallback);
            case AuthMethod.QQ:
                return fetchQqUser(code, config.getClientId(), config.getClientSecret(), backendCallback);
            default:
                throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的 OAuth 渠道: " + provider);
        }
    }

    private OAuthUserInfo fetchGithubUser(String code, String clientId, String clientSecret, String redirectUri) {
        String tokenUrl = "https://github.com/login/oauth/access_token";
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
        JsonNode tokenNode = readJson(response.getBody());
        String accessToken = tokenNode.path("access_token").asText();
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "GitHub 授权失败");
        }

        HttpHeaders userHeaders = new HttpHeaders();
        userHeaders.setBearerAuth(accessToken);
        userHeaders.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        HttpEntity<Void> userRequest = new HttpEntity<>(userHeaders);
        ResponseEntity<String> userResp = restTemplate.exchange(
                "https://api.github.com/user", HttpMethod.GET, userRequest, String.class);
        JsonNode userNode = readJson(userResp.getBody());

        String login = userNode.path("login").asText();
        Long githubId = userNode.path("id").asLong();
        if (githubId == null || githubId <= 0) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "GitHub 授权失败：无法获取用户标识");
        }
        // 安全：/user 的公开 profile email 未经验证，不得用于账号关联；
        // 仅使用 /user/emails 中 primary+verified 的邮箱（与 Google 的 email_verified 对齐），
        // 否则攻击者可把自己的公开邮箱设为受害者邮箱来绑定其账号。
        String email = "";
        try {
            ResponseEntity<String> emailResp = restTemplate.exchange(
                    "https://api.github.com/user/emails", HttpMethod.GET, userRequest, String.class);
            JsonNode emails = readJson(emailResp.getBody());
            if (emails.isArray()) {
                for (JsonNode e : emails) {
                    if (e.path("primary").asBoolean() && e.path("verified").asBoolean()) {
                        email = e.path("email").asText();
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("获取 GitHub 邮箱失败: {}", ex.getMessage());
        }
        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider(AuthMethod.GITHUB);
        info.setAccount(String.valueOf(githubId));
        info.setNickname(login);
        info.setEmail(email);
        return info;
    }

    private OAuthUserInfo fetchGoogleUser(String code, String clientId, String clientSecret, String redirectUri) {
        String tokenUrl = "https://oauth2.googleapis.com/token";
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("code", code);
        body.add("redirect_uri", redirectUri);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body);
        ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
        JsonNode tokenNode = readJson(response.getBody());
        String accessToken = tokenNode.path("access_token").asText();
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Google 授权失败");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        HttpEntity<Void> userRequest = new HttpEntity<>(headers);
        ResponseEntity<String> userResp = restTemplate.exchange(
                "https://openidconnect.googleapis.com/v1/userinfo", HttpMethod.GET, userRequest, String.class);
        JsonNode userNode = readJson(userResp.getBody());

        boolean emailVerified = userNode.path("email_verified").asBoolean(false);
        String sub = userNode.path("sub").asText();
        // 安全：sub 为空说明上游返回异常体（错误响应/格式变更），若放行会让所有人共用
        // account="" 身份记录，等同于账号接管面；与 GitHub 的 githubId 校验对齐
        if (!StringUtils.hasText(sub)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Google 授权失败：无法获取用户标识");
        }
        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider(AuthMethod.GOOGLE);
        info.setAccount(sub);
        info.setEmail(emailVerified ? userNode.path("email").asText() : "");
        info.setNickname(userNode.path("name").asText());
        return info;
    }

    private OAuthUserInfo fetchQqUser(String code, String clientId, String clientSecret, String redirectUri) {
        String tokenUrl = UriComponentsBuilder.fromHttpUrl("https://graph.qq.com/oauth2.0/token")
                .queryParam("grant_type", "authorization_code")
                .queryParam("client_id", clientId)
                .queryParam("client_secret", clientSecret)
                .queryParam("code", code)
                .queryParam("redirect_uri", redirectUri)
                .build().toUriString();
        ResponseEntity<String> tokenResp = restTemplate.getForEntity(tokenUrl, String.class);
        String accessToken = parseQqResponse(tokenResp.getBody()).path("access_token").asText();
        if (!StringUtils.hasText(accessToken)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "QQ 授权失败");
        }

        String openidUrl = UriComponentsBuilder.fromHttpUrl("https://graph.qq.com/oauth2.0/me")
                .queryParam("access_token", accessToken)
                .build().toUriString();
        ResponseEntity<String> openidResp = restTemplate.getForEntity(openidUrl, String.class);
        String openid = parseQqResponse(openidResp.getBody()).path("openid").asText();
        // 安全：openid 为空说明上游返回异常体（错误响应/格式变更），若放行会让所有人共用
        // account="" 身份记录，等同于账号接管面；与 GitHub 的 githubId 校验对齐
        if (!StringUtils.hasText(openid)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "QQ 授权失败：无法获取用户标识");
        }

        String infoUrl = UriComponentsBuilder.fromHttpUrl("https://graph.qq.com/user/get_user_info")
                .queryParam("access_token", accessToken)
                .queryParam("oauth_consumer_key", clientId)
                .queryParam("openid", openid)
                .build().toUriString();
        ResponseEntity<String> infoResp = restTemplate.getForEntity(infoUrl, String.class);
        JsonNode infoNode = readJson(infoResp.getBody());

        OAuthUserInfo info = new OAuthUserInfo();
        info.setProvider(AuthMethod.QQ);
        info.setAccount(openid);
        info.setNickname(infoNode.path("nickname").asText());
        return info;
    }

    private SysUser findOrCreateUser(String provider, OAuthUserInfo info) {
        String nickname = StringUtils.hasText(info.getNickname()) ? info.getNickname() : provider + "_" + info.getAccount();
        return identityService.resolveOAuthUser(provider, info.getAccount(), info.getEmail(), nickname);
    }

    private OAuthProperties.Provider getConfig(String provider) {
        switch (provider) {
            case AuthMethod.GITHUB:
                return oAuthProperties.getGithub();
            case AuthMethod.GOOGLE:
                return oAuthProperties.getGoogle();
            case AuthMethod.QQ:
                return oAuthProperties.getQq();
            default:
                throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的 OAuth 渠道: " + provider);
        }
    }

    private String defaultBackendCallback(String provider) {
        return "http://localhost:8080/api/auth/oauth/" + provider + "/callback";
    }

    private JsonNode readJson(String text) {
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "解析授权响应失败");
        }
    }

    private JsonNode parseQqResponse(String text) {
        // QQ 返回 callback( {...} ); 需要剥离
        if (text == null) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "QQ 授权响应为空");
        }
        String json = text.trim();
        if (json.startsWith("callback(")) {
            json = json.substring("callback(".length());
            if (json.endsWith(");")) {
                json = json.substring(0, json.length() - 2);
            }
            json = json.trim();
        }
        return readJson(json);
    }

    @Data
    public static class OAuthUserInfo {
        private String provider;
        private String account;
        private String email;
        private String nickname;
    }
}
