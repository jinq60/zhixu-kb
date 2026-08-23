package com.zhixu.kb.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.JwtUtils;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.config.AppProperties;
import com.zhixu.kb.security.TokenRevocationStore;
import com.zhixu.kb.system.auth.AuthMethod;
import com.zhixu.kb.system.auth.AuthProviderRegistry;
import com.zhixu.kb.system.auth.AuthResult;
import com.zhixu.kb.system.auth.EmailCodeService;
import com.zhixu.kb.system.auth.IdentityService;
import com.zhixu.kb.system.auth.SmsCodeService;
import com.zhixu.kb.system.entity.SysRole;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.entity.SysUserRole;
import com.zhixu.kb.system.mapper.SysRoleMapper;
import com.zhixu.kb.system.mapper.SysUserMapper;
import com.zhixu.kb.system.mapper.SysUserRoleMapper;
import com.zhixu.kb.system.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 认证服务：基于适配器支持多种登录方式（密码 / 邮箱验证码 / 第三方 OAuth 等）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService userDetailsService;
    private final JwtUtils jwtUtils;
    private final TokenRevocationStore revocationStore;
    private final AppProperties appProperties;
    private final AuthProviderRegistry authProviderRegistry;
    private final EmailCodeService emailCodeService;
    private final SmsCodeService smsCodeService;
    private final IdentityService identityService;
    private final AuditLogService auditLogService;

    @Transactional
    public void register(RegisterRequest request) {
        if (request == null || !StringUtils.hasText(request.getUsername()) || !StringUtils.hasText(request.getPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名或密码不能为空");
        }
        LambdaQueryWrapper<SysUser> wrapper = new QueryWrapper<SysUser>().lambda()
                .eq(SysUser::getUsername, request.getUsername().trim());
        if (StringUtils.hasText(request.getEmail())) {
            wrapper.or().eq(SysUser::getEmail, request.getEmail().trim());
        }
        Long count = userMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名或邮箱已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(request.getUsername().trim());
        user.setEmail(StringUtils.hasText(request.getEmail()) ? request.getEmail().trim() : null);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);
        try {
            userMapper.insert(user);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            // 并发注册撞唯一索引：给出友好提示而非 500
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名或邮箱已存在");
        }

        String roleKey = resolveRegisterRole(request);
        SysRole role = roleMapper.selectOne(new QueryWrapper<SysRole>().lambda().eq(SysRole::getRoleKey, roleKey));
        if (role == null) {
            role = roleMapper.selectOne(new QueryWrapper<SysRole>().lambda().eq(SysRole::getRoleKey, "user"));
        }
        if (role != null) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(role.getId());
            userRoleMapper.insert(ur);
        }
        // 建立 PASSWORD 绑定，保证"修改密码需校验旧密码"对所有注册用户生效
        identityService.bindAuth(user.getId(), AuthMethod.PASSWORD, user.getUsername());
        auditLogService.record(user.getId(), "REGISTER",
                "注册新用户（角色=" + roleKey + "）", "/api/auth/register");
    }

    /**
     * 账号密码登录（兼容旧接口）。
     */
    public LoginResponse login(LoginRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("username", request.getUsername());
        params.put("password", request.getPassword());
        try {
            AuthResult result = authProviderRegistry.get(AuthMethod.PASSWORD).authenticate(params);
            auditLogService.record(result.getUser().getId(), "LOGIN_SUCCESS",
                    "账号密码登录成功", "/api/auth/login");
            return buildLoginResponse(result.getUser());
        } catch (BusinessException e) {
            auditLogService.record(null, "LOGIN_FAIL",
                    "账号密码登录失败（username=" + request.getUsername() + "）", "/api/auth/login");
            throw e;
        }
    }

    /**
     * 统一登录入口，通过适配器路由到具体认证方式。
     */
    public LoginResponse unifiedLogin(UnifiedLoginRequest request) {
        try {
            AuthResult result = authProviderRegistry.get(request.getMethod())
                    .authenticate(request.getParams() == null ? Collections.emptyMap() : request.getParams());
            auditLogService.record(result.getUser().getId(), "LOGIN_SUCCESS",
                    "统一登录成功（method=" + request.getMethod() + "）", "/api/auth/login/unified");
            return buildLoginResponse(result.getUser());
        } catch (BusinessException e) {
            auditLogService.record(null, "LOGIN_FAIL",
                    "统一登录失败（method=" + request.getMethod() + "）", "/api/auth/login/unified");
            throw e;
        }
    }

    /**
     * 发送邮箱验证码。
     */
    public void sendEmailCode(SendEmailCodeRequest request) {
        emailCodeService.send(request.getEmail());
    }

    /**
     * 邮箱验证码登录。
     */
    public LoginResponse emailCodeLogin(EmailCodeLoginRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("email", request.getEmail());
        params.put("code", request.getCode());
        AuthResult result = authProviderRegistry.get(AuthMethod.EMAIL_CODE).authenticate(params);
        return buildLoginResponse(result.getUser());
    }

    public LoginResponse smsCodeLogin(SmsCodeLoginRequest request) {
        Map<String, Object> params = new HashMap<>();
        params.put("phone", request.getPhone());
        params.put("code", request.getCode());
        AuthResult result = authProviderRegistry.get(AuthMethod.SMS_CODE).authenticate(params);
        return buildLoginResponse(result.getUser());
    }

    public void sendSmsCode(SendSmsCodeRequest request) {
        smsCodeService.send(request.getPhone());
    }

    public String generateTokenForUser(SysUser user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        return jwtUtils.generateToken(userDetails);
    }

    private LoginResponse buildLoginResponse(SysUser user) {
        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        return new LoginResponse(jwtUtils.generateToken(userDetails));
    }

    public void logout(String token) {
        Long userId = SecurityUtils.getUserId();
        if (token != null && token.trim().length() > 0) {
            revocationStore.revoke(token);
        }
        auditLogService.record(userId, "LOGOUT", "用户登出", "/api/auth/logout");
    }

    public UserInfoResponse currentUser(UserInfoResponse fallback) {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null && fallback != null) {
            return fallback;
        }
        if (loginUser == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "未登录");
        }
        SysUser user = loginUser.getUser();
        List<String> roles = loginUser.getRoles();
        return new UserInfoResponse(user.getId(), user.getUsername(), user.getEmail(), user.getAvatar(), roles);
    }

    private String resolveRegisterRole(RegisterRequest request) {
        AppProperties.Admin admin = appProperties.getAdmin();
        String bootstrapKey = admin.getBootstrapKey();
        if (!StringUtils.hasText(bootstrapKey)) {
            return "user";
        }
        String provided = request.getAdminBootstrapKey();
        if (provided == null || !constantTimeEquals(bootstrapKey, provided)) {
            return "user";
        }
        if (Boolean.TRUE.equals(admin.getBootstrapFirstAdminOnly()) && existsAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "管理员引导密钥已失效（系统中已存在管理员）");
        }
        return "admin";
    }

    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 是否存在管理员：使用锁定读（FOR UPDATE）作为并发注册的串行化点，
     * 保证 bootstrapFirstAdminOnly=true 时并发注册只有一个能成为管理员。
     * 注意：本方法由 @Transactional 的 register() 调用，FOR UPDATE 锁随外层事务保持。
     */
    private boolean existsAdmin() {
        SysRole adminRole = roleMapper.selectOne(new QueryWrapper<SysRole>().lambda()
                .eq(SysRole::getRoleKey, "admin").last("FOR UPDATE"));
        if (adminRole == null) {
            return false;
        }
        List<SysUserRole> adminRows = userRoleMapper.selectList(new QueryWrapper<SysUserRole>().lambda()
                .eq(SysUserRole::getRoleId, adminRole.getId()).last("LIMIT 1 FOR UPDATE"));
        return adminRows != null && !adminRows.isEmpty();
    }
}
