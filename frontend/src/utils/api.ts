import axios from 'axios';

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

// 请求拦截器 - 添加Token
api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// 响应拦截器 - 处理401
api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      localStorage.removeItem('user');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

// ============ 认证 ============
export const authApi = {
  login: (username: string, password: string) => api.post('/auth/login', { username, password }),
  register: (data: any) => api.post('/auth/register', data),
  refresh: () => api.post('/auth/refresh'),
  getMe: () => api.get('/auth/me'),
  changePassword: (oldPassword: string, newPassword: string) =>
    api.post('/auth/change-password', { oldPassword, newPassword }),
  getDingTalkAuthUrl: (state?: string) => api.get('/auth/dingtalk/auth-url', { params: { state } }),
  dingTalkLogin: (authCode: string) => api.post('/auth/dingtalk/callback', { authCode }),
};

// ============ SQL ============
export const sqlApi = {
  execute: (instanceId: string, sql: string, schemaName?: string, offset?: number, limit?: number) =>
    api.post('/sql/execute', { instanceId, sql, schemaName, offset: offset ?? 0, limit: limit ?? 0 }),
  audit: (sql: string) => api.post('/sql/audit', { sql }),
  explain: (instanceId: string, sql: string, schemaName?: string) => api.post('/sql/explain', { instanceId, sql, schemaName }),
};

// ============ 实例管理 ============
export const instanceApi = {
  list: () => api.get('/instances'),
  get: (id: string) => api.get(`/instances/${id}`),
  create: (data: any) => api.post('/instances', data),
  update: (id: string, data: any) => api.put(`/instances/${id}`, data),
  delete: (id: string) => api.delete(`/instances/${id}`),
  test: (id: string) => api.post(`/instances/${id}/test`),
  // 获取实例下的所有Schema列表
  getSchemas: (id: string) => api.get(`/instances/${id}/schemas`),
  // 获取实例下的所有Schema列表（不做权限过滤，用于权限申请页面）
  getSchemasAll: (id: string) => api.get(`/instances/${id}/schemas`, { params: { all: true } }),
  // 获取实例列表（不做权限过滤，用于权限申请页面）
  listAll: () => api.get('/instances', { params: { all: true } }),
  // 获取表列表
  getTableNames: (id: string, schemaName?: string) => api.get(`/instances/${id}/tables`, { params: { schemaName } }),
  // 获取单张表的建表语句
  getCreateTableSql: (id: string, tableName: string, schemaName?: string) => api.get(`/instances/${id}/tables/${tableName}/create-sql`, { params: { schemaName } }),
  // 批量获取多张表的建表语句
  batchGetCreateTableSql: (id: string, tableNames: string[], schemaName?: string) => api.post(`/instances/${id}/tables/batch-create-sql`, tableNames, { params: { schemaName } }),
  // 获取Schema（表名+列名+类型），用于SQL智能补全
  getSchema: (id: string, schemaName?: string) => api.get(`/instances/${id}/schema`, { params: { schemaName } }),
  // 获取浏览器综合Schema（含表、视图、存储过程、函数、触发器、事件）
  getBrowserSchema: (id: string, schemaName?: string) => api.get(`/instances/${id}/browser-schema`, { params: { schemaName } }),
  // 获取单张表的详细信息（索引/外键/约束/触发器/DDL）
  getTableDetail: (id: string, tableName: string, schemaName?: string) => api.get(`/instances/${id}/tables/${tableName}/detail`, { params: { schemaName } }),
};

// ============ 工单 ============
export const ticketApi = {
  create: (data: any) => api.post('/tickets', data),
  list: (params?: any) => api.get('/tickets', { params }),
  get: (id: string) => api.get(`/tickets/${id}`),
  pending: (params?: any) => api.get('/tickets/pending', { params }),
  my: (params?: any) => api.get('/tickets/my', { params }),
  approve: (id: string, comment?: string) => api.post(`/tickets/${id}/approve`, { comment }),
  reject: (id: string, comment: string) => api.post(`/tickets/${id}/reject`, { comment }),
  cancel: (id: string) => api.post(`/tickets/${id}/cancel`),
  rollback: (id: string) => api.post(`/tickets/${id}/rollback`),
  execute: (id: string) => api.post(`/tickets/${id}/execute`),
  auditSql: (sql: string) => api.post('/tickets/audit-sql', { sql }),
  // 无锁数据变更检测
  checkLockFreeDml: (instanceId: string, schemaName: string, sql: string) =>
    api.post('/tickets/check-lock-free-dml', { instanceId, schemaName, sql }),
  approvals: (id: string) => api.get(`/tickets/${id}/approvals`),
  update: (id: string, data: any) => api.put(`/tickets/${id}`, data),
};

