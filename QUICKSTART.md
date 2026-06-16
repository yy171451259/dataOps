# 🚀 DataOps DMS 快速启动指南

## 环境要求

✅ **JDK 17+**  
✅ **Maven 3.8+**  
✅ **MySQL 8.0+**  
✅ **Node.js 18+**  

---

## 三步快速启动

### 📦 第一步：初始化数据库

```bash
# 1. 创建数据库
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS dataops_dms DEFAULT CHARACTER SET utf8 COLLATE utf8_unicode_ci;"

# 2. 执行初始化脚本
mysql -uroot -p dataops_dms < backend/src/main/resources/sql/init.sql
```

**默认管理员账号**: `admin` / `admin123`

---

### 🔧 第二步：启动后端服务

```bash
cd backend

# 修改数据库连接配置（如需要）
# 编辑 src/main/resources/application.yml
# spring.datasource.username/password

# 启动 Spring Boot
mvn spring-boot:run
```

✅ 启动成功后访问: http://localhost:8080

**验证接口**:
```bash
# 健康检查
curl http://localhost:8080/actuator/health

# 获取数据库列表
curl http://localhost:8080/api/v1/databases
```

---

### 🎨 第三步：启动前端服务

```bash
cd frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```

✅ 启动成功后访问: http://localhost:3000

---

## ✅ 功能测试清单

### 1. SQL查询窗口
- [ ] 访问 http://localhost:3000
- [ ] 左侧菜单点击「SQL查询」
- [ ] 选择数据库实例
- [ ] 输入查询 SQL: `SELECT 1 AS test`
- [ ] 点击「执行SQL」查看结果

### 2. 数据库实例管理
- [ ] 点击「数据库管理」菜单
- [ ] 点击「添加数据库」按钮
- [ ] 填写数据库连接信息
- [ ] 点击「测试连接」验证

### 3. 工单审批流程
- [ ] 点击「工单管理」菜单
- [ ] 点击「创建工单」
- [ ] 填写变更 SQL（例如: `SELECT 1`）
- [ ] 提交审批
- [ ] 在「待我审批」中看到工单
- [ ] 点击「通过」或「拒绝」

### 4. SQL审核功能
- [ ] 创建工单时输入危险SQL: `DROP TABLE test`
- [ ] 验证是否弹出高风险警告
- [ ] 验证是否检测到无WHERE的DELETE/UPDATE

### 5. 数据备份回滚
- [ ] 提交 UPDATE/DELETE 类型工单
- [ ] 审批通过后
- [ ] 在工单详情中查看「自动生成的回滚SQL」
- [ ] 点击「回滚」按钮测试

### 6. 审计日志
- [ ] 点击「审计日志」菜单
- [ ] 查看操作历史记录
- [ ] 点击「详情」查看完整信息

---

## 🐛 常见问题

### 1. Maven 下载依赖慢
```bash
# 配置阿里云镜像
# 在 ~/.m2/settings.xml 中添加:
<mirror>
  <id>aliyun</id>
  <mirrorOf>central</mirrorOf>
  <name>Aliyun Maven</name>
  <url>https://maven.aliyun.com/repository/public</url>
</mirror>
```

### 2. npm install 慢
```bash
npm config set registry https://registry.npmmirror.com
```

### 3. 数据库连接失败
- 检查 MySQL 是否启动
- 检查用户名密码是否正确
- 确认数据库 `dataops_dms` 已创建

### 4. Flowable 启动报错
- 确认数据库使用 MySQL 8.0+
- 确认没有其他 Flowable 表冲突（首次启动会自动建表）

---

## 📊 项目端口

| 服务 | 端口 | 地址 |
|------|------|------|
| 后端API | 8080 | http://localhost:8080 |
| 前端页面 | 3000 | http://localhost:3000 |

---

## 🎯 预期效果

启动成功后，你将看到一个完整的企业级数据管理平台：

- 🎨 **现代化界面**: Ant Design Pro 风格
- 📝 **专业SQL编辑器**: VS Code 同款 Monaco Editor
- 🔐 **完整审批流**: Flowable BPMN 工作流引擎
- 🛡️ **智能SQL审核**: 10+项安全检查规则
- 💾 **自动备份回滚**: 数据变更零风险
