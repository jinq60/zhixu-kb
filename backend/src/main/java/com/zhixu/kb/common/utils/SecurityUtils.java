package com.zhixu.kb.common.utils;

import com.zhixu.kb.system.model.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

public class SecurityUtils {

    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    public static Long getUserId() {
        LoginUser user = getLoginUser();
        return user != null ? user.getUser().getId() : null;
    }

    /**
     * 兼容字符串用户 ID 的调用方（AI 引擎路由等）。
     */
    public static String currentUserId() {
        Long userId = getUserId();
        return userId == null ? null : String.valueOf(userId);
    }

    public static String getUsername() {
        LoginUser user = getLoginUser();
        return user != null ? user.getUsername() : null;
    }

    public static List<String> getRoles() {
        LoginUser user = getLoginUser();
        return user != null && user.getRoles() != null ? user.getRoles() : Collections.emptyList();
    }

    public static boolean isAdmin() {
        return getRoles().stream().anyMatch("admin"::equalsIgnoreCase);
    }
}
