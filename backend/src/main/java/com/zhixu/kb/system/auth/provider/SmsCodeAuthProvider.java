package com.zhixu.kb.system.auth.provider;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.system.auth.AuthMethod;
import com.zhixu.kb.system.auth.AuthProvider;
import com.zhixu.kb.system.auth.AuthResult;
import com.zhixu.kb.system.auth.IdentityService;
import com.zhixu.kb.system.auth.SmsCodeService;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

/**
 * 短信验证码认证适配器。
 * 首次登录时自动创建账号并绑定手机号。
 */
@Component
@RequiredArgsConstructor
public class SmsCodeAuthProvider implements AuthProvider {

    private final SysUserMapper userMapper;
    private final SmsCodeService smsCodeService;
    private final IdentityService identityService;

    @Override
    public String method() {
        return AuthMethod.SMS_CODE;
    }

    @Override
    @Transactional
    public AuthResult authenticate(Map<String, Object> params) {
        String phone = getString(params, "phone");
        String code = getString(params, "code");
        if (phone == null || code == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "手机号或验证码不能为空");
        }
        smsCodeService.verify(phone, code);
        SysUser user = identityService.resolveSmsCodeUser(phone);
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
