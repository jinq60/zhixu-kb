package com.zhixu.kb.system.controller;

import com.zhixu.kb.system.auth.OAuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 兼容 Spring Security OAuth2 默认回调路径：/login/oauth2/code/{provider}
 */
@RestController
@RequiredArgsConstructor
public class OAuthCallbackController {

    private final OAuthService oAuthService;

    @GetMapping("/login/oauth2/code/{provider}")
    public void callback(@PathVariable String provider,
                         @RequestParam String code,
                         @RequestParam(required = false) String state,
                         HttpServletResponse response) throws IOException {
        String redirectUrl = oAuthService.callback(provider, code, state, response);
        response.sendRedirect(redirectUrl);
    }
}
