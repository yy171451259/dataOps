---
name: dms-dev
description: DataOps DMS 项目开发技能，包含技术栈、编码规范、项目结构、API接口规范、常见问题解决方案和新增功能标准流程。当开发或修改此项目代码时应使用此技能。
---

# DataOps DMS 项目开发 Skill

## 项目概述
一站式数据管理服务平台（类似阿里云DMS），支持多数据库管理、SQL查询、变更工单、审批流程、审计日志、数据脱敏、数据质量、元数据管理等企业级功能。

## 当前技术栈（2026.06.11 最新状态）

### 后端技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| **Java** | 1.8 | 编程语言（不是Java 17） |
| **Spring Boot** | 2.7.18 | 应用框架（已集成Security） |
| **MyBatis-Plus** | 3.5.5 | ORM框架 |
| **Druid** | 1.2.20 | 数据库连接池 |
| **MySQL** | 8.0+ | 元数据库 |
| **MySQL Connector** | 8.0.33 | JDBC驱动 |
| **Flowable** | 6.6.0 | 工作流引擎（审批流程） |
| **JWT** | 0.12.3 | 认证鉴权 |
| **Spring Security** | 5.7.x | 安全框架 |
| **Spring Mail** | - | 邮件通知 |
| **Apache POI** | 5.2.5 | Excel导出 |
| **Fastjson2** | 2.0.43 | JSON处理 |
| **Swagger/OpenAPI** | 1.7.0 | API文档 |
| **多数据库支持** | MySQL / PostgreSQL / Oracle | 数据源管理 |

### 前端技术栈
| 技术 | 版本 | 说明 |
|------|------|------|
| **React** | 18.2.0 | 前端框架 |
| **TypeScript** | 5.3.3 | 类型系统 |
| **Ant Design** | 5.14.0 | UI组件库 |
| **React Router** | 6.22.0 | 路由管理 |
| **Monaco Editor** | 4.6.0 | SQL编辑器 |
| **Zustand** | 4.4.7 | 状态管理 |
| **Axios** | 1.6.7 | HTTP客户端 |
| **Day.js** | 1.11.10 | 日期处理 |
| **Vite** | 6.4.3 | 构建工具 |

## 编码规范（强制遵守）

### 1. 文件编码
- **所有文件必须使用无BOM的UTF-8编码**
- PowerShell 写入方式（强制使用）：
  ```powershell
  [System.IO.File]::WriteAllText($path, $content, [System.Text.Encoding]::UTF8)
  ```
- ❌ 禁止使用：`Set-Content -Encoding UTF8`（会带BOM）

### 2. 后端命名规范
```
实体类:       Xxx.java (大驼峰, 继承BaseEntity)
Mapper接口:   XxxMapper.java (继承BaseMapper, @Mapper)
Service接口:  XxxService.java
Service实现:  XxxServiceImpl.java (继承ServiceImpl, @Service)
Controller:   XxxController.java (@RestController @RequestMapping)
DTO/VO:       XxxDTO.java / XxxVO.java
Config:       XxxConfig.java (@Configuration)
Filter:       XxxFilter.java
```

### 3. 前端命名规范
```
页面组件:     XxxPage.tsx (大驼峰)
公共组件:     XxxEditor.tsx / XxxTable.tsx
Store:        useXxxStore.ts
工具函数:     xxxUtil.ts (小驼峰)
类型定义:     xxx.type.ts
```

## 常用命令速查

### 后端命令
```bash
cd backend

# 启动服务
mvn clean spring-boot:run

# 编译检查
mvn clean compile -DskipTests

# 打包
mvn clean package -DskipTests

# 数据库初始化
mysql -uroot -p dataops_dms < src/main/resources/sql/init.sql
```

### 前端命令
```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务（端口3000）
npm run dev

# 构建生产包
npm run build

# 预览构建结果
npm run preview
```

## API 接口规范

### 统一返回格式
```java
// Result<T> 统一封装
{
  "code": 200,      // 200成功, 500失败, 401未登录
  "message": "ok",  // 提示信息
  "data": T,        // 返回数据
  "timestamp": 1234567890
}
```

