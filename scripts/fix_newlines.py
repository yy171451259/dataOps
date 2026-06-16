#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os
script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/SchemaDesignerPage.tsx')
with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# Fix literal \n in strings - replace them with actual newlines
c = c.replace(
    "lines.push('-- ============================================\\n');",
    "lines.push('-- ============================================');\n    lines.push('');"
)
c = c.replace(
    "const sql = lines.join('\\n');",
    'const sql = lines.join(\'\\n\');'
)

with open('frontend/src/pages/SchemaDesignerPage.tsx', 'w', encoding='utf-8') as f:
    f.write(c)
print('Fixed')