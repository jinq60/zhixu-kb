package com.zhixu.kb.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.system.auth.AuthMethod;
import com.zhixu.kb.system.auth.EmailCodeService;
import com.zhixu.kb.system.auth.IdentityService;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.entity.SysUserAuth;
import com.zhixu.kb.system.mapper.SysUserAuthMapper;
import com.zhixu.kb.system.mapper.SysUserMapper;
import com.zhixu.kb.system.model.BindEmailCodeRequest;
import com.zhixu.kb.system.model.BindEmailRequest;
import com.zhixu.kb.system.model.UpdatePasswordRequest;
import com.zhixu.kb.system.model.UserProfileResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户账号资料与绑定管理。
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final SysUserMapper userMapper;
    private final SysUserAuthMapper userAuthMapper;
    private final PasswordEncoder passwordEncoder;
    private final IdentityService identityService;
    private final EmailCodeService emailCodeService;
    private final AuditLogService auditLogService;

    public UserProfileResponse getProfile(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        List<SysUserAuth> auths = userAuthMapper.selectList(new QueryWrapper<SysUserAuth>().lambda()
                .eq(SysUserAuth::getUserId, userId)
                .eq(SysUserAuth::getIsDeleted, 0));
        List<String> bindings = auths.stream()
                .map(SysUserAuth::getProvider)
                .distinct()
                .collect(Collectors.toList());
        boolean hasPassword = bindings.contains(AuthMethod.PASSWORD);
        return new UserProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatar(),
                null,
                hasPassword,
                bindings
        );
    }

    @Transactional
    public void updatePassword(Long userId, UpdatePasswordRequest request) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户不存在");
        }
        boolean hasPassword = hasPasswordBinding(userId);
        if (hasPassword) {
            if (!StringUtils.hasText(request.getOldPassword())) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "请填写旧密码");
            }
            if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                throw new BusinessException(ResultCode.UNAUTHORIZED, "旧密码错误");
            }
        }
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);

        if (!hasPassword) {
            identityService.bindAuth(userId, AuthMethod.PASSWORD, user.getUsername());
        }
        auditLogService.record(userId, "PASSWORD_CHANGE", "用户修改密码", "/api/user/password");
    }

    private boolean hasPasswordBinding(Long userId) {
        Long count = userAuthMapper.selectCount(new QueryWrapper<SysUserAuth>().lambda()
                .eq(SysUserAuth::getUserId, userId)
                .eq(SysUserAuth::getProvider, AuthMethod.PASSWORD)
                .eq(SysUserAuth::getIsDeleted, 0));
        return count != null && count > 0;
    }

    public void sendBindEmailCode(Long userId, BindEmailCodeRequest request) {
        String email = request.getEmail().trim();
        SysUser existing = userMapper.selectOne(new QueryWrapper<SysUser>().lambda()
                .eq(SysUser::getEmail, email));
        if (existing != null && !existing.getId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该邮箱已被其他账号绑定");
        }
        emailCodeService.sendBindCode(email);
    }

    @Transactional
    public void bindEmail(Long userId, BindEmailRequest request) {
        String email = request.getEmail().trim();
        emailCodeService.verifyBindCode(email, request.getCode());
        SysUser existing = userMapper.selectOne(new QueryWrapper<SysUser>().lambda()
                .eq(SysUser::getEmail, email));
        if (existing != null && !existing.getId().equals(userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该邮箱已被其他账号绑定");
        }
        SysUser user = userMapper.selectById(userId);
        user.setEmail(email);
        userMapper.updateById(user);
        identityService.removeStaleEmailAuth(email);
        identityService.bindAuth(userId, AuthMethod.EMAIL_CODE, email);
        auditLogService.record(userId, "EMAIL_BIND", "绑定邮箱", "/api/user/bind/email");
    }
}
