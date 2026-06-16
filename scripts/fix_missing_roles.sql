-- ==============================================
-- 存量用户角色补齐脚本
-- 将尚未分配任何角色的活跃用户统一分配 developer（开发人员）角色
-- 执行方式：连接数据库后 source 本文件
-- ==============================================

INSERT INTO sys_user_role (id, user_id, role_id, granted_by, granted_at)
SELECT 
    CONCAT('ur_fix_', REPLACE(UUID(), '-', '')) AS id,
    u.id AS user_id,
    'role_developer' AS role_id,
    'system' AS granted_by,
    NOW() AS granted_at
FROM sys_user u
WHERE u.id NOT IN (SELECT DISTINCT user_id FROM sys_user_role)
  AND u.is_active = 1;

-- 显示修复结果
SELECT 
    COUNT(*) AS affected_users,
    '以下用户已分配 developer 角色：' AS message
FROM sys_user u
WHERE u.id NOT IN (SELECT DISTINCT user_id FROM sys_user_role WHERE role_id = 'role_developer')
  AND u.is_active = 1
  AND u.id IN (
      SELECT user_id FROM sys_user_role WHERE role_id = 'role_developer' AND granted_by = 'system'
  );
