package com.zhixu.kb.system.controller;

import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.system.auth.OAuthService;
import com.zhixu.kb.system.model.*;
import com.zhixu.kb.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthService oAuthService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 显式注册入口：携带 adminBootstrapKey 可注册为管理员（系统首个管理员引导用）。
     */
    @PostMapping("/register")
    public Result<Void> register(@Validated @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success("注册成功", null);
    }

    /**
     * 统一登录入口：通过 method 字段路由到具体适配器。
     */
    @PostMapping("/login/unified")
    public Result<LoginResponse> unifiedLogin(@Validated @RequestBody UnifiedLoginRequest request) {
        return Result.success(authService.unifiedLogin(request));
    }

    @PostMapping("/email-code/send")
    public Result<Void> sendEmailCode(@Validated @RequestBody SendEmailCodeRequest request) {
        authService.sendEmailCode(request);
        return Result.success(null);
    }

    @PostMapping("/email-code/login")
    public Result<LoginResponse> emailCodeLogin(@Validated @RequestBody EmailCodeLoginRequest request) {
        return Result.success(authService.emailCodeLogin(request));
    }

    @PostMapping("/sms-code/send")
    public Result<Void> sendSmsCode(@Validated @RequestBody SendSmsCodeRequest request) {
        authService.sendSmsCode(request);
        return Result.success(null);
    }

    @PostMapping("/sms-code/login")
    public Result<LoginResponse> smsCodeLogin(@Validated @RequestBody SmsCodeLoginRequest request) {
        return Result.success(authService.smsCodeLogin(request));
    }

    @GetMapping("/oauth/{provider}/authorize")
    public void authorize(@PathVariable String provider, HttpServletResponse response) throws IOException {
        String url = oAuthService.authorizeUrl(provider);
        response.sendRedirect(url);
    }

    @GetMapping("/oauth/{provider}/callback")
    public void callback(@PathVariable String provider,
                         @RequestParam String code,
                         @RequestParam(required = false) String state,
                         HttpServletResponse response) throws IOException {
        String redirectUrl = oAuthService.callback(provider, code, state);
        response.sendRedirect(redirectUrl);
    }

    /**
     * OAuth 回调换发：前端拿到回调 URL 中的一次性 code 后，用它换取 JWT。
     */
    @PostMapping("/oauth/exchange")
    public Result<LoginResponse> exchange(@Validated @RequestBody OAuthExchangeRequest request) {
        return Result.success(new LoginResponse(oAuthService.exchangeToken(request.getCode())));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        String token = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        authService.logout(token);
        return Result.success("已退出", null);
    }

    @GetMapping("/info")
    public Result<UserInfoResponse> info() {
        return Result.success(authService.currentUser(null));
    }

    @GetMapping("/me")
    public Result<UserInfoResponse> me() {
        return Result.success(authService.currentUser(null));
    }
}
