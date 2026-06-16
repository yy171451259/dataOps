#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/PermissionRequestPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# Fix loadDatabases to also set instances
old = 'const loadDatabases = async () => {\n    try {\n      const res = await databaseApi.list();\n      setDatabases(Array.isArray(res?.data?.data) ? res.data.data : []);\n    } catch {\n      setDatabases([]);\n    }\n  };'
new = 'const loadDatabases = async () => {\n    try {\n      const res = await databaseApi.list();\n      const data = Array.isArray(res?.data?.data) ? res.data.data : [];\n      setDatabases(data);\n      setInstances(data.map((db: any) => ({\n        id: db.id || db.name, name: db.name || db.id,\n        type: db.type || \'mysql\', host: db.host || \'\', port: db.port || 3306,\n      })));\n    } catch {\n      setDatabases([]);\n    }\n  };'
assert old in c, 'loadDatabases not found!'
c = c.replace(old, new)
print('OK: loadDatabases updated')

# Add toggle functions after loadDatabases
old = '\n  const handleResourceTypeChange'
new = '\n  const toggleInstance = (id) => {\n    const s = new Set(expandedInstances);\n    s.has(id) ? s.delete(id) : s.add(id);\n    setExpandedInstances(s);\n  };\n\n  const toggleResourceCheck = (dbId, checked) => {\n    if (!checked) {\n      setSelectedResources(prev => prev.filter(r => r.databaseId !== dbId));\n      return;\n    }\n    const inst = instances.find(i => i.id === dbId);\n    if (inst) setSelectedResources(prev => [...prev, { instanceId: inst.id, instanceName: inst.name, databaseId: inst.id, databaseName: inst.name }]);\n  };' + old
assert old in c, 'handleResourceTypeChange not found!'
c = c.replace(old, new)
print('OK: toggle functions added')

# Now update the form. Let me find and replace the whole form section
# The form is in the tab items - find the create tab
old = '"create"'
# Actually let me find the form by looking at the submit button
old = '提交申请</Button>'
new = '提交权限申请</Button>'
c = c.replace(old, new)
print('OK: submit button text updated')

# Replace the form content - find from resourceType select to after TextArea
old_form_marker = '请选择资源类型'
if old_form_marker in c:
    print(f'WARN: old form marker found - form may not have been replaced')
else:
    print('OK: form already updated')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('Done!')