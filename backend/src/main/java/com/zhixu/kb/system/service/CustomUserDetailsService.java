package com.zhixu.kb.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.system.entity.SysRole;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.entity.SysUserRole;
import com.zhixu.kb.system.mapper.SysRoleMapper;
import com.zhixu.kb.system.mapper.SysUserMapper;
import com.zhixu.kb.system.mapper.SysUserRoleMapper;
import com.zhixu.kb.system.model.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = userMapper.selectOne(new QueryWrapper<SysUser>().lambda().eq(SysUser::getUsername, username));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        List<Long> roleIds = userRoleMapper.selectList(new QueryWrapper<SysUserRole>().lambda()
                        .eq(SysUserRole::getUserId, user.getId()))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());

        List<String> roleKeys;
        if (roleIds == null || roleIds.isEmpty()) {
            roleKeys = Collections.singletonList("user");
        } else {
            roleKeys = roleMapper.selectBatchIds(roleIds).stream()
                    .filter(Objects::nonNull)
                    .map(SysRole::getRoleKey)
                    .collect(Collectors.toList());
            if (roleKeys.isEmpty()) {
                roleKeys = Collections.singletonList("user");
            }
        }
        return new LoginUser(user, roleKeys);
    }
}