### Controller URL命名规范
```
POST   /api/v1/auth/login         # 登录
GET    /api/v1/xxx                # 列表查询
GET    /api/v1/xxx/{id}           # 单个查询
POST   /api/v1/xxx                # 新增
PUT    /api/v1/xxx/{id}           # 更新
DELETE /api/v1/xxx/{id}           # 删除
POST   /api/v1/xxx/{id}/action    # 操作类接口
```

### 分页接口标准
```java
// 请求参数
{
  "pageNum": 1,
  "pageSize": 20,
  ... // 查询条件
}

// 返回结果
{
  "records": [...],
  "total": 100,
  "size": 20,
  "current": 1
}
```

## 常见问题解决方案

### 1. Flowable 启动报错
**症状**: `Table 'dataops_dms.act_ge_property' doesn't exist`
**解决方案**: 首次启动会自动创建Flowable表，如创建失败手动执行Flowable建表SQL。

### 2. 前端空白页面
**排查步骤**: F12 Console → npm install → 网络请求 → 路由配置 → Zustand store → BrowserRouter

### 3. JWT 认证失败
**症状**: 接口返回 401 / 403
**排查**: 请求头Authorization → Token过期 → JWT密钥 → SecurityConfig放行规则

### 4. Maven 依赖下载慢
配置 `~/.m2/settings.xml` 阿里云镜像

### 5. 编码问题（BOM）
**症状**: Java文件开头有乱码、编译失败
**清理**: 使用 Python 脚本清除 BOM 头

## 新增功能标准流程

### 后端新增模块
```
1. entity/Xxx.java → 2. mapper/XxxMapper.java → 3. service/XxxService.java
→ 4. service/impl/XxxServiceImpl.java → 5. controller/XxxController.java → 6. sql/init.sql
```

### 前端新增页面
```
1. pages/XxxPage.tsx → 2. App.tsx 路由 → 3. 左侧菜单 → 4. 对接API
```

## 开发注意事项（强制）

1. **文件编码必须无BOM UTF-8**
2. **所有Entity必须继承BaseEntity**
3. **所有接口必须有权限控制**
4. **SQL必须经过审核**，DELETE/UPDATE 必须有 WHERE 条件
5. **操作必须记录审计日志**
6. **Git提交规范**: feat/fix/refactor/docs/style/perf

## 联调验证清单

- [ ] 后端无编译错误
- [ ] 后端能正常启动（端口8080）
- [ ] 前端能正常启动（端口3000）
- [ ] 登录流程正常
- [ ] 数据库连接测试成功
- [ ] SQL查询执行成功
- [ ] 工单创建审批流程正常
- [ ] 审计日志正确记录
- [ ] 所有页面无控制台错误
- [ ] 文件编码全部为无BOM UTF-8

## 项目架构图

```
┌─────────────────────────────────────────────────────┐
│                     前端 (React)                      │
│  ┌─────────┐  ┌──────────┐  ┌──────────┐           │
│  │ 仪表盘  │  │ SQL编辑器 │  │ 工单管理 │  ...      │
│  └─────────┘  └──────────┘  └──────────┘           │
└──────────────────────┬──────────────────────────────┘
                       │ HTTP + JWT
┌──────────────────────▼──────────────────────────────┐
│                   后端 (Spring Boot)                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐           │
│  │ Security │  │ Flowable │  │  SQL引擎 │  ...      │
│  └──────────┘  └──────────┘  └──────────┘           │
│  ┌───────────────────────────────────────────────┐  │
│  │              MyBatis + Druid 连接池              │  │
│  └───────────────────────────────────────────────┘  │
└──────────────────────┬──────────────────────────────┘
                       │ JDBC
┌──────────────────────▼──────────────────────────────┐
│                   数据源层                            │
│  MySQL   PostgreSQL   Oracle   ...  业务数据库       │
└─────────────────────────────────────────────────────┘
```
