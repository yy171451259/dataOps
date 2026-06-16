---
name: frontend-code-editing
description: React/TypeScript/JSX 文件修改安全实践。当修改前端 .tsx/.ts 文件时使用此技能，避免语法错误和文件编码损坏。
---

# 前端代码修改安全实践

## 事故教训（2026-06-15）

### 事故经过
使用 PowerShell 批量替换 `destroyOnClose` → `destroyOnHidden`：
```powershell
Get-ChildItem -Filter '*.tsx' -Recurse | ForEach-Object {
  (Get-Content $_.FullName -Raw) -replace 'destroyOnClose', 'destroyOnHidden' |
  Set-Content $_.FullName -NoNewline
}
```

### 事故结果
- **全部 25+ 个 .tsx 文件编码从 UTF-8 损坏为 ANSI/GBK**
- 中文注释和 JSX 文本全部乱码
- 部分文件出现 Unterminated string constant 语法错误，Vite 编译失败
- 前端完全不可用，恢复耗时 30+ 分钟逐文件重写

### 恢复方法（按优先级）
1. **首选**：`write_to_file` 重写整个文件（最可靠，如 App.tsx、ResourceOwnerPage.tsx）
2. **次选**：Python Latin-1 recovery（`raw_bytes.decode('latin-1').encode('latin-1').decode('utf-8')`）
3. **备选**：Python GBK decode → UTF-8 encode（仅在文件确实是 GBK 编码时有效）
4. **最后手段**：用英文替换所有乱码中文 JSX 文本（保编译不保显示）

### 为什么 "只改一处" 却要批量操作？
`destroyOnClose` → `destroyOnHidden` 只是 antd deprecation warning，**不影响任何功能**。为消除一个无害警告而批量修改全部文件，风险远大于收益。**永远不要为消除警告而做批量替换。**

## 核心规则

### 1. 禁止的操作（红线）
- ❌ **严禁**使用 PowerShell `Set-Content` 或 `-replace` 操作任何 .tsx/.ts 文件
  - 原因：PowerShell 5.1 的 `Set-Content` 默认编码为 ANSI（中文 Windows 是 GBK），会损坏 UTF-8 文件
- ❌ **严禁**对 .tsx 文件做批量字符串替换
  - 原因：中文注释和 JSX 文本易被误伤，一旦损坏难以批量恢复
- ❌ **严禁**为消除 console warning 而修改代码
  - 原因：warning 不影响功能，批量修改风险远大于收益

### 2. 安全操作流程
- ✅ 使用 `replace_in_file` 工具做精确的单处修改（保 UTF-8 编码）
- ✅ 需要批量修改时，**必须**使用 Python 脚本，并明确指定 `encoding='utf-8'`
- ✅ 使用 `write_to_file` 重写整个文件（工具保证 UTF-8 输出）
- ✅ 修改前后使用 `read_lints` 检查语法错误
- ✅ 修改关键文件后立即验证前端能否正常启动

### 3. Python 安全脚本模板
```python
import os
fpath = r'path/to/file.tsx'
with open(fpath, 'r', encoding='utf-8') as f:
    content = f.read()
# 做修改...
with open(fpath, 'w', encoding='utf-8') as f:
    f.write(content)
```

### 4. 文件恢复检查清单
如果怀疑文件编码损坏：
1. 检查是否包含合法中文：`python -c "print('已' in open('f.tsx', encoding='utf-8').read())"`
2. 如果返回 False，尝试 GBK 恢复或 Latin-1 恢复
3. 恢复后必须重新编译验证

### 5. 复杂 JSX 处理
- 无论多长，拆分为多行
- 将 `{}` 表达式块放在独立行上
- 内联匿名函数提取为命名函数
