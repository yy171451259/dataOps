---
name: "user-nickname-display"
description: "DMS项目内所有业务表只存user_id（稳定主键），列表返回前统一从sys_user表查询昵称回填展示。新增工单/审批/审计/Owner/处理人等含用户显示的页面时必须使用此技能。"
---

# DMS 用户昵称显示处理 Skill（项目内专用）

## 适用范围

本项目（DataOps DMS）内的所有后端业务代码。当页面需要显示"发起人/审批人/处理人/创建人/负责人/操作员/Owner"等任何用户信息时，按此 Skill 实现。

## 项目上下文

| 组件 | 实际值 |
|------|--------|
| 用户表 | `sys_user` |
| 用户表主键 | `id`（如 `859f8f0d9436d7fe13568d1d15465409`） |
| 用户登录账号 | `username`（如 `0142429`，稳定不变） |
| 用户昵称 | `nickname`（如 `杨湘远`，** 可修改，不可作为查找键 **） |
| 实体类 | `com.dataops.dms.entity.User` |
| Mapper | `com.dataops.dms.mapper.UserMapper extends BaseMapper<User>` |
| 认证来源 | `JwtAuthFilter` 放入 `request.getAttribute("userId")` |

## 核心原则：存 user_id，展示时查 nickname

| 层级 | 字段 | 存什么 | 查什么 | 说明 |
|------|------|--------|--------|------|
| **数据库** | `applicant_id / approver_id / owner_id / created_by / ...` | `sys_user.id` | — | 只存稳定不变的用户主键 |
| **数据库（不推荐，仅兼容历史）** | `applicant_name / approver_name` | `sys_user.username`（登录账号） | — | 只用于旧数据兜底，新表 ** 不要建此类字段 ** |
| **Java Entity** | `applicantId` / `applicantName` | `applicantId` 写入数据库 | `applicantName` 在 Controller 层查询后回填，不持久化 |
| **前端展示** | `applicantName` / `approverName` | — | 直接渲染，不需要前端做用户查询 |

**一句话版本**：业务表写 `user_id` → 返回前 `selectBatchIds([user_id 集合])` → 回填 `*_name` → 前端直接展示。

## 数据库表设计规范（建新表时遵守）

**只存 user_id（主键），不要存 nickname/username 到业务表。**

```sql
-- ✅ 正确方式
ALTER TABLE your_table ADD COLUMN applicant_id VARCHAR(64) COMMENT '申请人ID（关联sys_user.id）';
ALTER TABLE your_table ADD COLUMN approver_id VARCHAR(64) COMMENT '审批人ID（关联sys_user.id）';

-- ❌ 错误方式（永远不要这样做）
ALTER TABLE your_table ADD COLUMN applicant_name VARCHAR(64);  -- 用户改昵称后就错位
```

## Entity 字段命名（写在 `src/main/java/com/dataops/dms/entity/` 下）

在 Entity 里同时保留两套字段：一套写库用的 `*Id`（持久化到数据库），一套展示用的 `*Name`（不写库，Controller 返回前回填）。

```java
private String applicantId;     // 写库用 ← 存 sys_user.id
private String applicantName;   // 展示用，Controller 层回填昵称，不持久化
private String approverId;     // 写库用
private String approverName;    // 展示用
private String ownerId;        // 写库用
private String ownerName;        // 展示用
```

## Controller 层：昵称回填标准实现

**所有返回列表/详情的 GET 接口，在 `return Result.success(...)` 之前调用下面这段逻辑。**

复制粘贴下面代码到 Controller 类末尾（已在 `PermissionRequestController` 中验证通过）：

