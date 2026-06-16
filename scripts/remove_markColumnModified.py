import os, re

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/SchemaDesignerPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

old = '// ========== 标记字段修改 ==========\n  const markColumnModified = (columnId: string) => {\n    if (!currentTableStructure) return;\n\n    const updated = {\n      ...currentTableStructure,\n      columns: currentTableStructure.columns.map(c =>\n        c.id === columnId && !c.isNew ? { ...c, changeType: \'MODIFY\' as ColumnChangeType } : c\n      ),\n      isModified: true,\n    };\n\n    setCurrentTableStructure(updated);\n    setTables([updated]);\n    message.success(\'已标记为修改字段\');\n  };\n\n  // ========== 删除字段 =========='

if old in c:
    c = c.replace(old, '// ========== 删除字段 ==========')
    with open(path, 'w', encoding='utf-8') as f:
        f.write(c)
    print('OK: replaced')
else:
    print('FAIL: old not found')
    idx = c.find('markColumnModified')
    if idx >= 0:
        print(f'Found markColumnModified at {idx}')
        print(repr(c[idx:idx+300]))