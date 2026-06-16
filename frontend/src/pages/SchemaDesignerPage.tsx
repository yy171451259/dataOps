import React, { useState, useEffect } from 'react';
import {
  Card, Button, Input, Select, Modal, Form, Table, message, Space, Row, Col, Tag,
  Divider, Popconfirm, Tabs, Alert, Empty, Spin, Steps, Descriptions, Switch,
  InputNumber, Dropdown, Tooltip, Badge,
} from 'antd';
import {
  PlusOutlined, DatabaseOutlined, TableOutlined, KeyOutlined,
  SettingOutlined, ThunderboltOutlined, EditOutlined,
  ReloadOutlined, SendOutlined,
  PlayCircleOutlined, CheckCircleOutlined,
  RollbackOutlined, ImportOutlined,
  EyeOutlined, DeleteOutlined, CopyOutlined, FileTextOutlined,
  BranchesOutlined, UnorderedListOutlined,
  HistoryOutlined, StopOutlined, DownOutlined,
  AppstoreOutlined, ArrowRightOutlined,
} from '@ant-design/icons';
import { instanceApi, ddlWorkbenchApi, userApi } from '../utils/api';
import { useAuthStore } from '../store/useAuthStore';

const { Option } = Select;
const { TextArea } = Input;
const { Step } = Steps;

// ========== Types ==========
interface ColumnDef {
  id: string; name: string; type: string; length?: number;
  nullable: boolean; primaryKey: boolean; autoIncrement: boolean;
  unique: boolean; unsigned?: boolean; default?: string; comment?: string;
  isNew?: boolean;
}

interface ProjectTable {
  id: string; tableName: string; changeType: 'NEW' | 'MODIFY';
  originalDdl?: string; modifiedDdl?: string; changeSql?: string;
  version: number; lastOperator?: string; lastModifiedAt?: string;
  envStatus?: string; envDetails?: string;
  createdAt?: string; updatedAt?: string;
}

const ENV_STAGES = ['dev', 'test', 'integration', 'staging', 'production'] as const;
const ENV_LABELS: Record<string, string> = {
  dev: '开发环墀', test: '测试环境', integration: '集成环境', staging: '预发布环墀', production: '生产环境',
};
const ENV_COLORS: Record<string, string> = {
  dev: '#1890ff', test: '#52c41a', integration: '#faad14', staging: '#722ed1', production: '#f5222d',
};

const genId = () => Math.random().toString(36).substring(2, 10);
const COLUMN_TYPES = ['VARCHAR','INT','BIGINT','TINYINT','CHAR','TEXT','LONGTEXT','DECIMAL','FLOAT','DOUBLE','DATE','DATETIME','TIMESTAMP','BLOB','JSON'];

