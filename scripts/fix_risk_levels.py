#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/SchemaDesignerPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Add infoNotes to RiskAssessment interface
c = c.replace(
    'interface RiskAssessment {\n  level: \'high\' | \'medium\' | \'low\';\n  message: string;\n  details: string[];\n}',
    'interface RiskAssessment {\n  level: \'high\' | \'medium\' | \'low\';\n  message: string;\n  details: string[];\n  infoNotes?: string[];\n}'
)

# 2. Add infoNotes array after risks array
c = c.replace(
    'const risks: string[] = [];\n    let hasChanges = false;\n    \n    lines.push',
    'const risks: string[] = [];\n    const infoNotes: string[] = [];\n    let hasChanges = false;\n    \n    lines.push'
)

# 3. Length expansion -> infoNotes
c = c.replace(
    '} else if (origLen && newLen && newLen > origLen && origType === newType) {\n            risks.push(`字段 [${col.name}] 长度从 (${origLen}) 扩展到 (${newLen})，无数据丢失风险`);\n          }',
    '} else if (origLen && newLen && newLen > origLen && origType === newType) {\n            infoNotes.push(`字段 [${col.name}] 长度从 (${origLen}) 扩展到 (${newLen})，无数据丢失风险`);\n          }'
)

# 4. NOT NULL -> nullable -> infoNotes
c = c.replace(
    '} else if (!origCol.nullable && col.nullable) {\n            risks.push(`字段 [${col.name}] 从 NOT NULL 改为可空，允许写入空值`);\n          }',
    '} else if (!origCol.nullable && col.nullable) {\n            infoNotes.push(`字段 [${col.name}] 从 NOT NULL 改为可空，允许写入空值`);\n          }'
)

# 5. Comment change -> infoNotes
c = c.replace(
    'if ((origCol.comment || \'\') !== (col.comment || \'\')) {\n            risks.push(`字段 [${col.name}] 注释已更新: "${origCol.comment || \'\'}" → "${col.comment || \'\'}"`);\n          }',
    'if ((origCol.comment || \'\') !== (col.comment || \'\')) {\n            infoNotes.push(`字段 [${col.name}] 注释已更新: "${origCol.comment || \'\'}" → "${col.comment || \'\'}"`);\n          }'
)

# 6. Default change -> infoNotes
c = c.replace(
    'if ((origCol.default || \'\') !== (col.default || \'\')) {\n            risks.push(`字段 [${col.name}] 默认值从 "${origCol.default || \'无\'}" 改为 "${col.default || \'无\'}"`);\n          }',
    'if ((origCol.default || \'\') !== (col.default || \'\')) {\n            infoNotes.push(`字段 [${col.name}] 默认值从 "${origCol.default || \'无\'}" 改为 "${col.default || \'无\'}"`);\n          }'
)

# 7. Unsigned -> infoNotes
c = c.replace(
    'if (origCol.unsigned !== col.unsigned && col.unsigned) {\n            risks.push(`字段 [${col.name}] 改为无符号(UNSIGNED)，负数数据会导致异常`);\n          }',
    'if (origCol.unsigned !== col.unsigned && col.unsigned) {\n            infoNotes.push(`字段 [${col.name}] 改为无符号(UNSIGNED)，负数数据会导致异常`);\n          }'
)

# 8. Auto increment -> infoNotes
c = c.replace(
    'if (!origCol.autoIncrement && col.autoIncrement) {\n            risks.push(`字段 [${col.name}] 改为自增(AUTO_INCREMENT)，仅整型主键可设置`);\n          }',
    'if (!origCol.autoIncrement && col.autoIncrement) {\n            infoNotes.push(`字段 [${col.name}] 改为自增(AUTO_INCREMENT)，仅整型主键可设置`);\n          }'
)

# 9. Update risk calculation
c = c.replace(
    'if (risks.length > 0) {\n      riskLevel = \'medium\';\n      riskMessage = \'存在中等风险，请仔细审核SQL\';\n    }\n\n    if (!hasChanges) {',
    'if (risks.length > 0) {\n      if (riskLevel !== \'high\') riskLevel = \'medium\';\n      riskMessage = \'存在中等风险，请仔细审核SQL\';\n    }\n\n    if (risks.length === 0 && infoNotes.length > 0) {\n      riskLevel = \'low\';\n      riskMessage = \'变更无高风险，但有以下变更提示\';\n    }\n\n    if (!hasChanges) {'
)

# 10. Update setRiskAssessment
c = c.replace(
    'setRiskAssessment({ level: riskLevel, message: riskMessage, details: risks });',
    'setRiskAssessment({ level: riskLevel, message: riskMessage, details: risks, infoNotes });'
)

# 11. Update modal rendering to show infoNotes
c = c.replace(
    'description={\n              riskAssessment.details.length > 0 && (\n                <ul style={{ margin: \'8px 0 0 20px\', padding: 0 }}>\n                  {riskAssessment.details.map((d, i) => (\n                    <li key={i} style={{ marginBottom: 4 }}>{d}</li>\n                  ))}\n                </ul>\n              )\n            }',
    'description={\n              <>\n                {riskAssessment.details.length > 0 && (\n                  <ul style={{ margin: \'8px 0 4px 20px\', padding: 0 }}>\n                    {riskAssessment.details.map((d, i) => (\n                      <li key={i} style={{ marginBottom: 4, color: \'#cf1322\' }}>⚠ {d}</li>\n                    ))}\n                  </ul>\n                )}\n                {riskAssessment.infoNotes && riskAssessment.infoNotes.length > 0 && (\n                  <ul style={{ margin: \'4px 0 0 20px\', padding: 0 }}>\n                    {riskAssessment.infoNotes.map((d, i) => (\n                      <li key={i} style={{ marginBottom: 4, color: \'#666\' }}>ℹ {d}</li>\n                    ))}\n                  </ul>\n                )}\n              </>\n            }'
)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print('OK: all changes applied')