-- =============================================
-- DataOps DMS 数据库升级脚本 V9
-- 菜单调整：
--   1. 删除「权限配置」菜单 (menu_permissions)
--   2. 删除「权限管理」分组 (menu_perm_group)
--   3. 「权限申请」升级为一级菜单 (menu_perm_requests)
-- =============================================

USE dataops_dms;

-- =============================================
-- 1. 先删除角色菜单关联中不再需要的菜单
-- =============================================
DELETE FROM `sys_role_menu` WHERE `menu_id` = 'menu_permissions';
DELETE FROM `sys_role_menu` WHERE `menu_id` = 'menu_perm_group';

-- =============================================
-- 2. 删除菜单记录
-- =============================================
DELETE FROM `sys_menu` WHERE `id` = 'menu_permissions';
DELETE FROM `sys_menu` WHERE `id` = 'menu_perm_group';

-- =============================================
-- 3. 将「权限申请」提升为一级菜单
-- =============================================
UPDATE `sys_menu` SET `parent_id` = NULL, `sort_order` = 10 WHERE `id` = 'menu_perm_requests';

-- =============================================
-- 完成
-- =============================================
SELECT 'V9 菜单调整完成！已删除权限配置/权限管理分组，权限申请已升级为一级菜单' AS result;
