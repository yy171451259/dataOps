---
name: "user-nickname-display"
description: "Ensures user display names are stored by stable user_id, resolved from user table at query time. Invoke when adding creator/approver/owner columns in ticket/audit/permission pages."
---

# 用户昵称显示处理 Skill

## 核心原则

| 层级 | 存什么 | 查什么 | 说明 |
|------|--------|---------|------|
| **数据库** | `applicant_id / approver_id / created_by / owner_id | —— 存稳定不变的用户主键 |
| **展示层** | 不存 nickname/username | **SELECT nickname FROM sys_user WHERE id = user_id | 实时查用户表取最新昵称 |

## 存储层规范（数据库表结构设计）

业务表中**只存稳定的 user_id（用户主键），永远不要存 nickname。

### 推荐字段命名

```
creator_id       VARCHAR(64)   -- 创建人ID
owner_id          VARCHAR(64)   -- 负责人ID
applicant_id      VARCHAR(64)   -- 申请人ID
approver_id       VARCHAR(64)   -- 审批人ID
assigned_to_id     VARCHAR(64)   -- 处理人ID
```

**错误做法（禁止）**

```
-- ❌ 不要这样写
applicant_name  VARCHAR(64)  -- 存昵称/账号，用户改昵称后就对不上
owner_name      VARCHAR(64)  -- 同上
```

### JWT / 认证层来源映射关系

登录时 JWT 提供哪些字段 → 存储时的对应字段：

```
JWT.sub / request.getAttribute("userId")    → 存表的 *_id 字段
JWT.username (登录账号)                → 展示层用 username 做反向查询（仅当 *_id 为空的历史数据兜底）
User.nickname                            → 只在最终展示时取，**永不在业务表存 nickname
```

**认证过滤器（示例，适用于任何需要当前用户信息的页面）**

在用户表查询后，将用户信息放入 request attribute，后续业务接口统一读取。

```
request.setAttribute("userId",     userId);           // 稳定主键 → 写表用这个
request.setAttribute("username",   username);        // 登录账号 → 仅兜底查询用
request.setAttribute("nickname",  nickname);       // 仅展示使用，** 不写进业务表
```

## 查询层规范（Controller 返回数据时统一处理）

所有 GET 列表接口返回数据时，统一做"昵称回填"。逻辑分 3 层：

**1. 先查所有出现过的 user_id 集合 → 一次 IN 查询 sys_user 表

```java
Set<String> userIds = new HashSet<>();
for (Ticket t : list) {
    if (t.getCreatorId() != null) userIds.add(t.getCreatorId());
    if (t.getOwnerId() != null) userIds.add(t.getOwnerId());
    if (t.getApproverId() != null) userIds.add(t.getApproverId());
}

if (!userIds.isEmpty()) {
    List<User> users = userMapper.selectBatchIds(userIds);
    Map<String, String> idToDisplay = new HashMap<>();
    for (User u : users) {
        String display = (u.getNickname() != null && !u.getNickname().isEmpty())
            ? u.getNickname() : u.getUsername();
        idToDisplay.put(u.getId(), display);
    }
    // 2. 回填到每个对象
    for (Ticket t : list) {
        if (t.getCreatorId() != null) t.setCreatorName(idToDisplay.get(t.getCreatorId()));
        if (t.getOwnerId() != null) t.setOwnerName(idToDisplay.get(t.getOwnerId()));
        if (t.getApproverId() != null) t.setApproverName(idToDisplay.get(t.getApproverId()));
    }
}
```

**2. 历史数据兜底**：如果旧数据没有写入 *_id，仅有 *_name（存的是 username 账号，查询不到时，保持原值。

**3. 关键字段 `nickname` → `username` → `username` 本身就是 nickname，永远用 nickname，就是 nickname；nickname 昵称 nickname。

**4. 最后展示层**：如果 nickname nickname 为空，退到 `username` 账号。

## Entity / DTO 规范

Entity 中同时保留两套字段：一套**存库用的 *_id（稳定主键），一套 *_name（展示用，查询时回填）。

```java
private String creatorId;    // 写库用 ← 核心，不会变
private String creatorName;  // 展示用，查询后回填，不持久化