// ============ 资源Owner ============
export const ownerApi = {
  list: () => api.get('/owners'),
  listByResource: (resourceType: string, resourceId: string) => 
    api.get('/owners/resource', { params: { type: resourceType, id: resourceId } }),
  listByUser: (userId: string) => api.get(`/owners/user/${userId}`),
  assign: (data: any) => api.post('/owners/assign', data),
  revoke: (id: string) => api.post(`/owners/${id}/revoke`),
  check: (resourceType: string, resourceId: string, userId: string) => 
    api.get('/owners/check', { params: { type: resourceType, id: resourceId, userId } }),
};

// ============ 访问控制 ============
export const accessControlApi = {
  list: () => api.get('/access-control'),
  check: (resourceType: string, resourceId: string, userId: string) => 
    api.get('/access-control/check', { params: { type: resourceType, id: resourceId, userId } }),
  enable: (data: any) => api.post('/access-control/enable', data),
  disable: (id: string, data?: any) => api.post(`/access-control/${id}/disable`, data || {}),
};

// ============ 敏感数据 ============
export const sensitiveApi = {
  listColumns: (instanceId: string, schemaName?: string, tableName?: string) =>
    api.get('/sensitive/columns', { params: { instanceId, schemaName, tableName } }),
  markColumn: (data: any) => api.post('/sensitive/columns', data),
  batchMark: (data: any) => api.post('/sensitive/columns/batch', data),
  deleteColumn: (id: string) => api.delete(`/sensitive/columns/${id}`),
  // 脱敏规则 CRUD
  listMaskRules: () => api.get('/sensitive/mask-rules'),
  getMaskRuleById: (id: string) => api.get(`/sensitive/mask-rules/${id}`),
  getMaskRuleByCode: (code: string) => api.get(`/sensitive/mask-rules/by-code/${code}`),
  createMaskRule: (data: any) => api.post('/sensitive/mask-rules', data),
  updateMaskRule: (id: string, data: any) => api.put(`/sensitive/mask-rules/${id}`, data),
  deleteMaskRule: (id: string) => api.delete(`/sensitive/mask-rules/${id}`),
};

// ============ 权限申请 ============
export const permissionRequestApi = {
  list: (params?: any) => api.get('/permission-requests', { params }),
  pending: (params?: any) => api.get('/permission-requests/pending', { params }),
  my: (params?: any) => api.get('/permission-requests/my', { params }),
  submit: (data: any) => api.post('/permission-requests', data),
  submitTicket: (data: any) => api.post('/permission-requests/ticket', data),
  approve: (id: string, data: any) => api.post(`/permission-requests/${id}/approve`, data),
  reject: (id: string, data: any) => api.post(`/permission-requests/${id}/reject`, data),
  cancel: (id: string) => api.post(`/permission-requests/${id}/cancel`),
};

// ============ 行级管控 ============
export const rowControlApi = {
  list: () => api.get('/row-controls'),
  create: (data: any) => api.post('/row-controls', data),
  update: (id: string, data: any) => api.put(`/row-controls/${id}`, data),
  delete: (id: string) => api.delete(`/row-controls/${id}`),
};

// ============ 审计 ============
export const auditApi = {
  list: (params?: any) => api.get('/audit', { params }),
};



// ============ 脱敏 ============
export const maskingApi = {
  listRules: (params?: any) => api.get('/masking/rules', { params }),
  getRule: (id: string) => api.get(`/masking/rules/${id}`),
  createRule: (data: any) => api.post('/masking/rules', data),
  updateRule: (id: string, data: any) => api.put(`/masking/rules/${id}`, data),
  deleteRule: (id: string) => api.delete(`/masking/rules/${id}`),
  toggleRule: (id: string, enabled: boolean) => api.post(`/masking/rules/${id}/toggle?enabled=${enabled}`),
};

