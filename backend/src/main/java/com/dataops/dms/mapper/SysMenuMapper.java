package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.SysMenu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface SysMenuMapper extends BaseMapper<SysMenu> {

    /** 查询角色已分配的菜单ID列表 */
    @Select("SELECT menu_id FROM sys_role_menu WHERE role_id = #{roleId}")
    List<String> findMenuIdsByRoleId(@Param("roleId") String roleId);

    /** 删除角色的所有菜单关联 */
    @Delete("DELETE FROM sys_role_menu WHERE role_id = #{roleId}")
    int deleteRoleMenus(@Param("roleId") String roleId);

    /** 插入角色菜单关联 */
    @Insert("INSERT INTO sys_role_menu (id, role_id, menu_id, created_at) VALUES (#{id}, #{roleId}, #{menuId}, NOW())")
    int insertRoleMenu(@Param("id") String id, @Param("roleId") String roleId, @Param("menuId") String menuId);

    /** 查询用户可见的菜单ID列表（通过角色关联） */
    @Select("SELECT DISTINCT sm.id FROM sys_menu sm " +
            "INNER JOIN sys_role_menu rm ON sm.id = rm.menu_id " +
            "INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id " +
            "WHERE ur.user_id = #{userId} AND sm.status = 'active' AND sm.visible = 1 " +
            "ORDER BY sm.sort_order")
    List<String> findUserMenuIds(@Param("userId") String userId);
}
