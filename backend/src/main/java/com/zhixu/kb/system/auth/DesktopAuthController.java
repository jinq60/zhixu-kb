package com.zhixu.kb.system.auth;

import com.zhixu.kb.common.utils.JwtUtils;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.system.model.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

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

    /** 应用形态探测（公开）：desktop=桌面版 / server=在线服务 */
    @GetMapping("/api/app-config")
    public Result<Map<String, Object>> appConfig() {
        Map<String, Object> data = new HashMap<>();
        data.put("mode", "desktop");
        data.put("appName", "知序知识库 桌面版");
        return Result.success(data);
    }

    /** 为本地单用户签发会话 token（公开端点；该端点仅存在于桌面版 jar 中） */
    @PostMapping("/api/auth/desktop-token")
    public Result<Map<String, Object>> desktopToken() {
        LoginUser loginUser = desktopUserService.getLoginUser();
        String token = jwtUtils.generateToken(loginUser);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        data.put("username", loginUser.getUsername());
        return Result.success(data);
    }
}
