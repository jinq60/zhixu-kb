package com.zhixu.kb.system.auth.provider;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.ClientIpResolver;
import com.zhixu.kb.system.auth.AuthMethod;
import com.zhixu.kb.system.auth.AuthProvider;
import com.zhixu.kb.system.auth.AuthResult;
import com.zhixu.kb.system.auth.RegistrationLimiter;
import com.zhixu.kb.system.auth.UserRegistrationHelper;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Objects;

/**
 * 账号密码认证适配器。
 * 账号不存在时自动注册并登录：注册路径校验用户名/密码格式，并按 IP 限制每日注册次数防刷号。
 */
@Component
@RequiredArgsConstructor
public class PasswordAuthProvider implements AuthProvider {

    private static final String USERNAME_PATTERN = "^[\\u4E00-\\u9FA5A-Za-z0-9_\\- ]+$";

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserRegistrationHelper registrationHelper;
    private final RegistrationLimiter registrationLimiter;
    private final ClientIpResolver clientIpResolver;

    @Override
    public String method() {
        return AuthMethod.PASSWORD;
    }

    @Override
    public AuthResult authenticate(Map<String, Object> params) {
        String username = getString(params, "username");
        String password = getString(params, "password");
        if (username == null || password == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名或密码不能为空");
        }
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().lambda()
                .eq(SysUser::getUsername, username));
        if (user != null) {
            checkStatus(user);
            if (!passwordEncoder.matches(password, user.getPassword())) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
            }
            return new AuthResult(user, false);
        }
        // 自动注册
        validateRegistration(username, password);
        registrationLimiter.checkAndIncrement(resolveClientIp());
        SysUser newUser = registrationHelper.createUser(username, null, password,
                AuthMethod.PASSWORD, username);
        return new AuthResult(newUser, true);
    }

    private void validateRegistration(String username, String password) {
        if (username.length() < 2 || username.length() > 24 || !username.matches(USERNAME_PATTERN)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "用户名仅支持中文、字母、数字、下划线、短横线与空格，长度 2-24");
        }
        if (password.length() < 6 || password.length() > 64) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "密码长度需在 6-64 个字符之间");
        }
    }

    private String resolveClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            return clientIpResolver.resolve(((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest());
        }
        return "unknown";
    }

    private void checkStatus(SysUser user) {
        if (Objects.equals(user.getStatus(), 0)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "账户已禁用");
        }
    }

    private String getString(Map<String, Object> params, String key) {
        Object value = params.get(key);
        return value == null ? null : value.toString().trim();
    }
}
