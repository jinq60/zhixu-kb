package com.zhixu.kb.system.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.entity.SysRole;
import com.zhixu.kb.system.entity.SysUserRole;
import com.zhixu.kb.system.mapper.SysRoleMapper;
import com.zhixu.kb.system.mapper.SysUserAuthMapper;
import com.zhixu.kb.system.mapper.SysUserMapper;
import com.zhixu.kb.system.mapper.SysUserRoleMapper;
import com.zhixu.kb.system.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * 桌面版本地单用户：desktop profile 下整个应用只有这一个身份。
 * 首次启动时初始化（用户+admin/user 双角色），此后所有请求自动以此身份执行。
 */
@Slf4j
@Service
@Profile("desktop")
@RequiredArgsConstructor
public class DesktopUserService {

    public static final String LOCAL_USERNAME = "local";

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserAuthMapper userAuthMapper;
    private final PasswordEncoder passwordEncoder;

    private volatile LoginUser cachedLoginUser;

    /** 首次启动初始化本地用户（幂等） */
    public void ensureLocalUser() {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().lambda()
                .eq(SysUser::getUsername, LOCAL_USERNAME));
        if (user == null) {
            user = new SysUser();
            user.setUsername(LOCAL_USERNAME);
            // 随机密码：桌面版不通过密码登录（自动会话/浏览器验证），密码仅占位
            user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
            user.setStatus(1);
            user.setEmailVerified(1);
            userMapper.insert(user);
            log.info("Desktop local user initialized: id={}", user.getId());
        }
        bindRole(user.getId(), "admin");
        bindRole(user.getId(), "user");
        cachedLoginUser = null;
    }

    private void bindRole(Long userId, String roleKey) {
        SysRole role = roleMapper.selectOne(new QueryWrapper<SysRole>().lambda()
                .eq(SysRole::getRoleKey, roleKey));
        if (role == null) {
            return;
        }
        Long exists = userRoleMapper.selectCount(new QueryWrapper<SysUserRole>().lambda()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, role.getId()));
        if (exists != null && exists > 0) {
            return;
        }
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);
    }

    /** 获取本地用户（带缓存，供自动登录过滤器/签发 desktop token 使用） */
    public LoginUser getLoginUser() {
        LoginUser cached = cachedLoginUser;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (cachedLoginUser != null) {
                return cachedLoginUser;
            }
            ensureLocalUser();
            SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().lambda()
                    .eq(SysUser::getUsername, LOCAL_USERNAME));
            List<String> roles = userRoleMapper.selectList(new QueryWrapper<SysUserRole>().lambda()
                            .eq(SysUserRole::getUserId, user.getId())).stream()
                    .map(ur -> roleMapper.selectById(ur.getRoleId()))
                    .filter(r -> r != null)
                    .map(SysRole::getRoleKey)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            LoginUser loginUser = new LoginUser(user, roles);
            cachedLoginUser = loginUser;
            return loginUser;
        }
    }
}
