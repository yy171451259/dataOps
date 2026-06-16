# DataOps DMS - 一站式数据管理服务平台

基于阿里云DMS功能需求的企业级数据管理平台，支持多数据库统一管理、SQL查询、数据变更、审批流程、审计日志、数据脱敏等核心功能。

## 技术栈

### 后端
- **框架**: Spring Boot 3.2
- **语言**: Java 17
- **ORM**: MyBatis-Plus 3.5
- **数据库**: MySQL 8.0
- **连接池**: Druid
- **认证**: JWT + Spring Security
- **工作流**: Flowable 7.0
- **SQL解析**: JSqlParser 4.7
- **多数据库支持**: MySQL、PostgreSQL、Oracle、SQL Server、OceanBase、达梦、人大金仓等

### 前端
- **框架**: React 18 + TypeScript
- **UI组件**: Ant Design 5
- **SQL编辑器**: Monaco Editor
- **构建工具**: Vite
- **状态管理**: Zustand

## ✅ 核心功能模块 (已实现)

| 模块 | 功能描述 | 状态 | 版本 |
|------|---------|------|------|
| **SQL查询窗口** | 语法高亮、智能补全、结果可视化、导出 | ✅ 完成 | V1.0 |
| **数据库实例管理** | 多类型数据库连接、健康检查、分组管理 | ✅ 完成 | V1.0 |
| **数据变更工单** | 工单创建、SQL预览、影响行数预估 | ✅ 完成 | V2.0 |
| **审批流程引擎** | 二级审批（项目经理→DBA）、Flowable BPMN | ✅ 完成 | V2.0 |
| **SQL智能审核** | 10+审核规则、危险SQL检测、优化建议 | ✅ 完成 | V2.0 |
| **自动备份回滚** | 执行前自动备份、自动生成回滚SQL、一键回滚 | ✅ 完成 | V2.0 |
| **审计日志** | 全链路操作记录、风险等级评估、详情查询 | ✅ 完成 | V2.0 |
| **RBAC权限模型** | 用户角色权限、细粒度控制 | ⚙️ 部分完成 | V1.0 |
| **敏感数据管理** | 自动发现、动态/静态脱敏 | 📋 计划中 | V3.0 |
| **无锁结构变更** | Online DDL、不锁表变更 | 📋 计划中 | V3.0 |

## 🏗️ 项目结构

```
dataops-dms/
├── backend/                    # Java后端
│   ├── src/main/java/com/dataops/dms/
│   │   ├── DmsApplication.java     # 启动类
│   │   ├── sql/                     # SQL执行核心
│   │   │   ├── SqlExecutor.java       # 多数据库SQL执行器
│   │   │   ├── SqlAuditEngine.java    # SQL智能审核引擎（10+规则）
│   │   │   ├── SqlRollbackGenerator.java # 回滚SQL自动生成器
│   │   │   └── SqlExecuteResult.java
│   │   ├── workflow/                # 工作流引擎
│   │   │   ├── FlowableConfig.java    # Flowable配置
│   │   │   └── SqlExecuteDelegate.java # SQL执行委托任务
│   │   ├── service/                 # 业务服务层
│   │   │   ├── TicketService.java     # 工单服务
│   │   │   ├── DatabaseInstanceService.java # 数据库实例服务
│   │   │   └── DataBackupService.java # 数据备份回滚服务
│   │   ├── controller/              # 控制器
│   │   │   └── TicketController.java  # 工单API
│   │   ├── entity/                  # 实体类
│   │   │   ├── Ticket.java
│   │   │   ├── DataChangeBackup.java   # 数据变更备份
│   │   │   ├── DatabaseInstance.java
│   │   │   └── AuditLog.java
│   │   ├── config/                  # 配置类
│   │   └── common/                  # 公共组件
│   └── src/main/resources/
│       ├── application.yml          # 应用配置
│       ├── sql/init.sql             # 数据库初始化脚本
│       └── processes/data-change.bpmn20.xml # BPMN审批流程定义
├── frontend/                   # React前端
│   ├── src/
│   │   ├── App.tsx               # 主应用（侧边栏+路由）
│   │   ├── components/
│   │   │   └── SqlEditor.tsx     # Monaco SQL编辑器
│   │   ├── pages/
│   │   │   ├── TicketList.tsx    # 工单管理（创建/审批/回滚）
│   │   │   ├── DatabaseList.tsx  # 数据库实例管理
│   │   │   └── AuditList.tsx     # 审计日志
│   ├── package.json
│   └── vite.config.ts
└── docs/                       # 文档
```

