package com.dataops.dms.service;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.SysMenu;

import java.util.List;
import java.util.Map;

public interface SysMenuService {
    /** 获取完整菜单树 */
    Result<List<Map<String, Object>>> getMenuTree();
    
    /** 获取用户可见的菜单树 */
    Result<List<Map<String, Object>>> getUserMenuTree(String userId);
    
    /** 获取单个菜单 */
    Result<SysMenu> getById(String id);
    
    /** 创建菜单/按钮 */
    Result<SysMenu> create(SysMenu menu);
    
    /** 更新菜单/按钮 */
    Result<SysMenu> update(SysMenu menu);
    
    /** 删除菜单/按钮 */
    Result<Void> delete(String id);
    
    /** 获取角色已分配的菜单ID */
    Result<List<String>> getRoleMenuIds(String roleId);
    
    /** 为角色分配菜单权限 */
    Result<Void> assignRoleMenus(String roleId, List<String> menuIds, String operatorId);
}
