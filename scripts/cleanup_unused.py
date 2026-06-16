#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

p = r'C:\Users\Administrator\Documents\dataOps\frontend\src\pages\PermissionRequestPage.tsx'
with open(p, 'r', encoding='utf-8') as f:
    c = f.read()

c = c.replace('const { user, hasPermission } = useAuthStore();', 'const { hasPermission } = useAuthStore();')

# Remove unused state: resources, selectedResourceType
old = 'const [databases, setDatabases] = useState<any[]>([]);\n  const [resources, setResources] = useState<any[]>([]);\n  const [selectedResourceType, setSelectedResourceType] = useState<string>(\'\');'
new = 'const [databases, setDatabases] = useState<any[]>([]);'
c = c.replace(old, new)

# Remove references to setResourceType
c = c.replace('      setSelectedResourceType(\'\');\n', '')
c = c.replace('setResources([]);\n', '')
c = c.replace('setResources(databases.map((db: any) => ({\n        label: `${db.name || db.id} (${db.type || \'\'})`,\n        value: db.id,\n      })));\n', '')

with open(p, 'w', encoding='utf-8') as f:
    f.write(c)
print('Cleanup done!')