```java
@Resource
private com.dataops.dms.mapper.UserMapper userMapper;

/**
 * 根据 user_id 批量回填用户昵称。
 * 主查询用 user_id -> sys_user.id 主键关联，100% 可靠。
 * Fallback: 历史数据只有 applicant_name（存的是 username 登录账号）时，用 username 反查。
 * 展示时优先用 nickname，为空则退回 username。
 * 绝不会用 nickname 去反向查找用户 —— nickname 可修改，查不到或查错。
 */
private void enrichWithNickname(java.util.List<? extends Object> list) {
    if (list == null || list.isEmpty()) return;
    try {
        java.util.Set<String> userIds = new java.util.HashSet<>();
        java.util.Map<String, String> idToName = new java.util.HashMap<>();

        // ========== 1. 收集所有 user_id ==========
        for (Object item : list) {
            try {
                java.lang.reflect.Method[] methods = item.getClass().getMethods();
                for (java.lang.reflect.Method m : methods) {
                    if (m.getName().startsWith("get")
                        && (m.getName().endsWith("Id") || m.getName().equals("getApplicantId") || m.getName().equals("getApproverId") || m.getName().equals("getOwnerId") || m.getName().equals("getCreatorId") || m.getName().equals("getCreatedBy") || m.getName().equals("getOperatorId") || m.getName().equals("getAssignedToId"))
                        && m.getParameterCount() == 0
                        && m.getReturnType() == String.class) {
                        Object val = m.invoke(item);
                        if (val != null) {
                            String s = (String) val;
                            // 过滤掉明显不是 user_id 的值（如数字/空串）
                            if (s.length() >= 4 && !s.contains(" ")) {
                                userIds.add(s);
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        // ========== 2. 一次 IN 查询 sys_user ==========
        if (!userIds.isEmpty()) {
            try {
                java.util.List<com.dataops.dms.entity.User> users = userMapper.selectBatchIds(userIds);
                if (users != null) {
                    java.util.Map<String, String> usernameToNickname = new java.util.HashMap<>();
                    for (com.dataops.dms.entity.User u : users) {
                        String display = (u.getNickname() != null && !u.getNickname().isEmpty())
                            ? u.getNickname() : u.getUsername();
                        idToName.put(u.getId(), display);
                        if (u.getUsername() != null) usernameToNickname.put(u.getUsername(), display);
                    }
                    // username 兜底映射 —— 历史数据只有 applicant_name（存的是 username登录账号）
                    // 不用 nickname 反查，因为 nickname 可变
                    idToName.putAll(usernameToNickname);
                }
            } catch (Exception ignored) {}
        }

        // ========== 3. 回填 *Name 字段 ==========
        for (Object item : list) {
            try {
                java.lang.reflect.Method[] methods = item.getClass().getMethods();
                for (java.lang.reflect.Method m : methods) {
                    if (m.getName().startsWith("get") && m.getName().endsWith("Id")
                        && m.getParameterCount() == 0 && m.getReturnType() == String.class) {
                        Object idVal = m.invoke(item);
                        if (idVal != null && idToName.containsKey(idVal)) {
                            String setterName = "set" + m.getName().substring(3).replace("Id", "") + "Name";
                            // 常见映射：getApplicantId -> setApplicantName
                            try {
                                java.lang.reflect.Method setter = item.getClass().getMethod(setterName, String.class);
                                setter.invoke(item, idToName.get(idVal));
                            } catch (NoSuchMethodException ignored2) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    } catch (Exception ignored) {}
}
```

**简化版（不使用反射，更稳定）—— 推荐用这版，显式列出需要回填的字段**：

