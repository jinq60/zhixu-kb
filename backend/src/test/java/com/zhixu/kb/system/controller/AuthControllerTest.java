package com.zhixu.kb.system.controller;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.system.model.LoginRequest;
import com.zhixu.kb.system.model.LoginResponse;
import com.zhixu.kb.system.model.UserInfoResponse;
import com.zhixu.kb.system.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    @InjectMocks
    private AuthController authController;

    @Test
    void login_withValidPayload_shouldReturnSuccessResult() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("123456");

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResponse("jwt-token-123"));

        Result<LoginResponse> result = authController.login(request);

        assertEquals(200, result.getCode());
        assertEquals("jwt-token-123", result.getData().getToken());
    }

    @Test
    void login_withWrongCredentials_shouldThrowBusinessException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        when(authService.login(any(LoginRequest.class)))
                .thenThrow(new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误"));

        assertThrows(BusinessException.class, () -> authController.login(request));
    }

    @Test
    void logout_shouldReturnSuccessResult() {
        Result<Void> result = authController.logout(null);
        assertEquals(200, result.getCode());
        assertEquals("已退出", result.getMessage());
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
