package com.zhixu.kb.system.service;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.JwtUtils;
import com.zhixu.kb.config.AppProperties;
import com.zhixu.kb.security.TokenRevocationStore;
import com.zhixu.kb.system.auth.AuthMethod;
import com.zhixu.kb.system.auth.AuthProvider;
import com.zhixu.kb.system.auth.AuthProviderRegistry;
import com.zhixu.kb.system.auth.AuthResult;
import com.zhixu.kb.system.auth.EmailCodeService;
import com.zhixu.kb.system.entity.SysRole;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.entity.SysUserRole;
import com.zhixu.kb.system.mapper.SysRoleMapper;
import com.zhixu.kb.system.mapper.SysUserMapper;
import com.zhixu.kb.system.mapper.SysUserRoleMapper;
import com.zhixu.kb.system.model.LoginRequest;
import com.zhixu.kb.system.model.LoginResponse;
import com.zhixu.kb.system.model.LoginUser;
import com.zhixu.kb.system.model.RegisterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private CustomUserDetailsService userDetailsService;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private TokenRevocationStore revocationStore;
    @Mock
    private AppProperties appProperties;
    @Mock
    private AuthProviderRegistry authProviderRegistry;
    @Mock
    private AuthProvider passwordAuthProvider;
    @Mock
    private EmailCodeService emailCodeService;

    @InjectMocks
    private AuthService authService;

    private SysUser existingUser;

    @BeforeEach
    void setUp() {
        existingUser = new SysUser();
        existingUser.setId(1L);
        existingUser.setUsername("admin");
        existingUser.setPassword("$2a$10$encodedPassword");
        existingUser.setEmail("admin@test.com");
        existingUser.setStatus(1);

        AppProperties.Admin admin = new AppProperties.Admin();
        admin.setBootstrapKey("");
        lenient().when(appProperties.getAdmin()).thenReturn(admin);
        lenient().when(authProviderRegistry.get(AuthMethod.PASSWORD)).thenReturn(passwordAuthProvider);
    }

    @Test
    void login_withValidCredentials_shouldReturnToken() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("123456");

        when(passwordAuthProvider.authenticate(anyMap())).thenReturn(new AuthResult(existingUser, false));

        LoginUser loginUser = new LoginUser(existingUser, Collections.singletonList("USER"));
        when(userDetailsService.loadUserByUsername("admin")).thenReturn(loginUser);
        when(jwtUtils.generateToken(any(UserDetails.class))).thenReturn("jwt-token-123");

        LoginResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("jwt-token-123", response.getToken());
        verify(jwtUtils).generateToken(any(UserDetails.class));
    }

    @Test
    void login_withWrongPassword_shouldThrowException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrongpassword");

        when(passwordAuthProvider.authenticate(anyMap())).thenThrow(new BusinessException(ResultCode.UNAUTHORIZED, "密码错误"));

        assertThrows(BusinessException.class, () -> authService.login(request));
    }

    @Test
    void login_withNonExistentUser_shouldThrowException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("nonexistent");
        request.setPassword("123456");

        when(passwordAuthProvider.authenticate(anyMap())).thenThrow(new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在"));

        assertThrows(BusinessException.class, () -> authService.login(request));
    }

    @Test
    void login_withDisabledAccount_shouldThrowException() {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("123456");

        existingUser.setStatus(0); // disabled
        when(passwordAuthProvider.authenticate(anyMap())).thenThrow(new BusinessException(ResultCode.FORBIDDEN, "账户已禁用"));

        assertThrows(BusinessException.class, () -> authService.login(request));
    }

    @Test
    void register_withNewUser_shouldSucceed() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("newuser");
        request.setPassword("password123");
        request.setEmail("new@test.com");

        doReturn(0L).when(userMapper).selectCount(any());
        when(passwordEncoder.encode("password123")).thenReturn("$2a$10$encoded");
        when(userMapper.insert(any(SysUser.class))).thenReturn(1);

        SysRole userRole = new SysRole();
        userRole.setId(2L);
        userRole.setRoleKey("user");
        doReturn(userRole).when(roleMapper).selectOne(any());
        when(userRoleMapper.insert(any(SysUserRole.class))).thenReturn(1);

        assertDoesNotThrow(() -> authService.register(request));

        verify(userMapper).insert(any(SysUser.class));
        verify(userRoleMapper).insert(any(SysUserRole.class));
    }

    @Test
    void register_withExistingUsername_shouldThrowException() {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("admin");
        request.setPassword("password123");
        request.setEmail("another@test.com");

        doReturn(1L).when(userMapper).selectCount(any());

        assertThrows(BusinessException.class, () -> authService.register(request));
        verify(userMapper, never()).insert(any(SysUser.class));
    }
}