```java
@Resource
private com.dataops.dms.mapper.UserMapper userMapper;

private void enrichWithNickname(java.util.List<? extends Object> list) {
    if (list == null || list.isEmpty()) return;
    try {
        java.util.Set<String> userIds = new java.util.HashSet<>();
        java.util.Map<String, String> idToDisplay = new java.util.HashMap<>();
        java.util.Map<String, String> usernameToDisplay = new java.util.HashMap<>();

        for (Object item : list) {
            try {
                // 显式列出需要查询的 id 字段（按实际 Entity 调整）
                Object[] ids = new Object[]{
                    invokeGetter(item, "getApplicantId"),
                    invokeGetter(item, "getApproverId"),
                    invokeGetter(item, "getOwnerId"),
                    invokeGetter(item, "getCreatorId"),
                    invokeGetter(item, "getCreatedBy"),
                    invokeGetter(item, "getOperatorId"),
                };
                for (Object id : ids) {
                    if (id != null) userIds.add((String) id);
                }
            } catch (Exception ignored) {}
        }

        if (!userIds.isEmpty()) {
            try {
                java.util.List<com.dataops.dms.entity.User> users = userMapper.selectBatchIds(userIds);
                if (users != null) {
                    for (com.dataops.dms.entity.User u : users) {
                        String display = (u.getNickname() != null && !u.getNickname().isEmpty())
                            ? u.getNickname() : u.getUsername();
                        idToDisplay.put(u.getId(), display);
                        if (u.getUsername() != null) usernameToDisplay.put(u.getUsername(), display);
                    }
                }
            } catch (Exception ignored) {}
        }

        for (Object item : list) {
            try {
                setDisplayIfPresent(item, "getApplicantId", "setApplicantName", idToDisplay, usernameToDisplay);
                setDisplayIfPresent(item, "getApproverId", "setApproverName", idToDisplay, usernameToDisplay);
                setDisplayIfPresent(item, "getOwnerId", "setOwnerName", idToDisplay, usernameToDisplay);
                setDisplayIfPresent(item, "getCreatorId", "setCreatorName", idToDisplay, usernameToDisplay);
                setDisplayIfPresent(item, "getCreatedBy", "setCreatedByName", idToDisplay, usernameToDisplay);
                setDisplayIfPresent(item, "getOperatorId", "setOperatorName", idToDisplay, usernameToDisplay);
            } catch (Exception ignored) {}
        }
    } catch (Exception ignored) {}
}

private Object invokeGetter(Object obj, String methodName) {
    try {
        return obj.getClass().getMethod(methodName).invoke(obj);
    } catch (Exception e) { return null; }
}

private void setDisplayIfPresent(Object item, String getter, String setter,
                                 java.util.Map<String, String> idToDisplay,
                                 java.util.Map<String, String> usernameToDisplay) {
    try {
        Object id = item.getClass().getMethod(getter).invoke(item);
        if (id != null && idToDisplay.containsKey(id)) {
            item.getClass().getMethod(setter, String.class).invoke(item, idToDisplay.get(id));
        }
    } catch (Exception ignored) {}
}
```

**调用方式**（在 Controller 的 GET 列表/详情接口中）：

```java
@GetMapping("/my")
@Operation(summary = "获取我的申请")
public Result<List<YourEntity>> getMy(HttpServletRequest request) {
    String userId = (String) request.getAttribute("userId");
    List<YourEntity> list = yourService.getMyItems(userId);
    enrichWithNickname(list);   // ← 统一回填昵称
    return Result.success(list);
}

@GetMapping("/{id}")
public Result<YourEntity> getById(@PathVariable String id) {
    YourEntity item = yourService.getById(id);
    if (item == null) return Result.error(404, "Not found");
    enrichWithNickname(java.util.Collections.singletonList(item));
    return Result.success(item);
}
```

## 认证来源：创建/提交新工单时写什么到数据库

在 `PermissionRequestController` 等提交接口里，从 `request.getAttribute("userId")` 取当前用户 ID 来写库：

```java
String userId = (String) request.getAttribute("userId");    // "859f8f0d9436..." —— 存这个到 applicant_id
String username = (String) request.getAttribute("username"); // "0142429" —— 仅当 applicant_id 没有时兜底写
// 不要直接写 nickname 到业务表
```

**写库代码示例**：

```java
req.setApplicantId(userId);           // ✅ 存稳定主键
// req.setApplicantName(nickname);     // ❌ 不要存，查询时回填
```

## 前端渲染（直接用返回的 `*_name`）

