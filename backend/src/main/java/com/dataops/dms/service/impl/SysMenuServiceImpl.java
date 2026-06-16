package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.SysMenu;
import com.dataops.dms.mapper.SysMenuMapper;
import com.dataops.dms.service.SysMenuService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SysMenuServiceImpl implements SysMenuService {

    @Resource
    private SysMenuMapper sysMenuMapper;

    @Override
    public Result<List<Map<String, Object>>> getMenuTree() {
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysMenu::getSortOrder);
        List<SysMenu> allMenus = sysMenuMapper.selectList(wrapper);
        List<Map<String, Object>> tree = buildTree(allMenus, null);
        return Result.success(tree);
    }

    @Override
    public Result<List<Map<String, Object>>> getUserMenuTree(String userId) {
        // 查询用户有权限的菜单ID
        List<String> userMenuIds = sysMenuMapper.findUserMenuIds(userId);
        if (userMenuIds.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        
        // 查询这些菜单的完整信息
        List<SysMenu> userMenus = sysMenuMapper.selectBatchIds(userMenuIds);
        // 需要补充父级菜单（确保树结构完整）
        Set<String> allIds = new HashSet<>(userMenuIds);
        for (SysMenu menu : userMenus) {
            String parentId = menu.getParentId();
            while (parentId != null && !parentId.isEmpty() && !allIds.contains(parentId)) {
                allIds.add(parentId);
                SysMenu parent = sysMenuMapper.selectById(parentId);
                if (parent != null) {
                    userMenus.add(parent);
                    parentId = parent.getParentId();
                } else {
                    break;
                }
            }
        }
        
        // 只保留 menu 类型的作为导航菜单（button类型仅用于权限控制，不显示在侧边栏）
        List<SysMenu> menuOnly = userMenus.stream()
                .filter(m -> "menu".equals(m.getType()) && m.getVisible() == 1)
                .sorted(Comparator.comparingInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 0))
                .collect(Collectors.toList());
        
        List<Map<String, Object>> tree = buildTree(menuOnly, null);
        return Result.success(tree);
    }

    @Override
    public Result<SysMenu> getById(String id) {
        return Result.success(sysMenuMapper.selectById(id));
    }

    @Override
    @Transactional
    public Result<SysMenu> create(SysMenu menu) {
        if (menu.getVisible() == null) menu.setVisible(1);
        if (menu.getSortOrder() == null) menu.setSortOrder(0);
        if (menu.getStatus() == null) menu.setStatus("active");
        if (menu.getType() == null) menu.setType("menu");
        sysMenuMapper.insert(menu);
        log.info("创建菜单: {} (type={})", menu.getName(), menu.getType());
        return Result.success("创建成功", menu);
    }

    @Override
    @Transactional
    public Result<SysMenu> update(SysMenu menu) {
        SysMenu existing = sysMenuMapper.selectById(menu.getId());
        if (existing == null) {
            return Result.error("菜单不存在");
        }
        sysMenuMapper.updateById(menu);
        log.info("更新菜单: {}", menu.getName());
        return Result.success("更新成功", menu);
    }

    @Override
    @Transactional
    public Result<Void> delete(String id) {
        // 检查是否有子菜单
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenu::getParentId, id);
        if (sysMenuMapper.selectCount(wrapper) > 0) {
            return Result.error("存在子菜单，请先删除子菜单");
        }
        sysMenuMapper.deleteById(id);
        log.info("删除菜单: {}", id);
        return Result.success("删除成功");
    }

    @Override
    public Result<List<String>> getRoleMenuIds(String roleId) {
        List<String> menuIds = sysMenuMapper.findMenuIdsByRoleId(roleId);
        return Result.success(menuIds);
    }

    @Override
    @Transactional
    public Result<Void> assignRoleMenus(String roleId, List<String> menuIds, String operatorId) {
        // 清除现有菜单关联
        sysMenuMapper.deleteRoleMenus(roleId);
        // 插入新的关联
        if (menuIds != null && !menuIds.isEmpty()) {
            for (String menuId : menuIds) {
                String recordId = UUID.randomUUID().toString().replace("-", "");
                sysMenuMapper.insertRoleMenu(recordId, roleId, menuId);
            }
        }
        log.info("角色[{}]菜单分配完成，共 {} 个菜单项，操作人: {}", roleId, menuIds != null ? menuIds.size() : 0, operatorId);
        return Result.success("菜单权限分配成功");
    }

    /** 构建菜单树 */
    private List<Map<String, Object>> buildTree(List<SysMenu> allMenus, String parentId) {
        List<Map<String, Object>> tree = new ArrayList<>();
        for (SysMenu menu : allMenus) {
            if (Objects.equals(menu.getParentId(), parentId)) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", menu.getId());
                node.put("name", menu.getName());
                node.put("type", menu.getType());
                node.put("path", menu.getPath());
                node.put("component", menu.getComponent());
                node.put("icon", menu.getIcon());
                node.put("permissionCode", menu.getPermissionCode());
                node.put("sortOrder", menu.getSortOrder());
                node.put("visible", menu.getVisible());
                node.put("status", menu.getStatus());
                
                List<Map<String, Object>> children = buildTree(allMenus, menu.getId());
                if (!children.isEmpty()) {
                    node.put("children", children);
                }
                tree.add(node);
            }
        }
        return tree;
    }
}
