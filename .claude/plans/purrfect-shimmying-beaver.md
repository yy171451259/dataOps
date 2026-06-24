# 将 frontend-vue SqlEditor 顶部工具栏改为左侧垂直工具栏

## Context

用户指出 `frontend-vue`（Vue 3）的 SQL 查询页面与 `frontend`（React 版本）布局差异大。`frontend` 使用左侧垂直工具栏（执行/格式化/解释/复制），而 `frontend-vue` 使用顶部水平工具栏（执行-文字标签/解释/审核）。用户要求统一为左侧垂直工具栏风格。

## Changes

### 文件修改

**`frontend-vue/src/components/SqlEditor.vue`** — 重构工具栏布局

### 具体改动

#### 1. Template 改造：顶部水平工具栏 → 左侧垂直工具栏

**去掉**（`<template>` 中）：
- 原第 12-26 行的顶部水平工具栏 `<div class="dbeaver-toolbar">`（含 DB 切换、Tab 选择器、实例/Schema 下拉框、执行/解释/审核按钮）

**新增**三段式布局（左右结构）：

**左侧垂直工具栏**（40px 宽，竖排，与 React 版一致）：
```
┌───────┐
│  ▶    │  ← 执行 (Ctrl+Enter)，绿色 PlayCircleOutlined 图标
│  ⚡   │  ← 解释 / 执行计划 (Explain)，ExperimentOutlined 图标
│  🖌   │  ← 格式化 (Format)，FormatPainterOutlined 图标
│ ───── │  ← Divider
│  📋   │  ← 复制 SQL，CopyOutlined 图标
└───────┘
```

**顶部信息栏**（在编辑器上方，紧接在对象浏览器之后）：
- 左侧：Tab 选择器 + 新增/关闭 Tab 按钮（基本保持现有逻辑）
- 右侧：实例下拉框 | Schema 下拉框 | 审核按钮（SafetyOutlined）

#### 2. 新增按钮功能

| 按钮 | 功能实现 |
|------|---------|
| **格式化** | 复用 React 版的格式化逻辑：`replace(/\s+/g, ' ')` → `replace(keyword, '\n$1')` → uppercase keywords。替换 editor 中的当前 SQL |
| **复制** | `navigator.clipboard.writeText(activeTabData.sql)` → message.success('OK') |
| **执行** | 从 `CaretRightOutlined` + 文字 "执行" 改为 `PlayCircleOutlined` 绿色图标 + tooltip "执行 (Ctrl+Enter)" |

#### 3. Script 部分更新

- **导入图标**：添加 `PlayCircleOutlined`, `ExperimentOutlined`, `FormatPainterOutlined`, `CopyOutlined`；移除不必要的 `CaretRightOutlined`
- **添加 `formatSql()` 函数**：将 React 版 lines 1216-1228 的格式化逻辑移植过来
- **添加 `copySql()` 函数**：`navigator.clipboard.writeText(...)`
- `handleExport()`、`executeSql()`、`handleExplain()`、`handleAudit()` 保持现有实现不变

#### 4. Style 补充

- `.vertical-toolbar` 样式：40px 宽，`#f9f9f9` 背景，`borderRight`，flex column，居中，gap 4px
- 按钮：`type="text" size="small"`，padding 4px 8px

#### 5. 不修改的部分

- 对象浏览器 (ObjectBrowser) — 保持 280px 左侧面板，保留 toggle 功能
- Monaco Editor 配置 — 保留现有 `editorOptions`
- 结果区 — 保持 grid/text/record 视图、导出功能
- 底部保存面板 (savedPanelTab) — 不变
- Explain/Audit Drawer — 不变
- Tab 管理逻辑 — 保持不变
- 实例/Schema 加载逻辑 — 保持不变

## Verification

1. 启动 `frontend-vue` 前端：`cd frontend-vue && npm run dev`
2. 打开 SQL 查询页面 (`/sql`)
3. 确认左侧垂直工具栏显示四个按钮：执行(绿色) + 解释 + 格式化 + 复制
4. 测试各按钮功能：
   - 执行：输入 SQL 点击执行，结果正常展示
   - 格式化：输入乱序 SQL，点击后正确格式化
   - 解释：点击后弹出 Explain Drawer
   - 复制：点击后 SQL 复制到剪贴板
5. 确认实例/Schema 下拉框在顶部信息栏右侧正常显示和切换
6. 审核按钮可正常使用