const SchemaDesignerPage: React.FC = () => {
  // ========== View State ==========
  const [viewMode, setViewMode] = useState<'list' | 'project'>('list');
  const [currentStep, setCurrentStep] = useState(0); // 0=create, 1=design, 2=publish, 3=finish

  // ========== Project List ==========
  const [projectList, setProjectList] = useState<any[]>([]);
  const [loadingProjects, setLoadingProjects] = useState(false);
  const [filterKeyword, setFilterKeyword] = useState('');
  const [filterStatus, setFilterStatus] = useState('');

  // ========== Project State ==========
  const [project, setProject] = useState<any>(null);
  const [projectTables, setProjectTables] = useState<ProjectTable[]>([]);
  const [selectedTableId, setSelectedTableId] = useState<string>('');
  const [loadingTables, setLoadingTables] = useState(false);

  // ========== Schema ==========
  const [databaseList, setDatabaseList] = useState<any[]>([]);
  const [schemaList, setSchemaList] = useState<string[]>([]);
  const [loadingSchemas, setLoadingSchemas] = useState(false);
  const [tableNames, setTableNames] = useState<string[]>([]);

  // ========== Table Editor ==========
  const [, setEditMode] = useState<'fields' | 'sql'>('fields');
  const [columns, setColumns] = useState<ColumnDef[]>([]);
  const [tableMeta, setTableMeta] = useState({ name: '', comment: '', engine: 'InnoDB', charset: 'utf8mb4' });
  const [columnModalVisible, setColumnModalVisible] = useState(false);
  const [editingColumn, setEditingColumn] = useState<ColumnDef | null>(null);
  const [editForm] = Form.useForm();

  // ========== SQL Import ==========
  const [sqlImportModalVisible, setSqlImportModalVisible] = useState(false);
  const [importSqlText, setImportSqlText] = useState('');

  // ========== Add Existing Table ==========
  const [addTableModalVisible, setAddTableModalVisible] = useState(false);
  const [selectedSchemaForAdd, setSelectedSchemaForAdd] = useState('');
  const [loadingAddTables] = useState(false);

  // ========== SQL Preview ==========
  const [sqlPreviewVisible, setSqlPreviewVisible] = useState(false);
  const [previewSqlContent, setPreviewSqlContent] = useState('');

  // ========== Execution ==========
  const [executing, setExecuting] = useState(false);
  const [executionHistory, setExecutionHistory] = useState<any[]>([]);
  const [activeTab, setActiveTab] = useState<'tables' | 'history'>('tables');

  // ========== Env Publish ==========
  // env tasks managed via projectTables envStatus
  const [submitEnvModalVisible, setSubmitEnvModalVisible] = useState(false);
  const [submitEnvTarget, setSubmitEnvTarget] = useState('');
  const [submitEnvDbId, setSubmitEnvDbId] = useState('');
  const [submittingEnv, setSubmittingEnv] = useState(false);

  // ========== Risk ==========
  // risk assessment integrated into project table flow

  // ========== Create Project Form ==========
  const [createForm] = Form.useForm();

  // ========== User List ==========
  const currentUser = useAuthStore(s => s.user);
  const [userList, setUserList] = useState<any[]>([]);

  // ========== Init ==========
  useEffect(() => { loadDatabaseList(); loadProjectList(); loadUserList(); }, []);

  const loadUserList = async () => {
    try {
      const res = await userApi.list();
      setUserList(res.data?.data?.records || res.data?.data || []);
    } catch (e) { console.error(e); }
  };

  const loadProjectList = async () => {
    setLoadingProjects(true);
    try {
      const params: any = {};
      if (filterStatus) params.status = filterStatus;
      if (filterKeyword) params.keyword = filterKeyword;
      const res = await ddlWorkbenchApi.listProjects(params);
      setProjectList(res.data?.data || []);
    } catch (e) { console.error(e); }
    finally { setLoadingProjects(false); }
  };

  const loadDatabaseList = async () => {
    try {
      const res = await instanceApi.list();
      setDatabaseList(res.data?.data?.records || res.data?.data || []);
    } catch (e) { console.error(e); }
  };

  const loadSchemas = async (dbId: string) => {
    if (!dbId) { setSchemaList([]); return; }
    setLoadingSchemas(true);
    try {
      const res = await instanceApi.getSchemas(dbId);
      setSchemaList(res.data?.data || []);
    } catch (e) { message.error('OK'); setSchemaList([]); }
    finally { setLoadingSchemas(false); }
  };

  const loadTableNames = async (dbId: string, schema: string) => {
    if (!dbId || !schema) { setTableNames([]); return; }
    try {
      const res = await instanceApi.getTableNames(dbId, schema);
      setTableNames(res.data?.data || []);
    } catch (e) { setTableNames([]); }
  };

  // ========== Project CRUD ==========
  const handleCreateProject = async () => {
    try {
      const values = await createForm.validateFields();
      const res = await ddlWorkbenchApi.createProject({
        projectName: values.projectName,
        businessBackground: values.businessBackground,
        baseDatabaseId: values.baseDatabaseId,
        baseSchemaName: values.baseSchemaName,
        priority: values.priority || 'normal',
        owner: currentUser?.username,
        relatedPersons: Array.isArray(values.relatedPersons) ? values.relatedPersons.join(',') : values.relatedPersons,
      });
      if (res.data?.data) {
        message.success('OK');
        setProject(res.data.data);
        setViewMode('project');
        setCurrentStep(1);
        loadProjectTables(res.data.data.id);
      }
    } catch (e: any) {
      if (e.response?.data) message.error(e.response.data.message || '创建失败');
    }
  };

  const openProject = async (p: any) => {
    setProject(p);
    setViewMode('project');
    // Determine step from project stage
    const stageMap: Record<string, number> = { CREATE: 0, DESIGN: 1, DEV: 1, TEST: 1, INTEGRATION: 2, STAGING: 3, PRODUCTION: 4, FINISH: 5 };
    setCurrentStep(stageMap[p.currentStage] ?? 0);
    loadProjectTables(p.id);
  };

  const loadProjectTables = async (projectId: string) => {
    setLoadingTables(true);
    try {
      const res = await ddlWorkbenchApi.listProjectTables(projectId);
      setProjectTables(res.data?.data || []);
    } catch (e) { console.error(e); }
    finally { setLoadingTables(false); }
  };

  const refreshProject = async () => {
    if (!project) return;
    try {
      const res = await ddlWorkbenchApi.getProject(project.id);
      if (res.data?.data) setProject(res.data.data);
    } catch (e) {}
  };

  const handleAdvanceStage = async () => {
    if (!project) return;
    try {
      const res = await ddlWorkbenchApi.advanceStage(project.id);
      if (res.data?.data) {
        setProject(res.data.data);
        const stageMap: Record<string, number> = { CREATE: 0, DESIGN: 1, DEV: 1, TEST: 1, INTEGRATION: 2, STAGING: 3, PRODUCTION: 4, FINISH: 5 };
        setCurrentStep(stageMap[res.data.data.currentStage] ?? currentStep);
        message.success('OK');
      }
    } catch (e: any) { message.error(e.response?.data?.message || '推进失败'); }
  };

  const handleCloseProject = async () => {
    if (!project) return;
    try {
      await ddlWorkbenchApi.closeProject(project.id);
      message.success('OK');
      setViewMode('list');
      loadProjectList();
    } catch (e: any) { message.error('OK'); }
  };

  // ========== Table Management ==========
  const handleAddNewTable = () => {
    setEditMode('fields');
    setColumns([{ id: genId(), name: 'id', type: 'BIGINT', length: undefined, nullable: false, primaryKey: true, autoIncrement: true, unique: false, unsigned: true, comment: '主键ID', isNew: true }]);
    setTableMeta({ name: '', comment: '', engine: 'InnoDB', charset: 'utf8mb4' });
    setSelectedTableId('__new__');
  };

  const handleAddExistingTable = async (tableName: string) => {
    if (!project) return;
    try {
      const dbId = project.baseDatabaseId;
      const schema = project.baseSchemaName;
      const res = await instanceApi.getCreateTableSql(dbId, tableName, schema);
      if (res.data?.data) {
        await ddlWorkbenchApi.addProjectTable(project.id, {
          tableName, changeType: 'MODIFY', originalDdl: res.data.data, modifiedDdl: res.data.data,
        });
        message.success("OK");
        loadProjectTables(project.id);
        setAddTableModalVisible(false);
      }
    } catch (e: any) { message.error('OK'); return; }
    if (columns.length === 0) { message.warning('OK'); return; }
    const ddl = generateCreateTableDdl();
    try {
      await ddlWorkbenchApi.addProjectTable(project.id, {
        tableName: tableMeta.name, changeType: 'NEW', modifiedDdl: ddl, changeSql: ddl,
      });
      message.success('OK');
      setSelectedTableId('');
      loadProjectTables(project.id);
    } catch (e: any) { message.error('OK'); }
  };

  const handleDeleteTable = async (tableId: string) => {
    if (!project) return;
    try {
      await ddlWorkbenchApi.deleteProjectTable(project.id, tableId);
      message.success('OK');
      if (selectedTableId === tableId) setSelectedTableId('');
      loadProjectTables(project.id);
    } catch (e: any) { message.error('OK'); }
  };

  const handleSelectTable = async (pt: ProjectTable) => {
    setSelectedTableId(pt.id);
    if (pt.changeType === 'NEW') {
      setEditMode('fields');
      const parsed = parseCreateTableSql(pt.modifiedDdl || '');
      if (parsed) {
        setColumns(parsed.columns);
        setTableMeta({ name: parsed.name, comment: parsed.comment || '', engine: parsed.engine || 'InnoDB', charset: parsed.charset || 'utf8mb4' });
      }
    } else {
      setEditMode('sql');
      setColumns([]);
      setTableMeta({ name: pt.tableName, comment: '', engine: 'InnoDB', charset: 'utf8mb4' });
    }
  };

  // ========== Field Editor ==========
  const addColumn = () => {
    setColumns([...columns, {
      id: genId(), name: `col_${columns.length + 1}`, type: 'VARCHAR', length: 255,
      nullable: true, primaryKey: false, autoIncrement: false, unique: false, comment: '', isNew: true,
    }]);
  };

  const openColumnEdit = (col: ColumnDef) => {
    setEditingColumn(col);
    editForm.setFieldsValue({ name: col.name, type: col.type, length: col.length, nullable: col.nullable, primaryKey: col.primaryKey, autoIncrement: col.autoIncrement, unsigned: col.unsigned, defaultValue: col.default, comment: col.comment });
    setColumnModalVisible(true);
  };

  const saveColumnEdit = async () => {
    if (!editingColumn) return;
    try {
      const v = await editForm.validateFields();
      setColumns(prev => prev.map(c => c.id === editingColumn.id ? {
        ...c, name: v.name, type: v.type,
        length: ['VARCHAR','CHAR','INT','BIGINT'].includes(v.type) ? v.length : undefined,
        nullable: v.nullable, primaryKey: v.primaryKey || false,
        autoIncrement: v.autoIncrement || false, unsigned: v.unsigned || false,
        default: v.defaultValue, comment: v.comment || '',
      } : c));
      setColumnModalVisible(false);
    } catch (e) {}
  };

  const removeColumn = (id: string) => setColumns(prev => prev.filter(c => c.id !== id));

  const generateCreateTableDdl = () => {
    const pkCols = columns.filter(c => c.primaryKey).map(c => c.name);
    const lines = [`CREATE TABLE \`${tableMeta.name}\` (`];
    columns.forEach((col, i) => {
      const typeStr = col.length ? `${col.type}(${col.length})` : col.type;
      const unsign = col.unsigned ? ' UNSIGNED' : '';
      const nn = col.nullable ? '' : ' NOT NULL';
      const ai = col.autoIncrement ? ' AUTO_INCREMENT' : '';
      const def = col.default ? ` DEFAULT ${col.default}` : '';
      const cmt = col.comment ? ` COMMENT '${col.comment}'` : '';
      lines.push(`  \`${col.name}\` ${typeStr}${unsign}${nn}${ai}${def}${cmt}${i < columns.length - 1 ? ',' : ''}`);
    });
    if (pkCols.length > 0) lines.push(`  PRIMARY KEY (${pkCols.map(c => `\`${c}\``).join(', ')})`);
    lines.push(`) ENGINE=${tableMeta.engine} DEFAULT CHARSET=${tableMeta.charset}${tableMeta.comment ? ` COMMENT='${tableMeta.comment}'` : ''};`);
    return lines.join('\n');
  };

  const handleSaveTableDesign = async () => {
    if (!project || !selectedTableId || selectedTableId === '__new__') { handleSaveNewTable(); return; }
    const pt = projectTables.find(t => t.id === selectedTableId);
    if (!pt) return;
    const ddl = generateCreateTableDdl();
    try {
      await ddlWorkbenchApi.updateProjectTable(project.id, selectedTableId, { modifiedDdl: ddl, changeSql: ddl });
      message.success('OK');
      loadProjectTables(project.id);
    } catch (e: any) { message.error('OK'); }
  };

  const handleSaveSqlEdit = async (sql: string) => {
    if (!project || !selectedTableId) return;
    try {
      await ddlWorkbenchApi.updateProjectTable(project.id, selectedTableId, { changeSql: sql, modifiedDdl: sql });
      message.success('OK');
      loadProjectTables(project.id);
    } catch (e: any) { message.error('OK'); }
  };

  // ========== SQL Import ==========
  const handleImportSql = async () => {
    if (!project || !importSqlText.trim()) { message.warning('OK'); return; }
    try {
      const res = await ddlWorkbenchApi.importSql(project.id, importSqlText);
      if (res.data?.data) {
        message.success(`Import done: ${res.data.data.imported} added, ${res.data.data.skipped} skipped`);
        loadProjectTables(project.id);
        setSqlImportModalVisible(false);
        setImportSqlText('');
      }
    } catch (e: any) { message.error('OK'); }
  };

  // ========== Execution ==========
  const handleExecuteTable = async (tableId: string) => {
    if (!project) return;
    setExecuting(true);
    try {
      const res = await ddlWorkbenchApi.executeProjectTable(project.id, tableId);
      if (res.data?.data) {
        message.success('OK');
        loadProjectTables(project.id);
        addToHistory({ tableName: res.data.data.tableName, status: 'SUCCESS', time: new Date().toLocaleString() });
      }
    } catch (e: any) {
      message.error('OK'); }
    finally { setExecuting(false); }
  };

  const addToHistory = (entry: any) => setExecutionHistory(prev => [entry, ...prev]);

  // ========== Batch Preview SQL ==========
  const handleBatchPreviewSql = async () => {
    if (!project) return;
    try {
      const res = await ddlWorkbenchApi.previewProjectSql(project.id);
      if (res.data?.data) {
        setPreviewSqlContent(res.data.data.sql);
        setSqlPreviewVisible(true);
      }
    } catch (e: any) { message.error('OK'); }
  };

  // ========== Env Publish ==========
  const handleSubmitToEnv = async () => {
    if (!project || !submitEnvTarget || !submitEnvDbId) { message.warning('OK'); return; }
    setSubmittingEnv(true);
    try {
      const sqlRes = await ddlWorkbenchApi.previewProjectSql(project.id);
      const sql = sqlRes.data?.data?.sql || '';
      await ddlWorkbenchApi.submitChangeTask({
        title: `${project.projectName} DDL变更`,
        environment: submitEnvTarget.toUpperCase(),
        databaseInstanceId: submitEnvDbId,
        databaseName: project.baseSchemaName,
        sqlContent: sql,
      });
      message.success("OK");
      setSubmitEnvModalVisible(false);
    } catch (e: any) { message.error('OK'); }
    finally { setSubmittingEnv(false); }
  };

  // ========== Parse CREATE TABLE SQL ==========
  const parseCreateTableSql = (sql: string) => {
    try {
      const nameMatch = sql.match(/CREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?`?(\w+)`?/i);
      if (!nameMatch) return null;
      const cols: ColumnDef[] = [];
      const innerPart = sql.match(/\(([\s\S]*)\)/)?.[1];
      if (innerPart) {
        innerPart.split(/,\s*(?=`|\n|PRIMARY|UNIQUE|KEY|INDEX|CONSTRAINT|FOREIGN)/i).forEach(line => {
          line = line.trim();
          if (!line || /^(PRIMARY|UNIQUE|KEY|INDEX|CONSTRAINT|FOREIGN)/i.test(line)) return;
          const m = line.match(/^`?(\w+)`?\s+(\w+)(?:\(([^)]+)\))?(.*)$/i);
          if (m) {
            const [, n, t, len, rest] = m;
            cols.push({
              id: genId(), name: n, type: t.toUpperCase(),
              length: len && !isNaN(parseInt(len)) ? parseInt(len) : undefined,
              nullable: !/NOT\s+NULL/i.test(rest), primaryKey: /PRIMARY\s+KEY/i.test(rest),
              autoIncrement: /AUTO_INCREMENT/i.test(rest), unique: /UNIQUE/i.test(rest),
              unsigned: /UNSIGNED/i.test(rest),
              comment: rest.match(/COMMENT\s+'([^']+)'/i)?.[1] || '',
              default: rest.match(/DEFAULT\s+'?([^',\s]+)'?/i)?.[1] || undefined,
              isNew: false,
            });
          }
        });
      }
      return { name: nameMatch[1], columns: cols, comment: sql.match(/COMMENT='([^']+)'/i)?.[1] || '', engine: sql.match(/ENGINE=(\w+)/i)?.[1] || 'InnoDB', charset: sql.match(/CHARSET=(\w+)/i)?.[1] || 'utf8mb4' };
    } catch (e) { return null; }
  };

  // ========== Status helpers ==========
  const parseEnvStatus = (s?: string): Record<string, string> => {
    try { return s ? JSON.parse(s) : {}; } catch { return {}; }
  };
  const envStatusTag = (status: string) => {
    const map: Record<string, { color: string; label: string }> = {
      NONE: { color: 'default', label: '-' }, PENDING: { color: 'processing', label: '待执血' },
      SUCCESS: { color: 'success', label: '成功' }, FAILED: { color: 'error', label: '失败' },
    };
    const cfg = map[status] || { color: 'default', label: status };
    return <Tag color={cfg.color}>{cfg.label}</Tag>;
  };
  const projectStatusTag = (s: string) => {
    const map: Record<string, { color: string; label: string }> = {
      DESIGNING: { color: 'processing', label: '设计中' }, DEV_EXECUTING: { color: 'cyan', label: '开发执行中' },
      DEV_DONE: { color: 'blue', label: '开发完成' }, PUBLISHED: { color: 'green', label: '已发布' },
      CLOSED: { color: 'default', label: '已关闭' },
    };
    const cfg = map[s] || { color: 'default', label: s };
    return <Tag color={cfg.color}>{cfg.label}</Tag>;
  };

  // ========== Render: Project List ==========
  const renderProjectList = () => (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Card size="small" style={{ marginBottom: 12, flexShrink: 0 }}>
        <Row gutter={16} align="middle">
          <Col><span style={{ fontWeight: 600, fontSize: 16 }}>结构设计工单列表</span></Col>
          <Col flex="auto">
            <Space>
              <Input.Search placeholder="搜索项目名称/背景" allowClear style={{ width: 280 }}
                value={filterKeyword} onChange={e => setFilterKeyword(e.target.value)} onSearch={loadProjectList} />
              <Select style={{ width: 140 }} value={filterStatus} onChange={v => { setFilterStatus(v); setTimeout(loadProjectList, 100); }} allowClear placeholder="筛选状态">
                <Option value="DESIGNING">设计中</Option>
                <Option value="DEV_DONE">开发完成</Option>
                <Option value="PUBLISHED">已发布</Option>
                <Option value="CLOSED">已关闭</Option>
              </Select>
            </Space>
          </Col>
          <Col>
            <Space>
              <Button icon={<ReloadOutlined />} onClick={loadProjectList}>刷新</Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={() => { createForm.resetFields(); setViewMode('project'); setCurrentStep(0); setProject(null); setProjectTables([]); }}>新建工单</Button>
            </Space>
          </Col>
        </Row>
      </Card>
      <div style={{ flex: 1, overflow: 'auto' }}>
        <Spin spinning={loadingProjects}>
          {projectList.length === 0 ? <Empty description="暂无项目工单" style={{ marginTop: 80 }} /> : (
            <Table dataSource={projectList} rowKey="id" size="small" pagination={{ pageSize: 20 }}
              onRow={(r) => ({ onClick: () => openProject(r), style: { cursor: 'pointer' } })}
              columns={[
                { title: '项目名称', dataIndex: 'projectName', width: 200, render: (t: string, r: any) => (<Space><AppstoreOutlined style={{ color: '#1890ff' }} /><span style={{ fontWeight: 500 }}>{t}</span>{projectStatusTag(r.status)}</Space>) },
                { title: '基准实例 / Schema', dataIndex: 'baseDatabaseName', width: 180, render: (t: string, r: any) => <span>{t} / {r.baseSchemaName}</span> },
                { title: '业务背景', dataIndex: 'businessBackground', ellipsis: true, width: 250 },
                { title: '变更表数', dataIndex: 'tableCount', width: 80, align: 'center', render: (v: number) => <Badge count={v} showZero overflowCount={99} style={{ backgroundColor: v > 0 ? '#1890ff' : '#d9d9d9' }} /> },
                { title: '当前阶段', dataIndex: 'currentStage', width: 100, render: (s: string) => {
                  const map: Record<string, string> = { CREATE: '创建', DESIGN: '设计', DEV: '开发', TEST: '测试', INTEGRATION: '集成', STAGING: '预发', PRODUCTION: '生产', FINISH: '完成' };
                  return <Tag>{map[s] || s}</Tag>;
                }},
                { title: '负责人', dataIndex: 'owner', width: 100 },
                { title: '创建时间', dataIndex: 'createdAt', width: 160 },
                { title: '操作', width: 120, fixed: 'right' as const, render: (_: any, r: any) => (
                  <Space size="small" onClick={e => e.stopPropagation()}>
                    <Button type="link" size="small" onClick={() => openProject(r)}>打开</Button>
                    {r.status !== 'CLOSED' && <Popconfirm title="确定关闭" onConfirm={async () => { await ddlWorkbenchApi.closeProject(r.id); message.success('OK'); loadProjectList(); }}><Button type="link" size="small" danger>关闭</Button></Popconfirm>}
                  </Space>
                )},
              ]} />
          )}
        </Spin>
      </div>
    </div>
  );

  // ========== Render: Step 0 - Create Project ==========
  const renderCreateStep = () => (
    <div style={{ maxWidth: 700, margin: '40px auto', padding: '0 24px' }}>
      <Card title={<Space><FileTextOutlined /><span>创建结构设计工单</span></Space>}>
        <Form form={createForm} layout="vertical">
          <Form.Item name="projectName" label="项目名称" rules={[{ required: true, message: '请输入项目名称' }]}>
            <Input placeholder="请输入项目名称" />
          </Form.Item>
          <Form.Item name="businessBackground" label="项目背景">
            <TextArea rows={3} placeholder="请输入描述" />
          </Form.Item>
          <Form.Item name="baseDatabaseId" label="变更基准实例" rules={[{ required: true, message: '请选择基准实例' }]}
            extra={<span style={{ color: '#999' }}>提示: 有基准实例的任意权限即可提交工单</span>}>
            <Select placeholder="请选择" onChange={v => { loadSchemas(v); createForm.setFieldValue('baseSchemaName', ''); }}>
              {databaseList.map((db: any) => <Option key={db.id} value={db.id}><Space><DatabaseOutlined />{db.name} <span style={{ color: '#999', fontSize: 12 }}>({db.host}:{db.port})</span></Space></Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="baseSchemaName" label="目标Schema" rules={[{ required: true, message: '请选择Schema' }]}>
            <Select placeholder="请选择" loading={loadingSchemas}>
              {schemaList.map(s => <Option key={s} value={s}>{s}</Option>)}
            </Select>
          </Form.Item>
          <Form.Item name="priority" label="优先纀" initialValue="normal">
            <Select>
              <Option value="low">佀</Option><Option value="normal">普退</Option>
              <Option value="high">髀</Option><Option value="urgent">紧怀</Option>
            </Select>
          </Form.Item>
          <Form.Item name="relatedPersons" label="变更相关人员">
            <Select mode="multiple" placeholder="请选择变更相关库" allowClear
              filterOption={(input, option) => (option?.label as string)?.toLowerCase().includes(input.toLowerCase())}>
              {userList.map((u: any) => <Option key={u.username || u.id} value={u.username || u.id} label={u.nickname || u.username}>{u.nickname || u.username} ({u.username})</Option>)}
            </Select>
          </Form.Item>
        </Form>
        <Divider />
        <Row justify="end"><Space>
          <Button onClick={() => setViewMode('list')}>取消</Button>
          <Button type="primary" icon={<SendOutlined />} onClick={handleCreateProject}>提交</Button>
        </Space></Row>
      </Card>
    </div>
  );

  // ========== Render: Step 1 - Schema Design ==========
  const selectedPt = projectTables.find(t => t.id === selectedTableId);
  const newCount = projectTables.filter(t => t.changeType === 'NEW').length;
  const modifyCount = projectTables.filter(t => t.changeType === 'MODIFY').length;

  const renderDesignStep = () => (
    <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {/* Project Info Bar */}
      {project && renderProjectInfoBar()}
      {/* Toolbar */}
      <Card size="small" style={{ borderRadius: 0, borderBottom: 0, flexShrink: 0 }} bodyStyle={{ padding: '8px 16px' }}>
        <Row align="middle" gutter={12}>
          <Col>
            <Space>
              <Dropdown menu={{ items: [
                { key: 'new', label: '新建物理表', icon: <PlusOutlined />, onClick: handleAddNewTable },
                { key: 'add', label: '添加已有表', icon: <DatabaseOutlined />, onClick: () => { setSelectedSchemaForAdd(project?.baseSchemaName || ''); setAddTableModalVisible(true); loadTableNames(project?.baseDatabaseId, project?.baseSchemaName); } },
                { key: 'import', label: '导入SQL语句', icon: <ImportOutlined />, onClick: () => setSqlImportModalVisible(true) },
              ]}}>
                <Button type="primary" icon={<PlusOutlined />}>新建工单 <DownOutlined /></Button>
              </Dropdown>
            </Space>
          </Col>
          <Col><Divider type="vertical" style={{ height: 24 }} /></Col>
          <Col>
            <Space>
              <span style={{ fontSize: 13, color: '#666' }}>
                修改(<span style={{ color: '#fa8c16' }}>{modifyCount}</span>) 新增(<span style={{ color: '#52c41a' }}>{newCount}</span>)
              </span>
            </Space>
          </Col>
          <Col flex="auto" />
          <Col>
            <Space>
              <Button icon={<EyeOutlined />} onClick={handleBatchPreviewSql} disabled={projectTables.length === 0}>批量预览SQL</Button>
              <Popconfirm title="确定批量执行所有表变更到基准库" onConfirm={handleExecuteAll}>
                <Button icon={<PlayCircleOutlined />} loading={executing} disabled={projectTables.length === 0}>批量执行到基准库</Button>
              </Popconfirm>
              <Button type="primary" icon={<ArrowRightOutlined />} onClick={handleAdvanceStage}>进入下一节点</Button>
            </Space>
          </Col>
        </Row>
      </Card>
      {/* Main Content: Left table list + Right editor */}
      <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
        {/* Left: Table List */}
        <div style={{ width: 300, borderRight: '1px solid #f0f0f0', display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: '8px 12px', borderBottom: '1px solid #f0f0f0', background: '#fafafa' }}>
            <Tabs activeKey={activeTab} onChange={k => setActiveTab(k as any)} size="small">
              <Tabs.TabPane tab={<span><UnorderedListOutlined /> 项目编辑的表</span>} key="tables" />
              <Tabs.TabPane tab={<span><HistoryOutlined /> 变更执行历史</span>} key="history" />
            </Tabs>
          </div>
          {activeTab === 'tables' ? (
            <div style={{ flex: 1, overflow: 'auto' }}>
              <Spin spinning={loadingTables}>
                {projectTables.length === 0 ? <Empty description="暂无表，请添加" style={{ marginTop: 40 }} /> :
                  projectTables.map(pt => {
                    const es = parseEnvStatus(pt.envStatus);
                    return (
                      <div key={pt.id} onClick={() => handleSelectTable(pt)}
                        style={{
                          padding: '10px 14px', cursor: 'pointer', borderBottom: '1px solid #f0f0f0',
                          background: selectedTableId === pt.id ? '#e6f7ff' : '#fff',
                          borderLeft: selectedTableId === pt.id ? '3px solid #1890ff' : '3px solid transparent',
                        }}
                        onMouseEnter={e => { if (selectedTableId !== pt.id) e.currentTarget.style.background = '#fafafa'; }}
                        onMouseLeave={e => { if (selectedTableId !== pt.id) e.currentTarget.style.background = '#fff'; }}>
                        <Row align="middle" justify="space-between">
                          <Col>
                            <Space>
                              <TableOutlined style={{ color: pt.changeType === 'NEW' ? '#52c41a' : '#fa8c16', fontSize: 12 }} />
                              <span style={{ fontSize: 13, fontWeight: 500 }}>{pt.tableName}</span>
                              <Tag color={pt.changeType === 'NEW' ? 'green' : 'orange'} style={{ fontSize: 10 }}>{pt.changeType === 'NEW' ? '新建' : '修改'}</Tag>
                            </Space>
                          </Col>
                          <Col>
                            <Space size={2}>
                              <span style={{ fontSize: 11, color: '#999' }}>v{pt.version}</span>
                              <Popconfirm title="删除此表变更" onConfirm={() => handleDeleteTable(pt.id)}>
                                <Button type="text" size="small" danger icon={<DeleteOutlined />} style={{ fontSize: 10, padding: 0, width: 20, height: 20 }} />
                              </Popconfirm>
                            </Space>
                          </Col>
                        </Row>
                        <div style={{ marginTop: 4, display: 'flex', gap: 2 }}>
                          {ENV_STAGES.map(env => <Tooltip key={env} title={ENV_LABELS[env]}>{envStatusTag(es[env] || 'NONE')}</Tooltip>)}
                        </div>
                      </div>
                    );
                  })
                }
              </Spin>
            </div>
          ) : renderExecutionHistory()}
        </div>
        {/* Right: Editor */}
        <div style={{ flex: 1, overflow: 'auto', background: '#f5f5f5', padding: 16 }}>
          {!selectedTableId ? <div style={{ textAlign: 'center', padding: '80px 0' }}><TableOutlined style={{ fontSize: 64, color: '#d9d9d9' }} /><p style={{ color: '#999', marginTop: 16 }}>选择左侧表进行编辑，或添加新表</p></div> :
            selectedTableId === '__new__' || selectedPt?.changeType === 'NEW' ? renderFieldEditor() :
            renderSqlEditor()
          }
        </div>
      </div>
    </div>
  );

  const renderProjectInfoBar = () => (
    <Card size="small" style={{ borderRadius: 0, borderBottom: 0, flexShrink: 0 }} bodyStyle={{ padding: '6px 16px' }}>
      <Row align="middle" gutter={24}>
        <Col><Space><span style={{ fontWeight: 600 }}>【物理表】{project.projectName}</span>{projectStatusTag(project.status)}</Space></Col>
        <Col><span style={{ color: '#999', fontSize: 12 }}>ID: #{project.id?.substring(0, 8)}</span></Col>
        <Col><span style={{ color: '#999', fontSize: 12 }}>Owner: {project.owner}</span></Col>
        <Col flex="auto"><span style={{ color: '#999', fontSize: 12 }}>业务背景: <EditOutlined style={{ cursor: 'pointer', marginLeft: 4 }} /> {project.businessBackground || '-'}</span></Col>
        <Col><Space size="small">
          <Tooltip title="工单详情"><Button size="small" icon={<FileTextOutlined />} onClick={refreshProject} /></Tooltip>
          <Tooltip title="操作日志"><Button size="small" icon={<HistoryOutlined />} /></Tooltip>
          <Popconfirm title="确定关闭此工单？" onConfirm={handleCloseProject}><Tooltip title="关闭工单"><Button size="small" icon={<StopOutlined />} danger /></Tooltip></Popconfirm>
        </Space></Col>
      </Row>
    </Card>
  );

  const renderFieldEditor = () => (
    <Card size="small" title={<Space><SettingOutlined /><span>{selectedTableId === '__new__' ? '新建表设计' : `表结构设讀- ${selectedPt?.tableName}`}</span></Space>}
      extra={<Space>
        {selectedTableId === '__new__' && <Input placeholder="表名" value={tableMeta.name} onChange={e => setTableMeta({ ...tableMeta, name: e.target.value })} style={{ width: 160 }} />}
        {selectedTableId === '__new__' && <Input placeholder="注释" value={tableMeta.comment} onChange={e => setTableMeta({ ...tableMeta, comment: e.target.value })} style={{ width: 120 }} />}
        <Button type="primary" size="small" icon={<PlusOutlined />} onClick={addColumn}>添加字段</Button>
        <Button size="small" type="primary" icon={<ThunderboltOutlined />} onClick={handleSaveTableDesign}>保存</Button>
      </Space>}>
      {selectedTableId !== '__new__' && (
        <Row gutter={16} style={{ marginBottom: 12 }}>
          <Col span={6}><Input addonBefore="表名" value={tableMeta.name} onChange={e => setTableMeta({ ...tableMeta, name: e.target.value })} /></Col>
          <Col span={6}><Input addonBefore="注释" value={tableMeta.comment} onChange={e => setTableMeta({ ...tableMeta, comment: e.target.value })} /></Col>
          <Col span={4}>
            <Select value={tableMeta.charset} onChange={v => setTableMeta({ ...tableMeta, charset: v })} style={{ width: '100%' }}>
              <Option value="utf8mb4">utf8mb4</Option><Option value="utf8">utf8</Option><Option value="latin1">latin1</Option>
            </Select>
          </Col>
        </Row>
      )}
      <Table dataSource={columns} rowKey="id" size="small" pagination={false}
        scroll={{ y: 'calc(100vh - 380px)' }}
        columns={[
          { title: '字段名', dataIndex: 'name', width: 140, render: (t: string, r: ColumnDef) => (<Space>{r.primaryKey && <KeyOutlined style={{ color: '#1890ff' }} />}<span>{t}</span></Space>) },
          { title: '类型', dataIndex: 'type', width: 80 },
          { title: '长度', dataIndex: 'length', width: 60 },
          { title: '可空', dataIndex: 'nullable', width: 60, render: (v: boolean) => v ? <Tag color="green">Y</Tag> : <Tag color="red">N</Tag> },
          { title: '主键', dataIndex: 'primaryKey', width: 50, render: (v: boolean) => v ? <Tag color="blue">PK</Tag> : '' },
          { title: '自增', dataIndex: 'autoIncrement', width: 50, render: (v: boolean) => v ? <Tag color="cyan">AI</Tag> : '' },
          { title: '注释', dataIndex: 'comment', ellipsis: true },
          { title: '操作', width: 120, fixed: 'right' as const, render: (_: any, r: ColumnDef) => (
            <Space size="small">
              <Button type="link" size="small" icon={<EditOutlined />} onClick={() => openColumnEdit(r)}>编辑</Button>
              <Popconfirm title="删除" onConfirm={() => removeColumn(r.id)}><Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button></Popconfirm>
            </Space>
          )},
        ]} />
      {selectedTableId === '__new__' && (
        <Alert message="提示: 字符集一旦保存，不可更改" type="info" showIcon style={{ marginTop: 8 }} />
      )}
    </Card>
  );

  const [sqlEditText, setSqlEditText] = useState('');
  const [sqlEditTableId, setSqlEditTableId] = useState('');

  const renderSqlEditor = () => {
    const pt = selectedPt;
    if (!pt) return null;
    const currentSql = sqlEditText !== undefined && selectedTableId === sqlEditTableId ? sqlEditText : (pt.changeSql || pt.modifiedDdl || '');
    return (
      <Card size="small" title={<Space><EditOutlined /><span>SQL编辑 - {pt.tableName}</span><Tag color="orange">{pt.changeType === 'NEW' ? '新建' : '修改'}</Tag></Space>}
        extra={<Space>
          <Button size="small" icon={<CopyOutlined />} onClick={() => { navigator.clipboard.writeText(currentSql); message.success('OK'); }}>复制SQL</Button>
          <Button size="small" icon={<ThunderboltOutlined />} onClick={() => { handleSaveSqlEdit(currentSql); setSqlEditText(''); setSqlEditTableId(''); }}>保存SQL</Button>
          <Button size="small" type="primary" icon={<PlayCircleOutlined />} onClick={() => handleExecuteTable(pt.id)} loading={executing}>执行到基准库</Button>
        </Space>}>
        <TextArea value={currentSql} onChange={e => { setSqlEditText(e.target.value); setSqlEditTableId(pt.id); }} rows={18}
          style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 12, background: '#fafafa' }} />
        <Divider />
        <Descriptions size="small" column={4}>
          <Descriptions.Item label="表名">{pt.tableName}</Descriptions.Item>
          <Descriptions.Item label="版本">v{pt.version}</Descriptions.Item>
          <Descriptions.Item label="最后操作人">{pt.lastOperator || '-'}</Descriptions.Item>
          <Descriptions.Item label="最近修改">{pt.lastModifiedAt || '-'}</Descriptions.Item>
        </Descriptions>
        <div style={{ marginTop: 8, display: 'flex', gap: 4 }}>
          {ENV_STAGES.map(env => <span key={env} style={{ fontSize: 12 }}>{ENV_LABELS[env]}: {envStatusTag(parseEnvStatus(pt.envStatus)[env] || 'NONE')}</span>)}
        </div>
      </Card>
    );
  };

  const renderExecutionHistory = () => (
    <div style={{ flex: 1, overflow: 'auto', padding: 8 }}>
      {executionHistory.length === 0 ? <Empty description="暂无执行记录" style={{ marginTop: 40 }} /> :
        executionHistory.map((h, i) => (
          <div key={i} style={{ padding: '8px 12px', borderBottom: '1px solid #f0f0f0', fontSize: 12 }}>
            <Space><Tag color={h.status === 'SUCCESS' ? 'green' : h.status === 'PARTIAL' ? 'orange' : 'red'}>{h.status}</Tag><span>{h.tableName}</span></Space>
            <div style={{ color: '#999', marginTop: 2 }}>{h.time}</div>
            {h.errors && h.errors.map((e: string, j: number) => <div key={j} style={{ color: '#ff4d4f', marginTop: 2 }}>Err: {e}</div>)}
          </div>
        ))
      }
    </div>
  );

  // ========== Render: Env Deploy Step ==========
  const ENV_DEPLOY_STEPS = ['dev', 'integration', 'staging', 'production'] as const;

  const renderEnvStep = (targetEnv: string) => (
    <div style={{ flex: 1, overflow: 'auto', padding: 24 }}>
      {project && renderProjectInfoBar()}
      <Card title={<Space><BranchesOutlined /><span>部署到{ENV_LABELS[targetEnv]}</span></Space>}
        style={{ marginTop: 12 }}
        extra={<Button type="primary" icon={<ArrowRightOutlined />} onClick={handleAdvanceStage}>推进到下一阶段</Button>}>
        {/* Env pipeline cards */}
        <Row gutter={12}>
          {ENV_DEPLOY_STEPS.map(env => {
            const isTarget = env === targetEnv;
            return (
              <Col key={env} span={6}>
                <Card size="small"
                  style={{ borderTop: `3px solid ${ENV_COLORS[env]}`, opacity: isTarget ? 1 : 0.55, boxShadow: isTarget ? '0 0 8px rgba(24,144,255,0.3)' : undefined }}
                  title={<span style={{ color: ENV_COLORS[env], fontWeight: isTarget ? 700 : 400 }}>{ENV_LABELS[env]}</span>}>
                  <Empty description="暂无任务" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                  <Button size="small" type="link" icon={<SendOutlined />}
                    disabled={!isTarget}
                    onClick={() => { setSubmitEnvTarget(env); setSubmitEnvDbId(''); setSubmitEnvModalVisible(true); }}>
                    {isTarget ? '提交到此环境' : ' '}
                  </Button>
                </Card>
              </Col>
            );
          })}
        </Row>
      </Card>
      {/* Batch SQL summary */}
      <Card title="变更SQL汇总" size="small" style={{ marginTop: 16 }} extra={<Button icon={<EyeOutlined />} onClick={handleBatchPreviewSql}>预览</Button>}>
        <Table dataSource={projectTables} rowKey="id" size="small" pagination={false}
          columns={[
            { title: '表名', dataIndex: 'tableName', width: 150 },
            { title: '类型', dataIndex: 'changeType', width: 80, render: (t: string) => <Tag color={t === 'NEW' ? 'green' : 'orange'}>{t === 'NEW' ? '新建' : '修改'}</Tag> },
            { title: '版本', dataIndex: 'version', width: 60, render: (v: number) => `v${v}` },
            { title: '开发', width: 60, render: (_: any, r: ProjectTable) => envStatusTag(parseEnvStatus(r.envStatus).dev || 'NONE') },
            { title: '测试', width: 60, render: (_: any, r: ProjectTable) => envStatusTag(parseEnvStatus(r.envStatus).test || 'NONE') },
            { title: '集成', width: 60, render: (_: any, r: ProjectTable) => envStatusTag(parseEnvStatus(r.envStatus).integration || 'NONE') },
            { title: '预发', width: 60, render: (_: any, r: ProjectTable) => envStatusTag(parseEnvStatus(r.envStatus).staging || 'NONE') },
            { title: '生产', width: 60, render: (_: any, r: ProjectTable) => envStatusTag(parseEnvStatus(r.envStatus).production || 'NONE') },
          ]} />
      </Card>
    </div>
  );

  // ========== Render: Step 3 - Finish ==========
  const renderFinishStep = () => (
    <div style={{ flex: 1, overflow: 'auto', padding: 24 }}>
      {project && renderProjectInfoBar()}
      <Card style={{ marginTop: 12 }}>
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <CheckCircleOutlined style={{ fontSize: 64, color: '#52c41a' }} />
          <h2 style={{ marginTop: 16 }}>项目工单已完成</h2>
          <p style={{ color: '#999' }}>{project?.projectName} - 兀{projectTables.length} 张表变更</p>
        </div>
        <Divider />
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="项目名称">{project?.projectName}</Descriptions.Item>
          <Descriptions.Item label="基准实例 / Schema">{project?.baseDatabaseName} / {project?.baseSchemaName}</Descriptions.Item>
          <Descriptions.Item label="新建表">{newCount}</Descriptions.Item>
          <Descriptions.Item label="修改表">{modifyCount}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{project?.createdAt}</Descriptions.Item>
          <Descriptions.Item label="状态">{projectStatusTag(project?.status || '')}</Descriptions.Item>
        </Descriptions>
        <Divider />
        <Row justify="center"><Space>
          <Button icon={<ArrowRightOutlined />} onClick={() => setViewMode('list')}>返回项目列表</Button>
          {project?.status !== 'CLOSED' && <Popconfirm title="关闭工单" onConfirm={handleCloseProject}><Button danger>关闭工单</Button></Popconfirm>}
        </Space></Row>
      </Card>
    </div>
  );

  // ========== Main Render ==========
  if (viewMode === 'list') {
    return <div style={{ height: 'calc(100vh - 64px)', overflow: 'hidden', display: 'flex', flexDirection: 'column', padding: 16 }}>{renderProjectList()}</div>;
  }

  const stepTitles = ['创建工单', '开发', '集成', '预发帀', '生产', '结束'];
  const envForStep: Record<number, string> = { 1: 'dev', 2: 'integration', 3: 'staging', 4: 'production' };
  return (
    <div style={{ height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {/* Top Steps Bar */}
      <Card size="small" style={{ borderRadius: 0, borderBottom: 0, flexShrink: 0 }} bodyStyle={{ padding: '8px 24px' }}>
        <Row align="middle" gutter={16}>
          <Col><Button icon={<RollbackOutlined />} size="small" onClick={() => {
            if (currentStep === 0 || (currentStep === 1 && project)) setViewMode('list');
            else setCurrentStep(currentStep - 1);
          }}>{currentStep === 0 || (currentStep === 1 && project) ? '返回列表' : '上一歀'}</Button></Col>
          <Col flex="auto">
            <Steps current={currentStep} size="small" onChange={s => { if (s <= currentStep && !(s === 0 && project)) setCurrentStep(s); }} style={{ maxWidth: 760, margin: '0 auto' }}>
              {stepTitles.map((t, i) => <Step key={i} title={t} icon={i < currentStep ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : i === currentStep ? <EditOutlined style={{ color: '#1890ff' }} /> : undefined} />)}
            </Steps>
          </Col>
          <Col>{currentStep < 5 && <Button type="primary" size="small" onClick={async () => {
            if (currentStep === 0) {
              if (!project) { await handleCreateProject(); }
              else { await handleAdvanceStage(); }
            } else if (currentStep <= 4) {
              await handleAdvanceStage();
            }
          }}>{currentStep === 0 && !project ? '提交创建' : currentStep === 4 ? '完成生产发布' : '推进到下一阶段'}</Button>}</Col>
        </Row>
      </Card>
      {/* Step Content */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
        {currentStep === 0 && renderCreateStep()}
        {currentStep === 1 && project?.currentStage === 'DESIGN' && renderDesignStep()}
        {currentStep === 1 && project?.currentStage !== 'DESIGN' && renderEnvStep(envForStep[currentStep])}
        {[2, 3, 4].includes(currentStep) && renderEnvStep(envForStep[currentStep])}
        {currentStep === 5 && renderFinishStep()}
      </div>

      {/* ========== Modals ========== */}
      {/* Column Edit Modal */}
      <Modal title={editingColumn?.isNew ? '配置新字段' : `编辑字段 - ${editingColumn?.name}`} open={columnModalVisible} onCancel={() => setColumnModalVisible(false)} onOk={saveColumnEdit} okText="保存" width={550}>
        <Form form={editForm} layout="vertical">
          <Row gutter={16}>
            <Col span={12}><Form.Item name="name" label="字段名" rules={[{ required: true, message: '请输入字段名' }, { pattern: /^\w+$/, message: '只能包含字母数字下划线' }]}><Input placeholder="例如: user_name" /></Form.Item></Col>
            <Col span={6}><Form.Item name="type" label="类型" rules={[{ required: true }]}><Select>{COLUMN_TYPES.map(t => <Option key={t} value={t}>{t}</Option>)}</Select></Form.Item></Col>
            <Col span={6}><Form.Item name="length" label="长度"><InputNumber style={{ width: '100%' }} min={1} max={65535} /></Form.Item></Col>
          </Row>
          <Row gutter={16}>
            <Col span={6}><Form.Item name="nullable" label="可空" valuePropName="checked"><Switch checkedChildren="昀" unCheckedChildren="吀" /></Form.Item></Col>
            <Col span={6}><Form.Item name="primaryKey" label="主键" valuePropName="checked"><Switch checkedChildren="昀" unCheckedChildren="吀" /></Form.Item></Col>
            <Col span={6}><Form.Item name="autoIncrement" label="自增" valuePropName="checked"><Switch checkedChildren="昀" unCheckedChildren="吀" /></Form.Item></Col>
            <Col span={6}><Form.Item name="unsigned" label="无符号" valuePropName="checked"><Switch checkedChildren="昀" unCheckedChildren="吀" /></Form.Item></Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}><Form.Item name="defaultValue" label="默认值"<Input placeholder="例如: 0, NULL" /></Form.Item></Col>
          </Row>
          <Form.Item name="comment" label="注释"><Input placeholder="字段说明" /></Form.Item>
        </Form>
      </Modal>

      {/* SQL Import Modal */}
      <Modal title="导入SQL语句" open={sqlImportModalVisible} onCancel={() => setSqlImportModalVisible(false)} onOk={handleImportSql} okText="导入" width={800}>
        <Alert message="Paste DDL SQL (CREATE/ALTER TABLE), separated by semicolons." type="info" showIcon style={{ marginBottom: 12 }} />
        <TextArea value={importSqlText} onChange={e => setImportSqlText(e.target.value)} rows={16} placeholder="粘贴DDL SQL语句..." style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 12 }} />
      </Modal>

      {/* Add Existing Table Modal */}
      <Modal title="添加已有表" open={addTableModalVisible} onCancel={() => setAddTableModalVisible(false)} footer={null} width={500}>
        <Form layout="vertical">
          <Form.Item label="Schema"><Select value={selectedSchemaForAdd || undefined} onChange={v => { setSelectedSchemaForAdd(v); loadTableNames(project?.baseDatabaseId, v); }}>{schemaList.map(s => <Option key={s} value={s}>{s}</Option>)}</Select></Form.Item>
        </Form>
        <Spin spinning={loadingAddTables}>
          {tableNames.length === 0 ? <Empty description="暂无血" /> : (
            <div style={{ maxHeight: 400, overflow: 'auto' }}>
              {tableNames.filter(t => !projectTables.some(pt => pt.tableName === t)).map(t => (
                <div key={t} style={{ padding: '8px 12px', borderBottom: '1px solid #f0f0f0', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Space><TableOutlined style={{ color: '#1890ff' }} /><span>{t}</span></Space>
                  <Button size="small" type="link" onClick={() => handleAddExistingTable(t)}>添加</Button>
                </div>
              ))}
            </div>
          )}
        </Spin>
      </Modal>

      {/* SQL Preview Modal */}
      <Modal title="变更SQL预览" open={sqlPreviewVisible} onCancel={() => setSqlPreviewVisible(false)} width={900} footer={<Button onClick={() => setSqlPreviewVisible(false)}>关闭</Button>}>
        <TextArea value={previewSqlContent} readOnly rows={24} style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 12 }} />
      </Modal>

      {/* Submit to Env Modal */}
      <Modal title={`提交DDL变更{ENV_LABELS[submitEnvTarget] || ''}环境`} open={submitEnvModalVisible} onCancel={() => setSubmitEnvModalVisible(false)} onOk={handleSubmitToEnv} confirmLoading={submittingEnv} width={500}>
        <Form layout="vertical">
          <Form.Item label="目标数据库实例" required>
            <Select placeholder="选择数据库" value={submitEnvDbId || undefined} onChange={v => setSubmitEnvDbId(v)}>
              {databaseList.map((db: any) => <Option key={db.id} value={db.id}>{db.name} ({db.host}:{db.port})</Option>)}
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default SchemaDesignerPage;
