#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/PermissionRequestPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

old = 'const handleSubmit = async (values: any) => {\n    setSubmitting(true);\n    try {\n      await permissionRequestApi.submit({\n        resourceType: values.resourceType,\n        resourceId: values.resourceId,\n        resourceName: values.resourceName || values.resourceId,\n        permissions: values.permissions || [],\n        reason: values.reason,\n      });\n      message.success(\'权限申请已提交\');\n      submitForm.resetFields();\n      setSelectedResourceType(\'\');\n      setActiveTab(\'my\');\n      loadMyRequests();\n    } catch (e: any) {\n      message.error(e?.response?.data?.message || \'提交失败\');\n    } finally {\n      setSubmitting(false);\n    }\n  };'

new = 'const handleSubmit = async (values: any) => {\n    if (selectedResources.length === 0) { message.warning(\'请至少选择一个数据库\'); return; }\n    if (checkedPerms.length === 0) { message.warning(\'请选择权限类型\'); return; }\n    setSubmitting(true);\n    try {\n      await permissionRequestApi.submitTicket({\n        title: values.title || \'权限申请\',\n        reason: values.reason || \'\',\n        ticketType: \'database\',\n        resources: selectedResources,\n        permissionTypes: checkedPerms,\n        expireDays: expireDays > 0 ? expireDays : undefined,\n      });\n      message.success(\'权限申请已提交，请等待审批\');\n      setSelectedResources([]);\n      setCheckedPerms([\'query\']);\n      setExpireDays(30);\n      submitForm.resetFields();\n      setActiveTab(\'my\');\n      loadMyRequests();\n    } catch (e: any) {\n      message.error(e?.response?.data?.message || \'提交失败\');\n    } finally {\n      setSubmitting(false);\n    }\n  };'

assert old in c, 'handleSubmit not found'
c = c.replace(old, new)

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('Done! handleSubmit updated')