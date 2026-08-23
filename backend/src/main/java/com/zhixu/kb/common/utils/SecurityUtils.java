package com.zhixu.kb.common.utils;

import com.zhixu.kb.system.model.LoginUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;

public class SecurityUtils {

    /**
     * 异步任务线程级用户上下文：异步任务（如文档清洗/向量化）无登录会话，
     * 由任务执行体显式设置归属用户，供 AI 引擎路由用户级配置。
     */
    private static final ThreadLocal<Long> CONTEXT_USER_ID = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        CONTEXT_USER_ID.set(userId);
    }

    public static void clear() {
        CONTEXT_USER_ID.remove();
    }

    public static LoginUser getLoginUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser) {
            return (LoginUser) authentication.getPrincipal();
        }
        return null;
    }

    public static Long getUserId() {
        Long contextUserId = CONTEXT_USER_ID.get();
        if (contextUserId != null) {
            return contextUserId;
        }
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
