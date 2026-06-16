import React, { useState, useEffect, useMemo } from 'react';
import {
  Card, Table, Button, Tag, Space, Modal, Form, Input, Select,
  Radio, message, Popconfirm, Descriptions, Alert, Timeline,
  Row, Col, Progress, Switch, Tooltip, Slider, Spin, DatePicker,
  Steps,
} from 'antd';
import {
  PlusOutlined, WarningOutlined, CheckOutlined, UnlockOutlined,
  HistoryOutlined, CloseCircleOutlined, ReloadOutlined,
  InfoCircleOutlined, EditOutlined,
} from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import { ticketApi, instanceApi, userApi } from '../utils/api';

const { TextArea } = Input;
const { Option } = Select;

interface Ticket {
  id: string;
  title: string;
  description: string;
  type: string;
  status: string;
  priority: string;
  creatorId: string;
  createTime: string;
  sqlContent: string;
  changeType: string;
  useLockFreeDml?: boolean;
  dmlBatchSize?: number;
  dmlBatchInterval?: number;
  dmlProgress?: number;
  databaseId: string;
  databaseName?: string;
  dmlTotalAffected?: number;
  dmlBatchCount?: number;
  approvalDeadline?: string;
  currentApproverId?: string;
  executeTime?: string;
  estimateAffectedRows?: number;
  errorMsg?: string;
  dmlProgressPercent?: number;
  dmlTotalBatches?: number;
  dmlStatus?: string;
}

interface TableCheckItem {
  sql: string;
  tableName: string;
  tableSize: number;
  tableSizeHuman: string;
  estimateRows: number;
  estimateAffectRows: number;
  needLockFree: boolean;
  riskLevel: string;
  message: string;
  recommendedBatchSize: number;
  hasPrimaryKey: boolean;
  primaryKey: string;
}

interface DmlCheckResult {
  tableName: string;
  tableSize: number;
  tableSizeHuman: string;
  estimateRows: number;
  estimateAffectRows: number;
  needLockFree: boolean;
  riskLevel: string;
  message: string;
  recommendedBatchSize: number;
  hasPrimaryKey: boolean;
  primaryKey: string;
  primaryKeys: string[];
  tables?: TableCheckItem[];
  tableCount?: number;
}

