package com.zhixu.kb.system.controller;

import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.system.auth.AuthCookieService;
import com.zhixu.kb.system.auth.OAuthService;
import com.zhixu.kb.system.model.*;
import com.zhixu.kb.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OAuthService oAuthService;
    private final AuthCookieService authCookieService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Validated @RequestBody LoginRequest request,
                                       HttpServletResponse response) {
        LoginResponse body = authService.login(request);
        issueSession(response, body.getUsername());
        return Result.success(body);
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
    public Result<LoginResponse> unifiedLogin(@Validated @RequestBody UnifiedLoginRequest request,
                                              HttpServletResponse response) {
        LoginResponse body = authService.unifiedLogin(request);
        issueSession(response, body.getUsername());
        return Result.success(body);
    }

    @PostMapping("/email-code/send")
    public Result<Void> sendEmailCode(@Validated @RequestBody SendEmailCodeRequest request) {
        authService.sendEmailCode(request);
        return Result.success(null);
    }

    @PostMapping("/email-code/login")
    public Result<LoginResponse> emailCodeLogin(@Validated @RequestBody EmailCodeLoginRequest request,
                                                HttpServletResponse response) {
        LoginResponse body = authService.emailCodeLogin(request);
        issueSession(response, body.getUsername());
        return Result.success(body);
    }

    @PostMapping("/sms-code/send")
    public Result<Void> sendSmsCode(@Validated @RequestBody SendSmsCodeRequest request) {
        authService.sendSmsCode(request);
        return Result.success(null);
    }

    @PostMapping("/sms-code/login")
    public Result<LoginResponse> smsCodeLogin(@Validated @RequestBody SmsCodeLoginRequest request,
                                              HttpServletResponse response) {
        LoginResponse body = authService.smsCodeLogin(request);
        issueSession(response, body.getUsername());
        return Result.success(body);
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
        String redirectUrl = oAuthService.callback(provider, code, state, response);
        response.sendRedirect(redirectUrl);
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        // Cookie → Authorization 头的顺序解析，保证 Cookie 登录态下登出能撤销正确 token
        authService.logout(authCookieService.resolveToken(request));
        authCookieService.clearSessionCookie(response);
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

    /**
     * 会话签发：JWT 只写 HttpOnly Cookie，不进响应体。
     */
    private void issueSession(HttpServletResponse response, String username) {
        authCookieService.addSessionCookie(response, authService.generateTokenForUsername(username));
    }
}
