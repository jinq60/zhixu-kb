package com.zhixu.kb.system.controller;

import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.system.entity.DeviceBinding;
import com.zhixu.kb.system.service.DeviceAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 桌面版设备授权端点（官网侧）：
 * bind-code 需官网登录（现有 JWT）；exchange/validate 公开（凭一次性码/设备凭证本身鉴权）。
 */
@RestController
@RequestMapping("/api/device")
@RequiredArgsConstructor
public class DeviceAuthController {

    private final DeviceAuthService deviceAuthService;

    @org.springframework.web.bind.annotation.PostMapping("/bind-code")
    public Result<Map<String, Object>> bindCode() {
        Long userId = SecurityUtils.getUserId();
        return Result.success(deviceAuthService.createBindCode(userId));
    }

    @PostMapping("/exchange")
    public Result<Map<String, Object>> exchange(@RequestBody Map<String, String> body) {
        String bindCode = body == null ? null : body.get("bindCode");
        String deviceName = body == null ? null : body.get("deviceName");
        return Result.success(deviceAuthService.exchange(bindCode, deviceName));
    }

    /** 设备凭证在线校验（Bearer device_token） */
    @GetMapping("/validate")
    public Result<Map<String, Object>> validate(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false)
            String authorization) {
        String token = extractBearer(authorization);
        return Result.success(deviceAuthService.validate(token));
    }

    /** 滑动续期（Bearer device_token） */
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(
            @org.springframework.web.bind.annotation.RequestHeader(value = "Authorization", required = false)
            String authorization) {
        String token = extractBearer(authorization);
        return Result.success(deviceAuthService.refresh(token));
    }

    @GetMapping("/my-devices")
    public Result<List<DeviceBinding>> myDevices() {
        return Result.success(deviceAuthService.myDevices(SecurityUtils.getUserId()));
    }

    @PostMapping("/revoke")
    public Result<Boolean> revoke(@RequestBody Map<String, String> body) {
        deviceAuthService.revoke(SecurityUtils.getUserId(), body == null ? null : body.get("deviceId"));
        return Result.success("设备已吊销", Boolean.TRUE);
    }

    private static String extractBearer(String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization;
    }
}
