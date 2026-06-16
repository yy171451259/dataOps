---
name: cleanup-temp-files
description: 临时文件清理规则。每次代码修改完成且验证通过后，自动删除过程中创建的临时 Python 脚本（如 fix_xxx.py、update_xxx.py 等）。防止项目根目录积累一次性脚本。
---

# 临时文件清理规则

## 适用场景
每次使用 `write_to_file` 创建临时 Python 脚本完成代码修改后，必须立即删除该脚本。

## 清理规则

1. **修改完成即删除**：代码修改完成且通过验证（lint / 编译通过）后，立即 `delete_file` 删除临时脚本
2. **不等待下次**：不要等下次清理，每次改完后顺手删
3. **仅删除临时脚本**：只删除为本次修改创建的 `.py` 临时文件，不删除 `scripts/` 目录下的正式脚本

## 示例

```
✅ 正确：
  1. write_to_file: fix_duplicate.py
  2. execute_command: python fix_duplicate.py
  3. read_lints 验证通过
  4. delete_file: fix_duplicate.py ← 立即删除

❌ 错误：
  1. write_to_file: fix_duplicate.py
  2. execute_command: python fix_duplicate.py
  3. 不管了，留下 fix_duplicate.py 在根目录
```
