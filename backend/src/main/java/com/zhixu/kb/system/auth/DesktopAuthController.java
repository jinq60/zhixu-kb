package com.zhixu.kb.system.auth;

import com.zhixu.kb.common.utils.JwtUtils;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.system.model.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 桌面版身份端点（仅 desktop profile 存在，服务器版 404）：
 * 前端启动时探测 /api/app-config；desktop 模式下凭 /api/auth/desktop-token 自动建立会话。
 */
@RestController
@Profile("desktop")
@RequiredArgsConstructor
public class DesktopAuthController {

    private final DesktopUserService desktopUserService;
    private final JwtUtils jwtUtils;
    private final DesktopActivationService activationService;

    /** 应用形态探测（公开）：desktop=桌面版 / server=在线服务；桌面版附带激活状态 */
    @GetMapping("/api/app-config")
    public Result<Map<String, Object>> appConfig() {
        Map<String, Object> data = new HashMap<>();
        data.put("mode", "desktop");
        data.put("appName", "知序知识库 桌面版");
        data.put("activated", activationService.isActivated());
        return Result.success(data);
    }

    /** 应用启动后异步在线校验设备凭证（刷新宽限窗口/感知吊销） */
    @org.springframework.context.event.EventListener(
            org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void validateOnReady() {
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                activationService.validateOnline();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "desktop-validate-online").start();
    }

    /** 为本地单用户签发会话 token（公开端点；该端点仅存在于桌面版 jar 中，且需已完成激活，限本机回环） */
    @PostMapping("/api/auth/desktop-token")
    public Result<Map<String, Object>> desktopToken(HttpServletRequest request) {
        // 直接校验回环地址（覆盖 127/8、::1、::ffff:127.0.0.1 等全部写法），不读 XFF 防伪造
        try {
            if (!java.net.InetAddress.getByName(request.getRemoteAddr()).isLoopbackAddress()) {
                return com.zhixu.kb.common.result.Result.error(403, "仅本机可访问");
            }
        } catch (Exception ex) {
            return com.zhixu.kb.common.result.Result.error(403, "仅本机可访问");
        }
        if (!activationService.isActivated()) {
            return com.zhixu.kb.common.result.Result.error(403, "桌面版尚未完成验证");
        }
        LoginUser loginUser = desktopUserService.getLoginUser();
        String token = jwtUtils.generateToken(loginUser);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", loginUser.getUsername());
        return Result.success(data);
    }
}
