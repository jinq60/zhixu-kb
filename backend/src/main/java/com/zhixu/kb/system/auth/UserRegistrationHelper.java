package com.zhixu.kb.system.auth;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.system.entity.SysRole;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.entity.SysUserAuth;
import com.zhixu.kb.system.entity.SysUserRole;
import com.zhixu.kb.system.mapper.SysRoleMapper;
import com.zhixu.kb.system.mapper.SysUserAuthMapper;
import com.zhixu.kb.system.mapper.SysUserMapper;
import com.zhixu.kb.system.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 统一处理“登录即注册”：创建用户、分配默认角色、绑定认证方式。
 */
@Component
@RequiredArgsConstructor
public class UserRegistrationHelper {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysUserAuthMapper userAuthMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SysUser createUser(String username, String email, String rawPassword,
                              String provider, String account) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setStatus(1);
        userMapper.insert(user);

        SysRole role = roleMapper.selectOne(new QueryWrapper<SysRole>().lambda()
                .eq(SysRole::getRoleKey, "user"));
        if (role != null) {
            SysUserRole ur = new SysUserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(role.getId());
            userRoleMapper.insert(ur);
        }

        SysUserAuth auth = new SysUserAuth();
        auth.setUserId(user.getId());
        auth.setProvider(provider);
        auth.setAccount(account);
        userAuthMapper.insert(auth);

        return user;
    }

    public String randomPassword() {
        return UUID.randomUUID().toString();
    }
}
