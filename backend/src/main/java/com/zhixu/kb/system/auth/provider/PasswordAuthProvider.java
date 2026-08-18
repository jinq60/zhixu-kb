package com.zhixu.kb.system.auth.provider;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.system.auth.AuthMethod;
import com.zhixu.kb.system.auth.AuthProvider;
import com.zhixu.kb.system.auth.AuthResult;
import com.zhixu.kb.system.auth.UserRegistrationHelper;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * 账号密码认证适配器。
 * 账号不存在时自动注册并登录。
 */
@Component
@RequiredArgsConstructor
public class PasswordAuthProvider implements AuthProvider {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserRegistrationHelper registrationHelper;

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
        SysUser newUser = registrationHelper.createUser(username, null, password,
                AuthMethod.PASSWORD, username);
        return new AuthResult(newUser, true);
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
