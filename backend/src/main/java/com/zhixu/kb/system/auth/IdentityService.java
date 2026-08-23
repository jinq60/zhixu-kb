package com.zhixu.kb.system.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.entity.SysUserAuth;
import com.zhixu.kb.system.mapper.SysUserAuthMapper;
import com.zhixu.kb.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;

/**
 * 用户身份统一解析：按 provider + account 查找，按 email 关联，否则创建新账号。
 * 保证同一邮箱/身份的多种登录方式最终落到同一个用户。
 */
@Service
@RequiredArgsConstructor
public class IdentityService {

    private static final SecureRandom USERNAME_RANDOM = new SecureRandom();

    private final SysUserMapper userMapper;
    private final SysUserAuthMapper userAuthMapper;
    private final UserRegistrationHelper registrationHelper;

    public SysUser findByProvider(String provider, String account) {
        SysUserAuth auth = userAuthMapper.selectOne(new QueryWrapper<SysUserAuth>().lambda()
                .eq(SysUserAuth::getProvider, provider)
                .eq(SysUserAuth::getAccount, account)
                .eq(SysUserAuth::getIsDeleted, 0));
        if (auth == null || auth.getUserId() == null) {
            return null;
        }
        return userMapper.selectById(auth.getUserId());
    }

    public SysUser findByEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return userMapper.selectOne(new QueryWrapper<SysUser>().lambda()
                .eq(SysUser::getEmail, email));
    }

    /**
     * OAuth 登录解析：优先按 provider+account，再按 email 关联，否则新建。
     */
    @Transactional
    public SysUser resolveOAuthUser(String provider, String account, String email, String nickname) {
        SysUser user = findByProvider(provider, account);
        if (user != null) {
            return user;
        }
        if (StringUtils.hasText(email)) {
            user = findByEmail(email);
            if (user != null) {
                bindAuth(user.getId(), provider, account);
                return user;
            }
        }
        String username = StringUtils.hasText(nickname) ? nickname : provider + "_" + account;
        if (userMapper.selectOne(new QueryWrapper<SysUser>().lambda()
                .eq(SysUser::getUsername, username)) != null) {
            username = username + "_" + randomSuffix();
        }
        return registrationHelper.createUser(username, email, registrationHelper.randomPassword(), provider, account);
    }

    /**
     * 邮箱验证码登录解析：按 email 查找或创建。
     */
    @Transactional
    public SysUser resolveEmailCodeUser(String email) {
        SysUser user = findByEmail(email);
        if (user != null) {
            return user;
        }
        removeStaleEmailAuth(email);
        String username = generateUsernameFromEmail(email);
        return registrationHelper.createUser(username, email, registrationHelper.randomPassword(), AuthMethod.EMAIL_CODE, email);
    }

    /**
     * 短信验证码登录已临时关闭。
     */
    @Transactional
    public SysUser resolveSmsCodeUser(String phone) {
        throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "短信验证码登录暂不可用");
    }

    /**
     * 删除该邮箱在陈旧的 email_code 绑定记录（旧账号换绑后遗留）。
     */
    @Transactional
    public void removeStaleEmailAuth(String email) {
        SysUserAuth stale = userAuthMapper.selectOne(new QueryWrapper<SysUserAuth>().lambda()
                .eq(SysUserAuth::getProvider, AuthMethod.EMAIL_CODE)
                .eq(SysUserAuth::getAccount, email)
                .eq(SysUserAuth::getIsDeleted, 0));
        if (stale != null) {
            userAuthMapper.deleteById(stale.getId());
        }
    }

    /**
     * 绑定新的认证方式到已有用户。
     */
    @Transactional
    public void bindAuth(Long userId, String provider, String account) {
        SysUserAuth existing = userAuthMapper.selectOne(new QueryWrapper<SysUserAuth>().lambda()
                .eq(SysUserAuth::getUserId, userId)
                .eq(SysUserAuth::getProvider, provider)
                .eq(SysUserAuth::getAccount, account)
                .eq(SysUserAuth::getIsDeleted, 0));
        if (existing != null) {
            return;
        }
        SysUserAuth auth = new SysUserAuth();
        auth.setUserId(userId);
        auth.setProvider(provider);
        auth.setAccount(account);
        userAuthMapper.insert(auth);
    }

    @Transactional
    public void syncEmailIfEmpty(Long userId, String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        SysUser user = userMapper.selectById(userId);
        if (user != null && !StringUtils.hasText(user.getEmail())) {
            user.setEmail(email);
            userMapper.updateById(user);
        }
    }

    private String generateUsernameFromEmail(String email) {
        String base = email.split("@")[0];
        if (base.length() > 20) {
            base = base.substring(0, 20);
        }
        return base + "_" + randomSuffix();
    }

    private String randomSuffix() {
        return String.format("%06d", USERNAME_RANDOM.nextInt(1000000));
    }
}
