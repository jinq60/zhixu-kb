package com.zhixu.kb.system.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.security.TokenRevocationStore;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.entity.SysUserAuth;
import com.zhixu.kb.system.mapper.SysUserAuthMapper;
import com.zhixu.kb.system.mapper.SysUserMapper;
import com.zhixu.kb.system.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.UUID;

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
    private final TokenRevocationStore tokenRevocationStore;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;

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
     * 安全：按 email 关联仅信任"本地账号邮箱已验证"的账号——注册通道填写的
     * 邮箱未经验证，若直接作为身份锚点，攻击者可抢先用受害者邮箱注册，
     * 待受害者 OAuth 登录时被静默绑进攻击者账号。OAuth 提供方（GitHub/Google）
     * 已确保返回的 email 是 provider 侧验证过的。
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
                if (isEmailVerified(user)) {
                    bindAuth(user.getId(), provider, account);
                    return user;
                }
                // 本地账号邮箱未验证：不自动合并（防止抢注锚点）；
                // 该 OAuth 身份走新账号路径，用户名加后缀避免冲突
                String base = StringUtils.hasText(nickname) ? nickname : provider + "_" + account;
                String username = base;
                while (userMapper.selectOne(new QueryWrapper<SysUser>().lambda()
                        .eq(SysUser::getUsername, username)) != null) {
                    username = base + "_" + randomSuffix();
                }
                SysUser created = registrationHelper.createUser(username, email,
                        registrationHelper.randomPassword(), provider, account);
                // 新建账号的邮箱来自 OAuth 提供方的已验证邮箱，直接标记可信
                markEmailVerified(created);
                return created;
            }
        }
        String username = StringUtils.hasText(nickname) ? nickname : provider + "_" + account;
        if (userMapper.selectOne(new QueryWrapper<SysUser>().lambda()
                .eq(SysUser::getUsername, username)) != null) {
            username = username + "_" + randomSuffix();
        }
        SysUser created = registrationHelper.createUser(username, email, registrationHelper.randomPassword(), provider, account);
        // OAuth 提供方（GitHub/Google）仅返回已验证邮箱，标记为可信锚点
        if (StringUtils.hasText(email)) {
            markEmailVerified(created);
        }
        return created;
    }

    /**
     * 邮箱验证码登录解析：能收到验证码即证明了邮箱所有权。
     * 命中既有账号时同时把该账号标记为"邮箱已验证"——此后该邮箱可作为可信身份锚点。
     * 安全：若命中的账号邮箱此前未经所有权验证且设有密码（注册通道允许填写任意邮箱），
     * 视为邮箱主人接管该账号——吊销全部存量会话并作废旧密码，
     * 防止抢注者凭其已知密码继续登录监控账号内容。被接管者可凭验证码随时登录，
     * 并可在设置页免旧密码重新设置密码。
     */
    @Transactional
    public SysUser resolveEmailCodeUser(String email) {
        SysUser user = findByEmail(email);
        if (user != null) {
            boolean wasVerified = isEmailVerified(user);
            markEmailVerified(user);
            if (!wasVerified) {
                takeOverUnverifiedAccount(user);
            }
            return user;
        }
        removeStaleEmailAuth(email);
        String username = generateUsernameFromEmail(email);
        SysUser created = registrationHelper.createUser(username, email, registrationHelper.randomPassword(), AuthMethod.EMAIL_CODE, email);
        // 验证码登录创建的账号：邮箱所有权已被证明，直接标记可信
        markEmailVerified(created);
        return created;
    }

    /** 邮箱所有权首次被证明：作废该账号旧密码凭证并吊销全部存量会话 */
    private void takeOverUnverifiedAccount(SysUser user) {
        Long userId = user.getId();
        boolean hadPasswordBinding = hasPasswordBinding(userId);
        if (hadPasswordBinding) {
            SysUserAuth binding = userAuthMapper.selectOne(new QueryWrapper<SysUserAuth>().lambda()
                    .eq(SysUserAuth::getUserId, userId)
                    .eq(SysUserAuth::getProvider, AuthMethod.PASSWORD)
                    .eq(SysUserAuth::getIsDeleted, 0));
            if (binding != null) {
                userAuthMapper.deleteById(binding.getId());
            }
            // 令 sys_user.password 哈希失效（绑定已删，改密接口将免验旧密码，用户可自行重设）
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            userMapper.updateById(user);
        }
        tokenRevocationStore.revokeUser(userId);
        auditLogService.record(userId, "EMAIL_IDENTITY_TAKEOVER",
                "验证码登录证明邮箱所有权，接管未验证邮箱账号：重置密码凭证并吊销全部会话", "/api/auth/login");
    }

    private boolean hasPasswordBinding(Long userId) {
        Long count = userAuthMapper.selectCount(new QueryWrapper<SysUserAuth>().lambda()
                .eq(SysUserAuth::getUserId, userId)
                .eq(SysUserAuth::getProvider, AuthMethod.PASSWORD)
                .eq(SysUserAuth::getIsDeleted, 0));
        return count != null && count > 0;
    }

    /** 判断账号邮箱是否已验证（历史数据 null 按 0 处理） */
    public boolean isEmailVerified(SysUser user) {
        return user != null && user.getEmailVerified() != null && user.getEmailVerified() == 1;
    }

    /** 标记邮箱已验证（幂等） */
    @Transactional
    public void markEmailVerified(SysUser user) {
        if (user == null || isEmailVerified(user)) {
            return;
        }
        user.setEmailVerified(1);
        userMapper.updateById(user);
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