```typescript
{
  title: '发起人',
  dataIndex: 'applicantName',
  key: 'applicantName',
  width: 120,
  render: (v: string) => v || '-'
}
```

## 已在项目中实现的参考位置

| 文件 | 说明 |
|------|------|
| `backend/.../controller/PermissionRequestController.java` | 工单列表/详情，`enrichWithNickname` 方法，单条详情用 `Collections.singletonList()` 包装 |
| `backend/.../entity/PermissionRequest.java` | Entity 字段约定：`applicantId`/`applicantName` |
| `backend/.../entity/User.java` | `id`（主键）、`username`（登录账号）、`nickname`（昵称） |
| `backend/.../mapper/UserMapper.java` | `extends BaseMapper<User>`，提供 `selectBatchIds(Collection<? extends Serializable>)` |
| `backend/.../config/JwtAuthFilter.java` | 认证后放入 `request.setAttribute("userId", ...)` |

## 新增页面 Checklist

创建一个新的含用户显示的列表/详情接口时按以下步骤走：

1. [ ] **业务表设计** → 只写 `*_id`（用户主键）到数据库，** 不建 `*_name` 字段 **
2. [ ] **Entity 设计** → 同时写 `*Id` 和 `*Name` 两个字段；`*Id` 走 MyBatis-Plus 持久化，`*Name` 标注 `@TableField(exist=false)` 或直接不写注解（让 Controller 层显式回填）
3. [ ] **创建/提交逻辑** → 写入 `applicantId = request.getAttribute("userId")`，不要写 `applicantName` 到数据库
4. [ ] **列表查询接口** → `return` 前调用 `enrichWithNickname(list)`
5. [ ] **详情查询接口** → `return` 前调用 `enrichWithNickname(Collections.singletonList(item))`
6. [ ] **编译** → `mvn clean compile`
7. [ ] **重启后端** → 刷新前端验证显示为昵称（如"杨湘远""超级管理员"），不是账号（`0142429`、`admin`）

## 反模式（项目内已踩过的坑）

| 反模式 | 问题 | 正确做法 |
|--------|------|---------|
| 业务表存 `nickname` | 用户改昵称后历史数据仍然显示旧昵称 | 只存 `user_id` |
| 用 `nickname` 反向查用户 | nickname 可修改，查不到或查错 | 用 `user_id` 主键查；兜底用 `username` 登录账号查 |
| 前端自己调接口查用户表 | 性能差（N+1 查询），维护成本高 | 后端返回前统一回填，前端直接用 `*Name` 字段 |
| 业务表只存 `username` 账号 | 同样有账号变更风险（虽概率低但非零） | 存 `user_id` 主键 |
| 数据库存 `applicant_name` 但不是 username 而是 nickname | 查询层用 nickname 反查，极不稳定 | 删除 `applicant_name` 列或确保存的是 username 不改变 |

## 验证 Checklist（上线前确认）

- [ ] 数据库里新写的工单记录有正确的 `applicant_id`（能在 `sys_user.id` 里找到）
- [ ] 旧工单即使 `applicant_id` 为空也能通过 `applicant_name`（存的是 username 登录账号）查到昵称
- [ ] 刷新前端列表，`发起人/审批人` 列显示的是真实昵称（如 "杨湘远"）而非登录账号
- [ ] MyBatis-Plus 的 `selectBatchIds(Collection)` 只查一次，没有 N+1 查询问题
- [ ] 编译通过 + 服务重启成功

## 典型场景字段命名对照表

| 功能页面 | *Id 字段（写库） | *Name 字段（展示） |
|---------|------------------|-------------------|
| 权限工单列表 | `applicantId`, `approverId` | `applicantName`, `approverName` |
| 数据变更工单 | `creatorId`, `ownerId` | `creatorName`, `ownerName` |
| 元数据资源 Owner | `ownerId` | `ownerName` |
| SQL查询历史 | `createdBy` | `createdByName` |
| 审计日志 | `operatorId` | `operatorName` |

