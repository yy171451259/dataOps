import re

import os
script_dir = os.path.dirname(os.path.abspath(__file__))
project_dir = os.path.dirname(script_dir)
with open(os.path.join(project_dir, 'frontend/src/pages/SchemaDesignerPage.tsx'), 'r', encoding='utf-8') as f:
    content = f.read()

old_lines = [
    "        if (col.type.includes('INT') || col.type.includes('DATE')) {",
    "          risks.push(`字段 ${col.name} 类型修改为 ${col.type}，可能导致数据截断或转换失败`);",
    "        }",
]

old_block = '\n'.join(old_lines)

new_block = """        // 对比原始表结构生成针对性风险评估
        const origCol = originalTableStructure?.columns.find(c => c.name === col.name);
        if (origCol) {
          const origType = origCol.type, newType = col.type;
          const origLen = origCol.length, newLen = col.length;

          if (origType !== newType) {
            risks.push(`字段 [${col.name}] 类型从 ${origType} 改为 ${newType}，可能导致数据隐式转换或写入失败（高风险）`);
            riskLevel = 'high';
          }
          if (origLen && newLen && newLen < origLen && origType === newType) {
            risks.push(`字段 [${col.name}] 长度从 (${origLen}) 缩小到 (${newLen})，已有数据可能被截断（高风险）`);
            riskLevel = 'high';
          } else if (origLen && newLen && newLen > origLen && origType === newType) {
            risks.push(`字段 [${col.name}] 长度从 (${origLen}) 扩展到 (${newLen})，无数据丢失风险`);
          }
          if (origCol.nullable && !col.nullable) {
            risks.push(`字段 [${col.name}] 从可空改为 NOT NULL，如存在 NULL 值会导致变更失败（高风险）`);
            riskLevel = 'high';
          } else if (!origCol.nullable && col.nullable) {
            risks.push(`字段 [${col.name}] 从 NOT NULL 改为可空，允许写入空值`);
          }
          if (origCol.primaryKey !== col.primaryKey) {
            risks.push(`字段 [${col.name}] 主键属性发生变化，修改主键可能导致索引重建（高风险）`);
            if (riskLevel !== 'high') riskLevel = 'high';
          }
          if ((origCol.comment || '') !== (col.comment || '')) {
            risks.push(`字段 [${col.name}] 注释已更新: "${origCol.comment || ''}" → "${col.comment || ''}"`);
          }
          if ((origCol.default || '') !== (col.default || '')) {
            risks.push(`字段 [${col.name}] 默认值从 "${origCol.default || '无'}" 改为 "${col.default || '无'}"`);
          }
          if (origCol.unsigned !== col.unsigned && col.unsigned) {
            risks.push(`字段 [${col.name}] 改为无符号(UNSIGNED)，负数数据会导致异常`);
          }
          if (!origCol.autoIncrement && col.autoIncrement) {
            risks.push(`字段 [${col.name}] 改为自增(AUTO_INCREMENT)，仅整型主键可设置`);
          }
        }"""

count = content.count(old_block)
print(f"Found {count} occurrence(s)")
if count > 0:
    content = content.replace(old_block, new_block)
    with open(os.path.join(project_dir, 'frontend/src/pages/SchemaDesignerPage.tsx'), 'w', encoding='utf-8') as f:
        f.write(content)
    print("Replacement done!")
else:
    # Debug: find partial match
    idx = content.find('col.type.includes')
    if idx >= 0:
        snippet = content[idx:idx+200]
        print(f"Found near: {repr(snippet[:100])}")
    else:
        print("Not found at all")