package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.Role;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /** 插入角色权限关联 */
    @Insert("INSERT INTO sys_role_permission (id, role_id, permission_id, granted_at) VALUES (#{id}, #{roleId}, #{permissionId}, NOW())")
    int insertRolePermission(@Param("id") String id, @Param("roleId") String roleId, @Param("permissionId") String permissionId);

    /** 删除角色的所有权限关联 */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId}")
    int deleteRolePermissions(@Param("roleId") String roleId);

    /** 删除角色的某个权限 */
    @Delete("DELETE FROM sys_role_permission WHERE role_id = #{roleId} AND permission_id = #{permissionId}")
    int deleteRolePermission(@Param("roleId") String roleId, @Param("permissionId") String permissionId);

    /** 获取角色拥有的权限码列表 */
    @Select("SELECT p.code FROM sys_permission p INNER JOIN sys_role_permission rp ON p.id = rp.permission_id WHERE rp.role_id = #{roleId}")
    List<String> findPermissionCodesByRoleId(@Param("roleId") String roleId);

    /** 获取角色的权限ID列表 */
    @Select("SELECT permission_id FROM sys_role_permission WHERE role_id = #{roleId}")
    List<String> findPermissionIdsByRoleId(@Param("roleId") String roleId);

    /** 获取拥有某个角色的用户ID列表 */
    @Select("SELECT user_id FROM sys_user_role WHERE role_id = #{roleId}")
    List<String> findUserIdsByRoleId(@Param("roleId") String roleId);

    /** 插入用户角色关联 */
    @Insert("INSERT INTO sys_user_role (id, user_id, role_id, granted_by, granted_at) VALUES (#{id}, #{userId}, #{roleId}, #{grantedBy}, NOW())")
    int insertUserRole(@Param("id") String id, @Param("userId") String userId, @Param("roleId") String roleId, @Param("grantedBy") String grantedBy);

    /** 删除用户的所有角色关联 */
    @Delete("DELETE FROM sys_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") String userId);

    /** 获取用户的角色ID列表 */
    @Select("SELECT role_id FROM sys_user_role WHERE user_id = #{userId}")
    List<String> findRoleIdsByUserId(@Param("userId") String userId);
}
