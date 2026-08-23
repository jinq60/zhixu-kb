package com.zhixu.kb.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
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
    private final AuditLogService auditLogService;

    /** 角色变更互斥锁：保证"至少一名管理员"的校验与变更原子化，防止并发降级导致零管理员 */
    private final Object roleChangeLock = new Object();

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
        Long currentUserId = SecurityUtils.getUserId();
        if (userId != null && userId.equals(currentUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能修改自己的角色");
        }
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        String normalized = roleKey == null ? "user" : roleKey.trim().toLowerCase();
        if (!"user".equals(normalized) && !"admin".equals(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "角色仅支持 user / admin");
        }
        synchronized (roleChangeLock) {
            if ("user".equals(normalized) && isAdmin(userId) && adminCount() <= 1) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "至少保留一名管理员，无法降级最后一名管理员");
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
        auditLogService.record(SecurityUtils.getUserId(), "ROLE_CHANGE",
                "变更用户角色 userId=" + userId + " → " + normalized, "/api/v1/admin/users/" + userId + "/role");
    }

    private boolean isAdmin(Long userId) {
        SysRole adminRole = findAdminRole();
        if (adminRole == null) {
            return false;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, userId)
                .eq(SysUserRole::getRoleId, adminRole.getId()));
        return count != null && count > 0;
    }

    private long adminCount() {
        SysRole adminRole = findAdminRole();
        if (adminRole == null) {
            return 0;
        }
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getRoleId, adminRole.getId()));
        return count == null ? 0 : count;
    }

    private SysRole findAdminRole() {
        return roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleKey, "admin").last("LIMIT 1"));
    }
}