// getter/setter 正常写，*_id 写入数据库，*_name 由 Controller 查询后填充
```

前端拿到返回的 JSON 中 *_name 字段就是昵称（如 "杨湘远"、"超级管理员"），直接渲染即可，前端不需要做用户表 JOIN。

## 新页面/接口的 Checklist

创建一个新的带用户显示的列表/详情接口时按下列步骤走一遍：

- [ ] 业务表设计 → 只有 `*_id` 字段存用户主键，不要存 `*_name` 到业务表
- [ ] 写操作 → 写入 `*_id`（来自 `request.getAttribute("userId")`
- [ ] 读操作 → 返回前统一走一遍"昵称回填"逻辑（取 nickname 优先，退 username
- [ ] 编译通过 → 重启后端验证效果
- [ ] 前端列表中 `*_name` 直接渲染，不需要前端做用户查询

## 典型场景应用

| 场景 | 字段名建议 |
|------|-----------|
| 工单/工单 | `creator_id/creator_name |
| 权限工单 | `applicant_id/applicant_name |
| 审批流 | `approver_id/approver_name |
| 审计日志 | `operator_id/operator_name |
| 数据资源 Owner | `owner_id/owner_name |
| 数据导入/导出 | `created_by/created_by_name |

## 反模式（踩过的坑）

1. ❌ **存 nickname 到业务表** → 用户改昵称后历史数据显示旧昵称
2. ❌ **用 nickname 反向查用户** → nickname 可变，查不到或查错
3. ❌ **前端自己查用户表做 JOIN** → 重复 N+1 查询，性能差
4. ❌ **业务表只存 username** → 同样有用户改账号的风险（虽然概率低但不是零）
5. ❌ **前端做用户 ID→昵称映射写在前端** → 维护成本高，多个页面都要重复实现

## 标准实现骨架（可直接复制粘贴）

**后端 Controller 层（Java/MyBatis-Plus）：

```java
// 在所有 GET 列表接口的最后，return 前调用
private void enrichWithNickname(List<YourEntity> list) {
    if (list == null || list.isEmpty()) return;
    try {
        java.util.Set<String> userIds = new java.util.HashSet<>();
        for (YourEntity item : list) {
            // 收集所有出现过的 user_id（如 creatorId, ownerId, approverId）
            if (item.getCreatorId() != null) userIds.add(item.getCreatorId());
            if (item.getOwnerId() != null) userIds.add(item.getOwnerId());
            if (item.getApproverId() != null) userIds.add(item.getApproverId());
        }

        if (userIds.isEmpty()) return;

        java.util.Map<String, String> idToDisplay = new java.util.HashMap<>();
        try {
            List<User> users = userMapper.selectBatchIds(userIds);
            if (users != null) {
                for (User u : users) {
                    String display = (u.getNickname() != null && !u.getNickname().isEmpty())
                        ? u.getNickname() : u.getUsername();
                    idToDisplay.put(u.getId(), display);
                }
            }
        } catch (Exception ignored) {}

        for (YourEntity item : list) {
            if (item.getCreatorId() != null)
                item.setCreatorName(idToDisplay.get(item.getCreatorId()));
            if (item.getOwnerId() != null)
                item.setOwnerName(idToDisplay.get(item.getOwnerId()));
            if (item.getApproverId() != null)
                item.setApproverName(idToDisplay.get(item.getApproverId()));
        }
    } catch (Exception ignored) {}
}
```

**前端 React/TypeScript 渲染层**

```typescript
// 直接用返回来的 *_name 字段渲染
{record.applicantName || '-'}
{record.approverName || '待审批'}
```

## 关键数据正确性检查

- 编译通过 → 重启后端
- 刷新前端列表验证"发起人""当前处理人"等列显示的是昵称（如"杨湘远""超级管理员"），而不是账号（0142429"
- 历史数据（没有 *_id 但有 *_name 存 username）也能正确通过 username 反查到昵称