// ============ 元数据 ============
export const metadataApi = {
  collect: (instanceId: string, schemaName?: string) => api.post(`/metadata/collect/${instanceId}`, { schemaName }),
  listTables: (params?: any) => api.get('/metadata/tables', { params }),
  getTable: (id: string) => api.get(`/metadata/tables/${id}`),
  updateTable: (id: string, params: any) => api.put(`/metadata/tables/${id}`, null, { params }),
  listColumns: (tableId: string) => api.get(`/metadata/tables/${tableId}/columns`),
  updateColumn: (id: string, params: any) => api.put(`/metadata/columns/${id}`, null, { params }),
  search: (params: any) => api.get('/metadata/search', { params }),
  stats: (instanceId: string) => api.get(`/metadata/stats/${instanceId}`),
};

// ============ 数据质量 ============
export const qualityApi = {
  listRules: (params?: any) => api.get('/quality/rules', { params }),
  getRule: (id: string) => api.get(`/quality/rules/${id}`),
  createRule: (data: any) => api.post('/quality/rules', data),
  updateRule: (id: string, data: any) => api.put(`/quality/rules/${id}`, data),
  deleteRule: (id: string) => api.delete(`/quality/rules/${id}`),
  toggleRule: (id: string, enabled: boolean) => api.post(`/quality/rules/${id}/toggle?enabled=${enabled}`),
  executeRule: (id: string) => api.post(`/quality/rules/${id}/execute`),
  executeAll: (databaseId: string) => api.post(`/quality/execute/${databaseId}`),
  listResults: (params?: any) => api.get('/quality/results', { params }),
};

// ============ 通知 ============
export const notificationApi = {
  listConfigs: (params?: any) => api.get('/notifications/configs', { params }),
  getConfig: (id: string) => api.get(`/notifications/configs/${id}`),
  createConfig: (data: any) => api.post('/notifications/configs', data),
  updateConfig: (id: string, data: any) => api.put(`/notifications/configs/${id}`, data),
  deleteConfig: (id: string) => api.delete(`/notifications/configs/${id}`),
  toggleConfig: (id: string, enabled: boolean) => api.post(`/notifications/configs/${id}/toggle?enabled=${enabled}`),
  testConfig: (id: string) => api.post(`/notifications/configs/${id}/test`),
  listLogs: (params?: any) => api.get('/notifications/logs', { params }),
};

// ============ 用户管理 ============
export const userApi = {
  list: (params?: any) => api.get('/users', { params }),
  get: (id: string) => api.get(`/users/${id}`),
  create: (data: any) => api.post('/users', data),
  update: (id: string, data: any) => api.put(`/users/${id}`, data),
  delete: (id: string) => api.delete(`/users/${id}`),
  resetPassword: (id: string, newPassword: string) =>
    api.post(`/users/${id}/reset-password?newPassword=${newPassword}`),
  assignRoles: (id: string, roleIds: string[]) => api.post(`/users/${id}/roles`, roleIds),
  getRoles: (id: string) => api.get(`/users/${id}/roles`),
};

// ============ 监控 ============
export const monitorApi = {
  system: () => api.get('/monitor/system'),
  health: () => api.get('/monitor/health'),
};

// ============ 数据库性能监控 ============
export const dbMonitorApi = {
  status: (instanceId: string, schemaName?: string) =>
    api.get(`/db-monitor/${instanceId}/status`, { params: { schemaName } }),
  slowQueries: (instanceId: string, schemaName?: string, limit = 20) =>
    api.get(`/db-monitor/${instanceId}/slow-queries`, { params: { schemaName, limit } }),
  locks: (instanceId: string, schemaName?: string) =>
    api.get(`/db-monitor/${instanceId}/locks`, { params: { schemaName } }),
  tableStats: (instanceId: string, schemaName?: string) =>
    api.get(`/db-monitor/${instanceId}/table-stats`, { params: { schemaName } }),
  diagnosis: (instanceId: string, schemaName?: string) =>
    api.get(`/db-monitor/${instanceId}/diagnosis`, { params: { schemaName } }),
  killProcess: (instanceId: string, processId: number, schemaName?: string) =>
    api.post(`/db-monitor/${instanceId}/kill-process`, { processId, schemaName }),
};

