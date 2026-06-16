#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/PermissionRequestPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

old = "  const [selectedResourceType, setSelectedResourceType] = useState<string>('');\n\n  // Tab 3: 待审批"
new = "  const [selectedResourceType, setSelectedResourceType] = useState<string>('');\n  const [instances, setInstances] = useState<any[]>([]);\n  const [selectedResources, setSelectedResources] = useState<any[]>([]);\n  const [checkedPerms, setCheckedPerms] = useState<string[]>(['query']);\n  const [expireDays, setExpireDays] = useState<number>(30);\n  const [expandedInstances, setExpandedInstances] = useState<Set<string>>(new Set());\n\n  // Tab 3: 待审批"

assert old in c, 'selectedResourceType not found'
c = c.replace(old, new)
print('States added!')

# Fix toggle functions to have proper types
old = "const toggleInstance = (id)"

# Check if toggle already exists
if 'toggleInstance' in c:
    print('toggleInstance already exists, skipping')
else:
    # Need to add them - check if handleResourceTypeChange exists
    idx = c.find('const handleResourceTypeChange')
    if idx > 0:
        toggles = '\n  const toggleInstance = (id: string) => {\n    const s = new Set(expandedInstances);\n    s.has(id) ? s.delete(id) : s.add(id);\n    setExpandedInstances(s);\n  };\n\n  const toggleResourceCheck = (dbId: string, checked: boolean) => {\n    if (!checked) {\n      setSelectedResources(prev => prev.filter(r => r.databaseId !== dbId));\n      return;\n    }\n    const inst = instances.find(i => i.id === dbId);\n    if (inst) setSelectedResources(prev => [...prev, {\n      instanceId: inst.id, instanceName: inst.name,\n      databaseId: inst.id, databaseName: inst.name,\n    }]);\n  };\n\n  '
        c = c[:idx] + toggles + c[idx:]
        print('Toggle functions added!')
    else:
        print('handleResourceTypeChange not found!')

# Remove unused imports
c = c.replace("  Button, Table, message, Tabs, Tag, Form, Select, Checkbox, Input, Modal,\n  Space, Card, Descriptions, Badge, Empty, Spin, Popconfirm, Tooltip,", "  Button, Table, message, Tabs, Tag, Form, Select, Checkbox, Input, Modal,\n  Space, Card, Descriptions, Empty, Popconfirm,")
print('Unused imports removed')

# Remove unused state vars
# resources and selectedResourceType can stay as harmless unused vars

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('Done!')