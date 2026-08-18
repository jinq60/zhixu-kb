package com.zhixu.kb.system.auth.provider;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.system.auth.AuthMethod;
import com.zhixu.kb.system.auth.AuthProvider;
import com.zhixu.kb.system.auth.AuthResult;
import com.zhixu.kb.system.auth.EmailCodeService;
import com.zhixu.kb.system.auth.IdentityService;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * 邮箱验证码认证适配器。
 * 首次登录时自动创建账号并绑定邮箱。
 */
@Component
@RequiredArgsConstructor
public class EmailCodeAuthProvider implements AuthProvider {

    private final SysUserMapper userMapper;
    private final EmailCodeService emailCodeService;
    private final IdentityService identityService;

    @Override
    public String method() {
        return AuthMethod.EMAIL_CODE;
    }

    @Override
    @Transactional
    public AuthResult authenticate(Map<String, Object> params) {
        String email = getString(params, "email");
        String code = getString(params, "code");
        if (email == null || code == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "邮箱或验证码不能为空");
        }
        emailCodeService.verify(email, code);

        SysUser user = identityService.resolveEmailCodeUser(email);
        checkStatus(user);
        return new AuthResult(user, false);
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