// ============ DDL工作台 ============
export const ddlWorkbenchApi = {
  assessRisk: (instanceId: string, schemaName: string, sql: string) =>
    api.post('/ddl-workbench/assess-risk', { instanceId, schemaName, sql }),
  executeDirect: (data: any) => api.post('/ddl-workbench/execute-direct', data),
  getProgress: (taskId: string) => api.get(`/ddl-workbench/execution-progress/${taskId}`),
  executionHistory: (limit = 20) => api.get('/ddl-workbench/execution-history', { params: { limit } }),
  submitTicket: (data: any) => api.post('/ddl-workbench/submit-ticket', data),
  getTableIndexes: (instanceId: string, tableName: string, schemaName?: string) =>
    api.get('/ddl-workbench/table-indexes', { params: { instanceId, tableName, schemaName } }),
  previewRollback: (sql: string, tableName?: string) =>
    api.post('/ddl-workbench/preview-rollback', { sql, tableName }),
  // 环境流转
  submitChangeTask: (data: any) => api.post('/ddl-workbench/change-tasks/submit', data),
  listChangeTasks: (environment: string, limit = 20) =>
    api.get('/ddl-workbench/change-tasks', { params: { environment, limit } }),
  executeChangeTask: (id: string) => api.post(`/ddl-workbench/change-tasks/${id}/execute`),
  skipChangeTask: (id: string) => api.post(`/ddl-workbench/change-tasks/${id}/skip`),
  // 三阶段流水线新增
  rollbackToDev: (id: string) => api.post(`/ddl-workbench/change-tasks/${id}/rollback-to-dev`),
  getSourceTaskDetail: (id: string) => api.get(`/ddl-workbench/change-tasks/${id}/source-detail`),
  // ========== 项目工单管理 ==========
  createProject: (data: any) => api.post('/ddl-workbench/projects', data),
  getProject: (id: string) => api.get(`/ddl-workbench/projects/${id}`),
  listProjects: (params?: any) => api.get('/ddl-workbench/projects', { params }),
  updateProject: (id: string, data: any) => api.put(`/ddl-workbench/projects/${id}`, data),
  closeProject: (id: string) => api.post(`/ddl-workbench/projects/${id}/close`),
  advanceStage: (id: string) => api.post(`/ddl-workbench/projects/${id}/advance-stage`),
  // 项目表变更管理
  addProjectTable: (projectId: string, data: any) => api.post(`/ddl-workbench/projects/${projectId}/tables`, data),
  listProjectTables: (projectId: string) => api.get(`/ddl-workbench/projects/${projectId}/tables`),
  getProjectTable: (projectId: string, tableId: string) => api.get(`/ddl-workbench/projects/${projectId}/tables/${tableId}`),
  updateProjectTable: (projectId: string, tableId: string, data: any) => api.put(`/ddl-workbench/projects/${projectId}/tables/${tableId}`, data),
  deleteProjectTable: (projectId: string, tableId: string) => api.delete(`/ddl-workbench/projects/${projectId}/tables/${tableId}`),
  previewProjectSql: (projectId: string) => api.get(`/ddl-workbench/projects/${projectId}/preview-sql`),
  importSql: (projectId: string, sql: string) => api.post(`/ddl-workbench/projects/${projectId}/import-sql`, { sql }),
  executeProjectTable: (projectId: string, tableId: string) => api.post(`/ddl-workbench/projects/${projectId}/tables/${tableId}/execute`),
  executeAllProjectTables: (projectId: string) => api.post(`/ddl-workbench/projects/${projectId}/execute-all`),
};

// ============ 数据导入 ============
export const importApi = {
  preview: (file: File, format: string = 'csv') => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post(`/import/preview?format=${format}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  execute: (params: { file: File; instanceId: string; tableName: string; schemaName?: string; format?: string; batchSize?: number; columnMapping?: string; createTable?: boolean; truncateFirst?: boolean }) => {
    const formData = new FormData();
    formData.append('file', params.file);
    const queryParams = new URLSearchParams();
    queryParams.set('instanceId', params.instanceId);
    queryParams.set('tableName', params.tableName);
    if (params.schemaName) queryParams.set('schemaName', params.schemaName);
    queryParams.set('format', params.format || 'csv');
    queryParams.set('batchSize', String(params.batchSize || 500));
    if (params.columnMapping) queryParams.set('columnMapping', params.columnMapping);
    if (params.createTable) queryParams.set('createTable', 'true');
    if (params.truncateFirst) queryParams.set('truncateFirst', String(params.truncateFirst));
    return api.post(`/import/execute?${queryParams.toString()}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },
  formats: () => api.get('/import/formats'),
};

