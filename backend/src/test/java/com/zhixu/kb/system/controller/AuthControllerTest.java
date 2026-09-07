package com.zhixu.kb.system.controller;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.system.auth.AuthCookieService;
import com.zhixu.kb.system.auth.OAuthService;
import com.zhixu.kb.system.model.LoginRequest;
import com.zhixu.kb.system.model.LoginResponse;
import com.zhixu.kb.system.model.UserInfoResponse;
import com.zhixu.kb.system.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @Mock
    private OAuthService oAuthService;

    @Mock
    private AuthCookieService authCookieService;

    @InjectMocks
    private AuthController authController;

    @Test
    void login_withValidPayload_shouldIssueCookieAndReturnUserSummary() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("123456");

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse(1L, "admin", Collections.singletonList("admin")));
        when(authService.generateTokenForUsername("admin")).thenReturn("jwt-token-123");

        MockHttpServletResponse response = new MockHttpServletResponse();
        Result<LoginResponse> result = authController.login(request, response);

        assertEquals(200, result.getCode());
        assertEquals("admin", result.getData().getUsername());
        verify(authCookieService).addSessionCookie(eq(response), eq("jwt-token-123"));
    }

    @Test
    void login_withWrongCredentials_shouldThrowBusinessException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误"));

        assertThrows(BusinessException.class,
                () -> authController.login(request, new MockHttpServletResponse()));
    }

    @Test
    void logout_shouldRevokeResolvedTokenAndClearCookie() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authCookieService.resolveToken(any())).thenReturn("jwt-token-123");

        Result<Void> result = authController.logout(request, response);

        assertEquals(200, result.getCode());
        assertEquals("已退出", result.getMessage());
        verify(authService).logout("jwt-token-123");
        verify(authCookieService).clearSessionCookie(response);
    }

    @Test
    void info_shouldReturnUserInfo() {
        UserInfoResponse userInfo = new UserInfoResponse(
                1L, "admin", "admin@test.com", null, Arrays.asList("ADMIN", "USER"));

        when(authService.currentUser(null)).thenReturn(userInfo);

        Result<UserInfoResponse> result = authController.info();

        assertEquals(200, result.getCode());
        assertEquals("admin", result.getData().getUsername());
        assertEquals(2, result.getData().getRoles().size());
    }
}
