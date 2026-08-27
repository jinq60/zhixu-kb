package com.zhixu.kb.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 桌面版 SPA 回退（仅 desktop profile）：
 * 前端 history 路由的路径后端没有对应控制器，显式 forward 到 index.html 交给前端路由。
 * 采用显式路由列举而非通配，避免与 /api/** 产生匹配冲突。
 * 服务器版不受影响（Nginx 承担同一职责）。
 */
@Configuration
@Profile("desktop")
public class DesktopSpaConfig {

    @Controller
    public static class SpaForwardController {

        @GetMapping({
                "/notes", "/notes/**",
                "/categories", "/categories/**",
                "/ask", "/ask/**",
                "/graph", "/graph/**",
                "/settings", "/settings/**",
                "/home",
                "/oauth-callback"
        })
        public String forwardToSpa() {
            return "forward:/index.html";
        }
    }
}
