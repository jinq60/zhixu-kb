package com.zhixu.kb.common.utils;

import org.springframework.util.StringUtils;

import javax.servlet.http.HttpServletRequest;

/**
 * 客户端 IP 解析：仅在请求来自本机代理时信任 X-Forwarded-For / X-Real-IP，
 * 避免外部请求伪造代理头绕过限流/审计。
 */
public final class ClientIpResolver {

    private ClientIpResolver() {
    }

    public static String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        boolean localProxy = "127.0.0.1".equals(remoteAddr) || "::1".equals(remoteAddr);
        if (localProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                String first = forwarded.split(",")[0].trim();
                if (!first.isEmpty()) {
                    return first;
                }
            }
            String realIp = request.getHeader("X-Real-IP");
            if (StringUtils.hasText(realIp)) {
                return realIp.trim();
            }
        }
        return remoteAddr;
    }
}