// ============ 权限管理 ============
// @deprecated 请使用 instanceApi 代替
export const databaseApi = {
  list: instanceApi.list,
  get: instanceApi.get,
  create: instanceApi.create,
  update: instanceApi.update,
  delete: instanceApi.delete,
  test: instanceApi.test,
  getDatabases: instanceApi.getSchemas,
  getTableNames: instanceApi.getTableNames,
  getCreateTableSql: instanceApi.getCreateTableSql,
  batchGetCreateTableSql: instanceApi.batchGetCreateTableSql,
  getSchema: instanceApi.getSchema,
  getBrowserSchema: instanceApi.getBrowserSchema,
  getTableDetail: instanceApi.getTableDetail,
};

// ============ 角色管理 ============
export const roleApi = {
  list: (params?: any) => api.get('/roles', { params }),
  listAll: () => api.get('/roles/all'),
  get: (id: string) => api.get(`/roles/${id}`),
  create: (data: any) => api.post('/roles', data),
  update: (id: string, data: any) => api.put(`/roles/${id}`, data),
  delete: (id: string) => api.delete(`/roles/${id}`),
  assignPermissions: (roleId: string, permissionIds: string[]) => 
    api.post(`/roles/${roleId}/permissions`, permissionIds),
  getPermissions: (roleId: string) => api.get(`/roles/${roleId}/permissions`),
  getPermissionDetails: (roleId: string) => api.get(`/roles/${roleId}/permissions/details`),
  removePermission: (roleId: string, permissionId: string) => 
    api.delete(`/roles/${roleId}/permissions/${permissionId}`),
  getUsers: (roleId: string) => api.get(`/roles/${roleId}/users`),
};

// ============ 导出 ============
export const exportApi = {
  csv: (data: any) => api.post('/export/csv', data, { responseType: 'blob' }),
  excel: (data: any) => api.post('/export/excel', data, { responseType: 'blob' }),
  json: (data: any) => api.post('/export/json', data, { responseType: 'blob' }),
  sql: (data: any) => api.post('/export/sql', data, { responseType: 'blob' }),
  auditLog: (format: string) => api.get(`/export/audit?format=${format}`, { responseType: 'blob' }),
};

// ============ SQL保存查询/执行历史 ============
export const savedQueryApi = {
  list: () => Promise.resolve({ data: { data: [], success: true } }), // 前端本地存储
};

// ============ DDL部署流水线 ============
export const pipelineApi = {
  list: (page = 1, size = 10) => api.get(`/pipelines?page=${page}&size=${size}`),
  get: (id: string) => api.get(`/pipelines/${id}`),
  create: (data: any) => api.post('/pipelines', data),
  delete: (id: string) => api.delete(`/pipelines/${id}`),
  
  // 执行记录
  startExecution: (data: any) => api.post('/pipelines/executions', data),
  listExecutions: (pipelineId?: string, page = 1, size = 10) => 
    api.get(`/pipelines/executions?pipelineId=${pipelineId || ''}&page=${page}&size=${size}`),
  getExecution: (id: string) => api.get(`/pipelines/executions/${id}`),
  cancelExecution: (id: string) => api.post(`/pipelines/executions/${id}/cancel`),
  
  // 阶段操作
  executeStage: (id: string) => api.post(`/pipelines/stage-executions/${id}/execute`),
  approveStage: (id: string, comment: string) => 
    api.post(`/pipelines/stage-executions/${id}/approve`, { comment }),
  rejectStage: (id: string, comment: string) => 
    api.post(`/pipelines/stage-executions/${id}/reject`, { comment }),
  rollbackStage: (id: string) => api.post(`/pipelines/stage-executions/${id}/rollback`),
};

// ============ 菜单管理 ============
export const menuApi = {
  getTree: () => api.get('/menus/tree'),
  getUserTree: () => api.get('/menus/user-tree'),
  get: (id: string) => api.get(`/menus/${id}`),
  create: (data: any) => api.post('/menus', data),
  update: (id: string, data: any) => api.put(`/menus/${id}`, data),
  delete: (id: string) => api.delete(`/menus/${id}`),
  getRoleMenus: (roleId: string) => api.get(`/menus/roles/${roleId}`),
  assignRoleMenus: (roleId: string, menuIds: string[]) =>
    api.post(`/menus/roles/${roleId}`, menuIds),
};

export default api;