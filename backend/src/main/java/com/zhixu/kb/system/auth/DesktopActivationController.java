package com.zhixu.kb.system.auth;

import com.zhixu.kb.common.result.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 桌面版激活端点（仅 desktop profile，仅本机回环可达）：
 * 前端轮询状态、发起浏览器验证、接收官网回跳。
 */
@Slf4j
@RestController
@Profile("desktop")
@RequiredArgsConstructor
public class DesktopActivationController {

    private final DesktopActivationService activationService;

    @GetMapping("/desktop/status")
    public Result<Map<String, Object>> status() {
        Map<String, Object> data = new HashMap<>();
        data.put("activated", activationService.isActivated());
        data.put("graceRemainingMs", activationService.graceRemainingMs());
        return Result.success(data);
    }

    /** 发起浏览器验证：返回官网验证页 URL（后端同时尝试自动唤起浏览器） */
    @PostMapping("/desktop/verify")
    public Result<Map<String, Object>> verify() {
        Map<String, Object> data = new HashMap<>();
        String url = activationService.startVerify();
        data.put("verifyUrl", url);
        return Result.success("已打开浏览器进行验证", data);
    }

    /** 官网验证完成后的本地回跳（浏览器 302 到此） */
    @GetMapping("/desktop/callback")
    public org.springframework.http.ResponseEntity<String> callback(
            @RequestParam(value = "bindCode", required = false) String bindCode,
            @RequestParam(value = "state", required = false) String state) {
        Map<String, Object> result = activationService.handleCallback(bindCode, state);
        boolean ok = Boolean.TRUE.equals(result.get("ok"));
        String message = ok
                ? "验证成功！请返回知序知识库应用，界面将自动进入。"
                : "验证失败：" + result.get("message");
        String html = "<!DOCTYPE html><html lang=\"zh\"><head><meta charset=\"utf-8\">"
                + "<title>知序知识库 - 设备验证</title>"
                + "<style>body{font-family:system-ui;display:flex;align-items:center;justify-content:center;"
                + "height:100vh;margin:0;background:#f5f7fa} .card{background:#fff;padding:40px 48px;border-radius:16px;"
                + "box-shadow:0 4px 24px rgba(0,0,0,.08);text-align:center} h1{font-size:22px;margin:0 0 12px}"
                + "p{color:#666;margin:0}</style></head><body><div class=\"card\"><h1>"
                + (ok ? "✅ " : "❌ ") + message + "</h1><p>本页面可关闭</p></div></body></html>";
        return org.springframework.http.ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.TEXT_HTML)
                .body(html);
    }
}
