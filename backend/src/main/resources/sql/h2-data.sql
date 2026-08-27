-- =====================================================================
-- H2 初始数据（桌面版）：角色种子（幂等，靠 role_key 唯一约束 + MERGE 语义）
-- =====================================================================
MERGE INTO sys_role (id, role_name, role_key, status, remark) KEY (role_key)
VALUES (1, '管理员', 'admin', 1, '系统管理员');
MERGE INTO sys_role (id, role_name, role_key, status, remark) KEY (role_key)
VALUES (2, '普通用户', 'user', 1, '普通用户');
