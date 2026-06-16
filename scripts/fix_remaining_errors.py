#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/PermissionRequestPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Restore Badge and Tooltip in imports
c = c.replace(
    'Space, Card, Descriptions, Empty, Popconfirm,',
    'Space, Card, Descriptions, Badge, Empty, Popconfirm, Tooltip,'
)
print('1. Badge/Tooltip restored')

# 2. Fix toggleInstance types
old = 'const toggleInstance = (id)'
new = 'const toggleInstance = (id: string)'
c = c.replace(old, new)
print('2. toggleInstance typed')

old = 'const toggleResourceCheck = (dbId, checked)'
new = 'const toggleResourceCheck = (dbId: string, checked: boolean)'
c = c.replace(old, new)
print('3. toggleResourceCheck typed')

# 4. Remove handleResourceTypeChange function and its handler
# Find the function
old_hrc_start = 'const handleResourceTypeChange'
old_hrc_end = 'submitForm.setFieldsValue({ resourceId: undefined });\n  };'
idx_start = c.find(old_hrc_start)
if idx_start >= 0:
    idx_end = c.find(old_hrc_end, idx_start)
    if idx_end >= 0:
        c = c[:idx_start] + c[idx_end + len(old_hrc_end):]
        print('4. handleResourceTypeChange removed')
    else:
        print('4. handleResourceTypeChange end not found')
else:
    print('4. handleResourceTypeChange not found')

# 5. Remove the side panel of resources/resources loading (the handleResourceTypeChange related panel)
# The resources state and resources loading code is now unused but harmless

# 6. expireOptions should be defined - check if it was added
if 'expireOptions' not in c:
    print('5. expireOptions missing - need to check')
else:
    print('5. expireOptions found')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('Done!')