package com.zhixu.kb.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixu.kb.system.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 锁定 admin 角色行（SELECT ... FOR UPDATE）。
     * 用于角色变更临界区的数据库层互斥：多实例部署时，所有并发角色变更事务
     * 在该行锁上串行化，保证"至少一名管理员"的 count 校验不被并发降级绕过
     * （单实例由 Service 层 JVM 锁快速路径兜底）。
     *
     * @return admin 角色 id；角色不存在时返回 null（无需保护）
     */
    @Select("SELECT id FROM sys_role WHERE role_key = 'admin' LIMIT 1 FOR UPDATE")
    Long lockAdminRoleRowForChange();
}
