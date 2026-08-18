package com.zhixu.kb.system.controller;

import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.system.model.BindEmailCodeRequest;
import com.zhixu.kb.system.model.BindEmailRequest;
import com.zhixu.kb.system.model.UpdatePasswordRequest;
import com.zhixu.kb.system.model.UserProfileResponse;
import com.zhixu.kb.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public Result<UserProfileResponse> profile() {
        Long userId = SecurityUtils.getUserId();
        return Result.success(userService.getProfile(userId));
    }

    @PostMapping("/password")
    public Result<Void> updatePassword(@Validated @RequestBody UpdatePasswordRequest request) {
        Long userId = SecurityUtils.getUserId();
        userService.updatePassword(userId, request);
        return Result.success(null);
    }

    @PostMapping("/bind/email/send")
    public Result<Void> sendBindEmailCode(@Validated @RequestBody BindEmailCodeRequest request) {
        Long userId = SecurityUtils.getUserId();
        userService.sendBindEmailCode(userId, request);
        return Result.success(null);
    }

    @PostMapping("/bind/email")
    public Result<Void> bindEmail(@Validated @RequestBody BindEmailRequest request) {
        Long userId = SecurityUtils.getUserId();
        userService.bindEmail(userId, request);
        return Result.success(null);
    }
}