## 🔄 数据变更安全保障（已实现）

### 完整的变更安全闭环
```
提交工单 
    ↓
SQL智能审核（危险检测）
    ↓
项目经理审批 ✅
    ↓
DBA审批 ✅
    ↓
======== 执行前自动 ========
① 解析SQL类型（INSERT/UPDATE/DELETE）
② 查询即将被变更的原始数据
③ 自动生成回滚SQL：
   - DELETE → 生成对应 INSERT
   - UPDATE → 生成反向 UPDATE
   - INSERT → 提醒手动确认
④ 保存备份记录（含回滚SQL）
===========================
    ↓
执行SQL变更 ✅
    ↓
工单完成（支持一键回滚）
```

### SQL审核规则（10+项）
| 风险等级 | 检查项 | 描述 |
|----------|--------|------|
| 🔴 高风险 | DROP / TRUNCATE | 检测表删除操作 |
| 🔴 高风险 | DELETE/UPDATE 无WHERE | 全表数据变更 |
| 🟡 中风险 | SELECT * | 全列查询性能问题 |
| 🟡 中风险 | LIKE %xxx | 前缀通配符索引失效 |
| 🟢 建议 | OR 条件 | 可能导致索引失效 |
| 🟢 建议 | 无 LIMIT | 大结果集风险 |

## 🚀 快速开始

### 前置要求
- JDK 17+
- Maven 3.8+
- MySQL 8.0+
- Node.js 18+

### 后端启动

1. **创建数据库**
```sql
CREATE DATABASE dataops_dms DEFAULT CHARACTER SET utf8 COLLATE utf8_unicode_ci;
```

2. **初始化表结构**
```bash
mysql -uroot -p < backend/src/main/resources/sql/init.sql
```

3. **修改配置**
```yaml
# backend/src/main/resources/application.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/dataops_dms
    username: root
    password: your_password
```

4. **启动服务**
```bash
cd backend
mvn spring-boot:run
```

后端访问: http://localhost:8080  
默认账号: `admin` / `admin123`

### 前端启动

```bash
cd frontend
npm install
npm run dev
```

前端访问: http://localhost:3000

## 📊 版本演进

### ✅ V1.0 - 基础版 (已完成)
- SQL查询窗口
- 数据库实例管理
- 用户权限管理
- 基础审计日志

### ✅ V2.0 - 标准版 (已完成 90%)
- ✅ 数据变更工单
- ✅ Flowable审批流程引擎
- ✅ SQL审核与优化建议
- ✅ 自动备份与一键回滚
- ⏳ 数据导入导出

### 📋 V3.0 - 企业版 (计划中)
- 无锁结构变更（Online DDL）
- 敏感数据自动发现与脱敏
- 数据资产管理
- 自动化运维

### 📋 V4.0 - 智能版 (规划中)
- AI智能SQL生成
- 自动性能优化
- 智能运维机器人
- 自然语言查询

## 🔒 安全合规

- ✅ 等保2.0三级合规设计
- ✅ 全链路操作审计
- ✅ 敏感数据加密存储
- ✅ SQL注入防护
- ✅ 权限越权拦截
- ✅ 数据变更前自动备份

## 🤝 技术亮点

1. **企业级工作流** - 基于 Flowable BPMN 2.0，支持复杂审批场景扩展
2. **智能SQL审核** - 10+审核规则，实时检测高危操作
3. **自动备份回滚** - 变更前自动备份，一键恢复，数据零丢失
4. **多数据库统一管理** - MySQL / PostgreSQL / Oracle / SQL Server 一平台管理
5. **前后端分离架构** - React + Spring Boot 经典架构，易于团队协作

## 📝 开发文档

详见 [阿里云DMS功能清单与需求文档](./阿里云DMS功能清单与需求文档.md)

## License

MIT
