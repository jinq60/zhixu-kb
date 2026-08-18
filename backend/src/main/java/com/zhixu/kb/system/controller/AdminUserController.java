package com.zhixu.kb.system.controller;

import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.system.model.AdminUserView;
import com.zhixu.kb.system.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 用户治理接口（管理员）。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public Result<List<AdminUserView>> users() {
        return Result.success(adminUserService.listUsers());
    }

    @PostMapping("/{userId}/role")
    public Result<Boolean> changeRole(@PathVariable("userId") Long userId,
                                      @RequestBody Map<String, String> body) {
        String roleKey = body == null ? null : body.get("role");
        adminUserService.changeRole(userId, roleKey);
        return Result.success("角色变更成功", Boolean.TRUE);
    }
}
