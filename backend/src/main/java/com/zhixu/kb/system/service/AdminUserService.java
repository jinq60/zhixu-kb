package com.zhixu.kb.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.system.entity.SysRole;
import com.zhixu.kb.system.entity.SysUser;
import com.zhixu.kb.system.entity.SysUserRole;
import com.zhixu.kb.system.mapper.SysRoleMapper;
import com.zhixu.kb.system.mapper.SysUserMapper;
import com.zhixu.kb.system.mapper.SysUserRoleMapper;
import com.zhixu.kb.system.model.AdminUserView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户治理（管理员）：用户列表、角色变更。
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;

    @Transactional(readOnly = true)
    public List<AdminUserView> listUsers() {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId));
        Map<Long, List<Long>> rolesByUser = userRoleMapper.selectList(null).stream()
                .collect(Collectors.groupingBy(SysUserRole::getUserId, Collectors.mapping(SysUserRole::getRoleId, Collectors.toList())));

        Map<Long, String> roleKeyById = roleMapper.selectList(null).stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleKey, (a, b) -> a));

        return users.stream().map(user -> {
            AdminUserView view = new AdminUserView();
            view.setId(user.getId());
            view.setUsername(user.getUsername());
            view.setEmail(user.getEmail());
            view.setStatus(user.getStatus());
            view.setCreateTime(user.getCreateTime());
            List<Long> roleIds = rolesByUser.getOrDefault(user.getId(), java.util.Collections.emptyList());
            List<String> roles = roleIds.stream()
                    .map(roleKeyById::get)
                    .filter(java.util.Objects::nonNull)
                    .collect(Collectors.toList());
            if (roles.isEmpty()) {
                roles = java.util.Collections.singletonList("user");
            }
            view.setRoles(roles);
            return view;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void changeRole(Long userId, String roleKey) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        String normalized = roleKey == null ? "user" : roleKey.trim().toLowerCase();
        if (!"user".equals(normalized) && !"admin".equals(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色仅支持 user / admin");
        }
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleKey, normalized).last("LIMIT 1"));
        if (role == null) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "角色不存在");
        }

        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId));
        SysUserRole ur = new SysUserRole();
        ur.setUserId(userId);
        ur.setRoleId(role.getId());
        userRoleMapper.insert(ur);
    }
}