const TicketList: React.FC = () => {
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedTicket, setSelectedTicket] = useState<Ticket | null>(null);
  const [ticketDetail, setTicketDetail] = useState<any>(null);
  const [approvalRecords, setApprovalRecords] = useState<any[]>([]);
  const [loadingDetail, setLoadingDetail] = useState(false);
  const [tabKey] = useState('my');
  const [form] = Form.useForm();
  const [databases, setDatabases] = useState<any[]>([]);
  const [databaseNames, setDatabaseNames] = useState<string[]>([]);
  const [loadingDbNames, setLoadingDbNames] = useState(false);
  const [dmlCheckResult, setDmlCheckResult] = useState<DmlCheckResult | null>(null);
  const [checkingDml, setCheckingDml] = useState(false);
  const [useLockFree, setUseLockFree] = useState(false);
  const [batchSize, setBatchSize] = useState(1000);
  const [batchInterval, setBatchInterval] = useState(100);
  const [users, setUsers] = useState<any[]>([]);
  const [statusFilter, setStatusFilter] = useState('all');
  const [searchText, setSearchText] = useState('');
  const [createStep, setCreateStep] = useState(0);
  const changeType = Form.useWatch('changeType', form);

  useEffect(() => { fetchTickets(); fetchDatabases(); fetchUsers(); }, [tabKey]);
  useEffect(() => { fetchTickets(); }, [statusFilter]);

  const fetchTickets = async () => {
    setLoading(true);
    try {
      const params: any = {};
      if (statusFilter && statusFilter !== 'all') { params.status = statusFilter; }
      if (searchText.trim()) { params.keyword = searchText.trim(); }
      const res = await ticketApi.list(params);
      setTickets(res.data.data || []);
    } catch { message.error('OK'); }
    finally { setLoading(false); }
  };

  const fetchDatabases = async () => {
    try { const res = await instanceApi.list(); setDatabases(res.data.data || []); } catch {}
  };

  const fetchUsers = async () => {
    try {
      const res = await userApi.list();
      const data = res.data?.data;
      setUsers(Array.isArray(data) ? data : data?.records || data?.list || data?.content || []);
    } catch {}
  };

  const userNameMap = useMemo(() => {
    const map: Record<string, string> = {};
    if (Array.isArray(users)) {
      users.forEach((u: any) => {
        const name = u.nickname || u.username || u.realName || u.id;
        if (u.id) map[String(u.id)] = name;
      });
    }
    return map;
  }, [users]);

  const handleInstanceChange = async (instanceId: string) => {
    setDatabaseNames([]);
    form.setFieldValue('databaseName', undefined);
    if (!instanceId) return;
    setLoadingDbNames(true);
    try {
      const res = await instanceApi.getSchemas(instanceId);
      setDatabaseNames(res.data.data || []);
    } catch { setDatabaseNames([]); }
    finally { setLoadingDbNames(false); }
  };

  const handleCreateTicket = async (values: any) => {
    try {
      const sql = values.sqlContent;
      if (sql && sql.trim()) {
        try {
          const auditRes = await ticketApi.auditSql(sql);
          const auditResult = auditRes.data?.data;
          if (auditResult && !auditResult.passed) {
            const errors = (auditResult.issues || []).filter((i: any) => i.level === 'error').map((i: any) => i.message);
            if (errors.length > 0) {
              message.error({
                content: <div><div style={{ fontWeight: 'bold', marginBottom: 8 }}>SQL审核不通过，请修正后重新提交：</div>{errors.map((err: string, idx: number) => <div key={idx}>? {err}</div>)}</div>,
                duration: 8,
              });
              return;
            }
          }
        } catch {}
      }
      const selectedType = values.changeType || 'DML';
      const isLockFreeDml = selectedType === 'lock_free_dml';
      const isLockFreeDdl = selectedType === 'lock_free_ddl';
      const data = {
        ...values, sqlContent: (values.sqlContent || '').trim(),
        changeType: isLockFreeDml ? 'DML' : isLockFreeDdl ? 'DDL' : selectedType,
        useLockFreeDml: isLockFreeDml || useLockFree,
        dmlBatchSize: isLockFreeDml || useLockFree ? batchSize : undefined,
        dmlBatchInterval: isLockFreeDml || useLockFree ? batchInterval : undefined,
        useOnlineDdl: isLockFreeDdl || undefined,
      };
      await ticketApi.create(data);
      message.success('OK');
      setModalVisible(false); form.resetFields(); setDmlCheckResult(null);
      setUseLockFree(false); setBatchSize(1000); setBatchInterval(100);
      setCreateStep(0); setDatabaseNames([]);
      fetchTickets();
    } catch (error: any) {
      message.error(error.response?.data?.message || '创建工单失败');
    }
  };

  const handleSubmitApplication = async () => {
    try { await form.validateFields(); } catch { return; }
    if (!dmlCheckResult) {
      setCheckingDml(true);
      const sql = form.getFieldValue('sqlContent');
      const databaseId = form.getFieldValue('databaseId');
      const databaseName = form.getFieldValue('databaseName');
      if (!sql || !databaseId) { message.warning('OK'); setCheckingDml(false); return; }
      try {
        const res = await ticketApi.checkLockFreeDml(databaseId, databaseName, sql);
        const result: DmlCheckResult = res.data.data;
        setDmlCheckResult(result);
        setBatchSize(result.recommendedBatchSize);
        if (result.riskLevel === 'error') { setCheckingDml(false); return; }
      } catch { message.error('OK'); setCheckingDml(false); return; }
      setCheckingDml(false);
    }
    if (dmlCheckResult?.riskLevel === 'error') return;
    setCreateStep(1);
  };

  const handleRollback = async (ticket: Ticket) => {
    try { await ticketApi.rollback(ticket.id); message.success('OK'); fetchTickets(); }
    catch (error: any) { message.error(error.response?.data?.message || '回滚失败'); }
  };

  const getStatusTag = (status: string) => {
    const m: Record<string, { color: string; text: string }> = {
      pending:{color:'orange',text:'待审批'}, approving:{color:'orange',text:'审批中'}, approved:{color:'cyan',text:'已审批'},
      executing:{color:'blue',text:'执行中'}, done:{color:'green',text:'已完成'}, failed:{color:'red',text:'失败'},
      rejected:{color:'red',text:'已拒绝'}, cancelled:{color:'default',text:'已取消'}, rolled_back:{color:'purple',text:'已回滚'},
    };
    const { color, text } = m[status] || { color: 'default', text: status || '未知' };
    return <Tag color={color}>{text}</Tag>;
  };

  const getRiskColor = (level: string) => {
    switch (level) { case 'high': return '#ff4d4f'; case 'medium': return '#faad14'; case 'low': return '#52c41a'; default: return '#999'; }
  };

  const getSpeedLabel = (interval: number) => {
    if (interval <= 50) return '快速（高负载）'; if (interval <= 150) return '适中（推荐）'; return '慢速（低负载）';
  };

  const fetchTicketDetail = async (ticket: Ticket) => {
    setSelectedTicket(ticket); setDetailVisible(true); setLoadingDetail(true);
    try {
      const [detailRes, approvalRes] = await Promise.all([
        ticketApi.get(ticket.id), ticketApi.approvals(ticket.id).catch(() => ({ data: { data: [] } })),
      ]);
      setTicketDetail(detailRes.data.data);
      setApprovalRecords(approvalRes.data?.data || []);
    } catch { setTicketDetail(ticket as any); setApprovalRecords([]); }
    finally { setLoadingDetail(false); }
  };

  const columns = [
    { title: '工单编号', dataIndex: 'id', key: 'id', width: 160,
      render: (id: string) => <a onClick={() => { const t = tickets.find(tk => tk.id === id); if (t) fetchTicketDetail(t); }} style={{ color: '#1890ff' }}>{id}</a> },
    { title: '业务背景', dataIndex: 'description', key: 'description', width: 160, ellipsis: true, render: (v: string) => v || '-' },
    { title: '工单类型', dataIndex: 'changeType', key: 'changeType', width: 120,
      render: (type: string) => { const m: Record<string,string>={DML:'普通数据变更',DDL:'结构变更'}; return <span>{m[type] || (type||'-').toUpperCase()}</span>; } },
    { title: '当前状态', dataIndex: 'status', key: 'status', width: 100,
      render: (status: string) => {
        const s: Record<string,{color:string;text:string}> = {
          pending:{color:'#faad14',text:'受理状态'}, approving:{color:'#faad14',text:'审批中'}, approved:{color:'#1890ff',text:'已审批'},
          executing:{color:'#1890ff',text:'变更状态'}, done:{color:'#52c41a',text:'已完成'}, failed:{color:'#ff4d4f',text:'失败'},
          rejected:{color:'#ff4d4f',text:'已拒绝'}, cancelled:{color:'#999',text:'已取消'}, rolled_back:{color:'#722ed1',text:'已回滚'},
        };
        const { color, text } = s[status] || { color:'#999', text: status||'未知' };
        return <Tag style={{ color }}>{text}</Tag>;
      } },
    { title: '发起人', dataIndex: 'creatorId', key: 'creatorId', width: 100, render: (v: string) => userNameMap[String(v)] || v || '-' },
    { title: '当前处理人', key: 'handler', width: 100,
      render: (_: any, record: Ticket) => {
        const approverId = record.currentApproverId;
        if (approverId) return <span>{userNameMap[String(approverId)] || approverId}</span>;
        if (record.status === 'pending') return <span style={{ color: '#faad14' }}>待审批</span>;
        if (record.status === 'executing') return <span style={{ color: '#1890ff' }}>系统中</span>;
        return '-';
      } },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 160, render: (t: string) => t ? t.replace('T',' ').slice(0,19) : '-' },
    { title: '最后操作时间', key: 'lastOpTime', width: 160,
      render: (_: any, record: Ticket) => { const t = record.executeTime || record.createTime; return t ? String(t).replace('T',' ').slice(0,19) : '-'; } },
    { title: '操作', key: 'action', width: 80, fixed: 'right' as const,
      render: (_: any, record: Ticket) => <a onClick={() => fetchTicketDetail(record)} style={{ color: '#1890ff' }}>详情</a> },
  ];

  const closeModal = () => {
    setModalVisible(false); form.resetFields(); setDmlCheckResult(null);
    setUseLockFree(false); setBatchSize(1000); setBatchInterval(100);
    setCreateStep(0); setDatabaseNames([]);
  };

  return (
    <div>
      <Card title="数据变更工单列表" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setModalVisible(true)}>数据新建</Button>}>
        <div style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 12 }}>
            <Space><span>状态：</span>
              <Select value={statusFilter} onChange={v => setStatusFilter(v)} style={{ width: 140 }} size="middle">
                <Option value="all">全部</Option><Option value="pending">待审批</Option><Option value="approving">审批中</Option>
                <Option value="approved">已审批</Option><Option value="executing">执行中</Option><Option value="done">已完成</Option>
                <Option value="failed">失败</Option><Option value="rejected">已拒绝</Option>
                <Option value="cancelled">已取消</Option><Option value="rolled_back">已回滚</Option>
              </Select>
            </Space>
          </div>
          <Row gutter={12} align="middle">
            <Col flex="none"><Select size="middle" defaultValue="submitTime" style={{ width: 110 }}><Option value="submitTime">按提交时间</Option></Select></Col>
            <Col flex="none"><DatePicker.RangePicker size="middle" placeholder={['起始时间','截止时间']} /></Col>
            <Col flex="auto"><Input.Search size="middle" placeholder="工单号、姓名、id或者、AppId、业务背景、任务确认" value={searchText} onChange={e => setSearchText(e.target.value)} onSearch={() => fetchTickets()} /></Col>
          </Row>
        </div>
        <Table columns={columns} dataSource={tickets} loading={loading} rowKey="id" scroll={{ x: 1100 }}
          pagination={{ pageSize: 10, showTotal: t => `共 ${t} 条`, showSizeChanger: true }} />
      </Card>

      {/* 创建工单弹窗 */}
      <Modal title="创建数据变更工单" open={modalVisible} onCancel={closeModal} footer={null} width={920}>
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {/* ====== 步骤1：申请 ====== */}
          <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }}>
              <div style={{ width: 28, height: 28, borderRadius: '50%', background: '#1890ff', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 'bold', flexShrink: 0 }}>1</div>
              <div style={{ width: 2, flex: 1, minHeight: 16, background: createStep > 0 ? '#52c41a' : '#d9d9d9' }} />
            </div>
            <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 8, padding: 16, background: '#fff', marginBottom: createStep === 0 ? 0 : 4 }}>
              <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 12 }}>申请</div>
              <Form form={form} layout="horizontal" labelCol={{ flex: '90px' }} wrapperCol={{ flex: 'auto' }} onFinish={handleCreateTicket}>
                <Form.Item label="数据库" required>
                  <Row gutter={16}>
                    <Col span={12}>
                      <Form.Item name="databaseId" noStyle rules={[{ required: true, message: '请选择实例' }]}>
                        <Select placeholder="选择实例" showSearch onChange={val => handleInstanceChange(val)}>
                          {databases.map(db => <Option key={db.id} value={db.id}>{db.name || db.instanceName || db.id}{db.host ? ` (${db.host}:${db.port || 3306})` : ''}</Option>)}
                        </Select>
                      </Form.Item>
                    </Col>
                    <Col span={12}>
                      <Form.Item name="databaseName" noStyle rules={[{ required: true, message: '请选择Schema' }]}>
                        <Select placeholder={databaseNames.length > 0 ? '选择Schema' : '请先选择实例'} loading={loadingDbNames}>
                          {databaseNames.map(name => <Option key={name} value={name}>{name}</Option>)}
                        </Select>
                      </Form.Item>
                    </Col>
                  </Row>
                </Form.Item>
                <Form.Item name="changeType" label="变更类型" initialValue="DML" rules={[{ required: true, message: '请选择变更类型' }]}>
                  <Radio.Group>
                    <Radio value="DML">普通DML（数据变更）</Radio>
                    <Radio value="DDL">普通DDL（结构变更）</Radio>
                    <Radio value="lock_free_dml">无锁变更DML</Radio>
                    <Radio value="lock_free_ddl">无锁变更DDL</Radio>
                  </Radio.Group>
                </Form.Item>
                {(changeType === 'lock_free_dml' || changeType === 'lock_free_ddl') && (
                  <Alert message="无锁变更不作数据备份" description="无锁DML采用分批执行，无锁DDL采用Online DDL机制，均不生成数据备份。如需回滚需自行提供回滚SQL。" type="info" showIcon icon={<InfoCircleOutlined />} style={{ marginBottom: 16 }} />
                )}
                {changeType === 'lock_free_dml' && (
                  <div style={{ padding: '12px 16px', background: '#f9f0ff', borderRadius: 4, border: '1px solid #d3adf7', marginBottom: 16 }}>
                    <Row gutter={16} align="middle" style={{ marginBottom: 12 }}>
                      <Col span={3}>批次大小：</Col>
                      <Col span={16}><Slider min={100} max={5000} step={100} value={batchSize} onChange={setBatchSize} /></Col>
                      <Col span={3} style={{ textAlign: 'right', fontWeight: 'bold', color: '#722ed1' }}>{batchSize} 行/批</Col>
                    </Row>
                    <Row gutter={16} align="middle">
                      <Col span={3}>执行速度：</Col>
                      <Col span={16}><Slider min={0} max={500} step={50} value={batchInterval} onChange={setBatchInterval} /></Col>
                      <Col span={3} style={{ textAlign: 'right', fontWeight: 'bold', color: '#722ed1' }}>{getSpeedLabel(batchInterval)}</Col>
                    </Row>
                  </div>
                )}
                {changeType === 'lock_free_ddl' && (
                  <Alert message="Online DDL 无锁变更" description="将使用 pt-osc / gh-ost 等工具在线执行DDL变更，不锁表。适用于大表结构变更场景。" type="info" showIcon style={{ marginBottom: 16 }} />
                )}
                <Form.Item label="SQL 文本" required>
                  <div style={{ border: '1px solid #d9d9d9', borderRadius: 4 }}>
                    <Editor height="200px" defaultLanguage="sql" theme="vs" options={{ minimap: { enabled: false }, fontSize: 13, lineNumbers: 'on' }} onChange={value => form.setFieldValue('sqlContent', value || '')} />
                  </div>
                </Form.Item>
                <Form.Item name="reasonType" label="原因类型" rules={[{ required: true, message: '请选择原因类型' }]}>
                  <Select placeholder="请选择" allowClear>
                    <Option value="config_fix">配置项订正</Option><Option value="init_data">项目初始化数据</Option>
                    <Option value="bug_fix">程序bug</Option><Option value="no_backend_feature">无后台功能的需求处理</Option>
                    <Option value="data_cleanup">历史数据清理</Option><Option value="test">测试</Option>
                    <Option value="misoperation">误操作</Option><Option value="other">其他</Option>
                  </Select>
                </Form.Item>
                <Form.Item name="description" label="业务背景" rules={[{ required: true, message: '请填写业务背景' }]}>
                  <TextArea rows={4} placeholder="为了减少沟通成本，请在这里认真填写业务背景" />
                </Form.Item>
                <Form.Item name="execMode" label="执行方式" initialValue="auto_after_approve" rules={[{ required: true, message: '请选择执行方式' }]}>
                  <Select placeholder="请选择执行方式">
                    <Option value="auto_after_approve">审批通过后自动执行</Option>
                    <Option value="manual_after_approve">审批通过后手动执行</Option>
                    <Option value="scheduled">定时执行</Option>
                  </Select>
                </Form.Item>
                <Form.Item name="affectedRows" label="影响行数"><Input type="number" placeholder="预估影响行数（可选）" addonAfter="行" /></Form.Item>
                <Form.Item label="回滚SQL">
                  <div style={{ border: '1px solid #d9d9d9', borderRadius: 4 }}>
                    <Editor height="160px" defaultLanguage="sql" theme="vs" options={{ minimap: { enabled: false }, fontSize: 13 }} onChange={value => form.setFieldValue('rollbackSql', value || '')} />
                  </div>
                </Form.Item>
                <Form.Item name="relatedPersons" label="变更相关人员">
                  <Select mode="multiple" placeholder="请输入用户昵称进行筛选" allowClear showSearch
                    filterOption={(input, option) => { const label = option?.label ?? option?.children; return String(label || '').toLowerCase().includes(input.toLowerCase()); }}
                    style={{ width: '100%' }}>
                    {Array.isArray(users) && users.map((u: any) => <Option key={u.id} value={u.id}>{u.nickname || u.username || u.realName || u.id}</Option>)}
                  </Select>
                </Form.Item>
              </Form>
              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12, borderTop: '1px solid #f0f0f0', paddingTop: 12 }}>
                <Button onClick={closeModal}>取消</Button>
                <Button type="primary" onClick={handleSubmitApplication}>提交申请</Button>
              </div>
            </div>
          </div>

          {/* ====== 步骤2：预检查 ====== */}
          <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }}>
              <div style={{ width: 28, height: 28, borderRadius: '50%', background: dmlCheckResult?.riskLevel === 'error' ? '#ff4d4f' : createStep >= 1 ? (createStep > 1 ? '#52c41a' : '#1890ff') : '#d9d9d9', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 'bold', flexShrink: 0 }}>
                {dmlCheckResult?.riskLevel === 'error' ? '?' : createStep > 1 ? '?' : '2'}
              </div>
              <div style={{ width: 2, flex: 1, minHeight: 16, background: createStep > 1 ? '#52c41a' : createStep >= 1 ? '#1890ff' : '#d9d9d9' }} />
            </div>
            <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 8, padding: 16, background: '#fff', marginBottom: createStep === 1 ? 0 : 4 }}>
              <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 8, color: createStep >= 1 ? '#262626' : '#bfbfbf' }}>预检查</div>
              {createStep >= 1 ? (
                dmlCheckResult ? (
                  <div>
                    <Alert message={dmlCheckResult.riskLevel === 'error' ? '预检查未通过' : '预检查通过'} type={dmlCheckResult.riskLevel === 'error' ? 'error' : 'success'} showIcon style={{ marginBottom: 12 }} />
                    <Row gutter={16} style={{ marginBottom: 8 }}>
                      <Col><span style={{ color: '#666' }}>预估影响总行数：</span><b style={{ color: dmlCheckResult.estimateAffectRows > 10000 ? '#ff4d4f' : '#52c41a' }}>{dmlCheckResult.estimateAffectRows.toLocaleString()} 行</b><span style={{ color: '#ff4d4f', marginLeft: 8 }}>（系统统计值，实际影响行数仍然以SQL执行为准!）</span></Col>
                    </Row>
                    {dmlCheckResult.tables && dmlCheckResult.tables.length > 1 && (
                      <Table size="small" dataSource={dmlCheckResult.tables.map((t, idx) => ({ ...t, key: idx }))} pagination={false} style={{ marginBottom: 12 }}
                        columns={[
                          { title: '#', width: 30, render: (_: any, __: any, idx: number) => idx + 1 },
                          { title: '目标表', dataIndex: 'tableName', ellipsis: true, width: 130 },
                          { title: '大小', dataIndex: 'tableSizeHuman', width: 80 },
                          { title: '影响行数', dataIndex: 'estimateAffectRows', width: 90, render: (v: number) => <b>{v.toLocaleString()}</b> },
                          { title: '主键', dataIndex: 'primaryKey', width: 90, render: (v: string) => v || '-' },
                        ]} />
                    )}
                    {dmlCheckResult.needLockFree && createStep === 1 && (
                      <div style={{ padding: '8px 12px', background: '#f9f0ff', borderRadius: 4, border: '1px solid #d3adf7', marginBottom: 8 }}>
                        <Row align="middle" style={{ marginBottom: 8 }}><Col span={4}><span style={{ fontWeight: 500, fontSize: 13 }}>启用无锁变更：</span></Col><Col><Switch checked={useLockFree} onChange={setUseLockFree} checkedChildren={<CheckOutlined />} size="small" /></Col></Row>
                        {useLockFree && (<>
                          <Row gutter={16} align="middle" style={{ marginBottom: 8 }}><Col span={3}><span style={{ fontSize: 12 }}>批次大小：</span></Col><Col span={16}><Slider min={100} max={5000} step={100} value={batchSize} onChange={setBatchSize} /></Col><Col span={3} style={{ textAlign: 'right', fontWeight: 'bold', color: '#722ed1', fontSize: 12 }}>{batchSize} 行/批</Col></Row>
                          <Row gutter={16} align="middle"><Col span={3}><span style={{ fontSize: 12 }}>执行速度：</span></Col><Col span={16}><Slider min={0} max={500} step={50} value={batchInterval} onChange={setBatchInterval} /></Col><Col span={3} style={{ textAlign: 'right', fontWeight: 'bold', color: '#722ed1', fontSize: 12 }}>{getSpeedLabel(batchInterval)}</Col></Row>
                        </>)}
                      </div>
                    )}
                    {dmlCheckResult.message && <Alert message={dmlCheckResult.message} type={dmlCheckResult.riskLevel === 'error' ? 'error' : dmlCheckResult.needLockFree ? 'warning' : 'info'} showIcon />}
                  </div>
                ) : <span style={{ color: '#999' }}>等待检测...</span>
              ) : <span style={{ color: '#bfbfbf' }}>请先填写申请信息并提交</span>}
              {createStep === 1 && (
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12, borderTop: '1px solid #f0f0f0', paddingTop: 12 }}>
                  <Button onClick={() => setCreateStep(0)}>上一步</Button>
                  <Button type="primary" disabled={dmlCheckResult?.riskLevel === 'error'} onClick={() => setCreateStep(2)}>下一步</Button>
                </div>
              )}
            </div>
          </div>

          {/* ====== 步骤3：提交审核 ====== */}
          <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }}>
              <div style={{ width: 28, height: 28, borderRadius: '50%', background: createStep >= 2 ? '#1890ff' : '#d9d9d9', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 'bold', flexShrink: 0 }}>3</div>
              <div style={{ width: 2, flex: 1, minHeight: 16, background: createStep >= 2 ? '#1890ff' : '#d9d9d9' }} />
            </div>
            <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 8, padding: 16, background: '#fff', marginBottom: createStep === 2 ? 0 : 4 }}>
              <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 8, color: createStep >= 2 ? '#262626' : '#bfbfbf' }}>提交审核</div>
              {createStep >= 2 ? (
                <div>
                  <Descriptions column={1} size="small" bordered style={{ marginBottom: 12 }}>
                    <Descriptions.Item label="实例">{form.getFieldValue('databaseId')}</Descriptions.Item>
                    <Descriptions.Item label="目标Schema">{form.getFieldValue('databaseName')}</Descriptions.Item>
                    <Descriptions.Item label="变更类型">{form.getFieldValue('changeType')}</Descriptions.Item>
                    <Descriptions.Item label="预估影响行数"><b style={{ color: (dmlCheckResult?.estimateAffectRows || 0) > 10000 ? '#ff4d4f' : '#52c41a' }}>{dmlCheckResult?.estimateAffectRows?.toLocaleString() || '-'} 行</b></Descriptions.Item>
                    <Descriptions.Item label="业务背景">{form.getFieldValue('description') || '-'}</Descriptions.Item>
                    <Descriptions.Item label="SQL"><div style={{ maxHeight: 80, overflow: 'auto', fontSize: 12, fontFamily: 'monospace', whiteSpace: 'pre-wrap' }}>{form.getFieldValue('sqlContent') || '-'}</div></Descriptions.Item>
                  </Descriptions>
                  <Alert message="点击「提交审核」将创建工单并进入审批流程" type="info" showIcon />
                </div>
              ) : <span style={{ color: '#bfbfbf' }}>预检查通过后可提交审核</span>}
              {createStep === 2 && (
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12, borderTop: '1px solid #f0f0f0', paddingTop: 12 }}>
                  <Button onClick={() => setCreateStep(1)}>上一步</Button>
                  <Button onClick={closeModal}>取消</Button>
                  <Button type="primary" loading={loading} onClick={() => form.submit()}>提交审核</Button>
                </div>
              )}
            </div>
          </div>

          {/* ====== 步骤4：执行 ====== */}
          <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }}>
              <div style={{ width: 28, height: 28, borderRadius: '50%', background: '#d9d9d9', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 'bold', flexShrink: 0 }}>4</div>
              <div style={{ width: 2, flex: 1, minHeight: 16, background: '#d9d9d9' }} />
            </div>
            <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 8, padding: 12, background: '#fff' }}>
              <div style={{ fontWeight: 600, fontSize: 14, color: '#bfbfbf' }}>执行</div>
              <span style={{ color: '#bfbfbf' }}>工单审批通过后自动执行</span>
            </div>
          </div>

          {/* ====== 步骤5：完成 ====== */}
          <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
            <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }}>
              <div style={{ width: 28, height: 28, borderRadius: '50%', background: '#d9d9d9', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 'bold', flexShrink: 0 }}>5</div>
            </div>
            <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 8, padding: 12, background: '#fff' }}>
              <div style={{ fontWeight: 600, fontSize: 14, color: '#bfbfbf' }}>完成</div>
              <span style={{ color: '#bfbfbf' }}>执行成功后自动完成</span>
            </div>
          </div>
        </div>
      </Modal>

      {/* 工单详情弹窗 */}
      <Modal title={null} open={detailVisible} onCancel={() => { setDetailVisible(false); setTicketDetail(null); setApprovalRecords([]); }}
        footer={[<Button key="close" onClick={() => { setDetailVisible(false); setTicketDetail(null); setApprovalRecords([]); }}>关闭</Button>]} width={960}>
        {(selectedTicket || ticketDetail) && (() => {
          const d = ticketDetail || selectedTicket;
          if (loadingDetail && !ticketDetail) return <div style={{ textAlign: 'center', padding: 40 }}><Spin tip="加载详情..." /></div>;
          const stage = !d.status || d.status === 'pending' ? 0 : d.status === 'executing' ? 1 : 3;
          return (
            <div>
              <div style={{ marginBottom: 16 }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                  <h3 style={{ margin: 0, fontSize: 16, fontWeight: 600 }}>数据变更工单详情</h3>
                  <Space>
                    <Button size="small" icon={<HistoryOutlined />}>操作日志</Button>
                    {['pending','approved'].includes(d.status) && <Popconfirm title="确认关闭此工单？" onConfirm={() => { handleRollback(d); setDetailVisible(false); }}><Button size="small" danger icon={<CloseCircleOutlined />}>关闭工单</Button></Popconfirm>}
                    <Button size="small" icon={<ReloadOutlined />} onClick={() => selectedTicket && fetchTicketDetail(selectedTicket)} />
                  </Space>
                </div>
                <div style={{ display: 'flex', gap: 24, marginTop: 12, flexWrap: 'wrap' }}>
                  <span>状态：{getStatusTag(d.status)}</span>
                  <span style={{ color: '#666' }}>工单编号：<span style={{ fontWeight: 500 }}>{d.id}</span></span>
                  <span style={{ color: '#666' }}>工单类型：<span style={{ fontWeight: 500 }}>{d.changeType === 'dml' ? '普通数据变更' : d.changeType?.toUpperCase() || '数据变更'}</span></span>
                </div>
              </div>
              <Descriptions column={1} size="small" labelStyle={{ width: 80, color: '#999' }} style={{ marginBottom: 24 }}>
                <Descriptions.Item label="提交时间">{d.createTime?.replace('T', ' ')}</Descriptions.Item>
                <Descriptions.Item label="创建人"><Tag>{d.creatorId || 'admin'}</Tag></Descriptions.Item>
                <Descriptions.Item label="目标库">{d.databaseName || d.databaseId || '-'}</Descriptions.Item>
                <Descriptions.Item label="变更类型">{d.changeType === 'DML' ? '普通数据变更' : d.changeType === 'DDL' ? '结构变更' : d.changeType || '-'}</Descriptions.Item>
                <Descriptions.Item label="业务背景">{d.description || <Tag>无</Tag>}</Descriptions.Item>
                <Descriptions.Item label="影响行数">{d.dmlTotalAffected != null ? <b>{d.dmlTotalAffected.toLocaleString()}</b> : d.estimateAffectedRows != null ? <b>{d.estimateAffectedRows.toLocaleString()} <span style={{ color: '#ff4d4f', fontWeight: 'normal' }}>（预估）</span></b> : '-'}</Descriptions.Item>
                <Descriptions.Item label="SQL"><div style={{ background: '#1e1e1e', padding: 12, borderRadius: 4, color: '#d4d4d4', fontSize: 12, fontFamily: 'monospace', maxHeight: 150, overflow: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{d.sqlContent || '(空)'}</div></Descriptions.Item>
              </Descriptions>
              <Steps direction="vertical" size="small"
                current={stage}
                status={d.status === 'rejected' || d.status === 'failed' ? 'error' : d.status === 'done' || d.status === 'rolled_back' ? 'finish' : 'process'}
                items={[
                  { title: '预检查', description: <div style={{ marginTop: 8 }}><Space wrap size={4}><Tag color="blue">表检查</Tag><Tag color="blue">通过</Tag><Tag color="blue">SQL审核</Tag><Tag color="blue">通过</Tag><Tag color="orange">{d.estimateAffectedRows?.toLocaleString() || '-'} 行（预估）</Tag></Space><br /><span style={{ color: '#52c41a', fontSize: 12 }}>预检查通过</span></div> },
                  { title: '审批', description: <div style={{ marginTop: 8 }}>
                    {approvalRecords.length > 0 ? <Timeline>{approvalRecords.map((rec: any) => <Timeline.Item key={rec.id} color={rec.status === 'approved' ? 'green' : 'red'}><Tag color={rec.status === 'approved' ? 'green' : 'red'}>{rec.status === 'approved' ? '审批通过' : '审批拒绝'}</Tag><span>{rec.approverId || 'admin'}</span>{rec.comment && <Tooltip title={rec.comment}><InfoCircleOutlined style={{ color: '#1890ff', marginLeft: 4 }} /></Tooltip>}<br /><span style={{ fontSize: 12, color: '#999' }}>{rec.approvedAt || rec.createTime}</span></Timeline.Item>)}</Timeline> : ['rejected','cancelled'].includes(d.status) ? <span style={{ color: '#999' }}>暂无审批记录</span> : <Alert message="等待审批中..." type="warning" showIcon style={{ marginTop: 4 }} />}
                  </div> },
                  { title: '执行', description: <div style={{ marginTop: 8 }}>
                    {['executing','done','failed','rolled_back'].includes(d.status) ? <>
                      <Table size="small" dataSource={[{ key: d.id, id: d.id?.substring(0, 8), database: d.databaseName || d.databaseId, status: d.status, executeTime: d.executeTime || d.createTime }]} pagination={false}
                        columns={[{ title: '分配编号', dataIndex: 'id', width: 100 }, { title: '数据库', dataIndex: 'database', ellipsis: true }, { title: '状态', dataIndex: 'status', width: 80, render: (s: string) => getStatusTag(s) }, { title: '最近执行时间', dataIndex: 'executeTime', width: 150, render: (t: string) => t ? String(t).replace('T', ' ') : '-' }]} />
                      {d.useLockFreeDml && <div style={{ marginTop: 8, background: '#fafafa', padding: 12, borderRadius: 4 }}><Row gutter={16}><Col span={12}><div style={{ fontSize: 12, color: '#666' }}>执行进度</div><Progress percent={d.dmlProgressPercent || 0} /></Col><Col span={6}><div style={{ fontSize: 12, color: '#666' }}>影响行数</div><strong>{(d.dmlTotalAffected ?? 0).toLocaleString()}</strong></Col><Col span={6}><div style={{ fontSize: 12, color: '#666' }}>批次</div><strong>{d.dmlBatchCount || 0}/{d.dmlTotalBatches || '-'}</strong></Col></Row></div>}
                    </> : <span style={{ color: '#999' }}>等待审批完成后自动执行</span>}
                  </div> },
                  { title: '完成', description: <div style={{ marginTop: 8 }}>
                    {d.status === 'done' ? <Alert message="任务执行完成" type="success" showIcon /> : d.status === 'failed' ? <Alert message="任务执行失败" description={d.errorMsg || '未知错误'} type="error" showIcon /> : d.status === 'rolled_back' ? <Alert message="任务已回滚" type="info" showIcon /> : d.status === 'cancelled' ? <Alert message="任务已取消" type="warning" showIcon /> : d.status === 'rejected' ? <Alert message="审批已拒绝" type="error" showIcon /> : <span style={{ color: '#999' }}>等待中...</span>}
                  </div> },
                ]} />
            </div>
          );
        })()}
      </Modal>
    </div>
  );
};

export default TicketList;

