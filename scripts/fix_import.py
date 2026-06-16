# -*- coding: utf-8 -*-
p = 'C:\\Users\\Administrator\\Documents\\dataOps\\frontend\\src\\App.tsx'
with open(p, 'r', encoding='utf-8') as f:
    c = f.read()
# Remove FileTextOutlined from import
c = c.replace(', FileTextOutlined,', ',')
with open(p, 'w', encoding='utf-8') as f:
    f.write(c)
print('OK')