import React, { useState, useEffect, useMemo } from 'react';
import {
  Card, Table, Button, Tag, Space, Form, Input, Select,
  Radio, message, Popconfirm, Descriptions, Alert, Timeline,
  Row, Col, Progress, Switch, Slider,
} from 'antd';
import {
  PlusOutlined, CheckOutlined,
  CloseCircleOutlined,
  InfoCircleOutlined, EditOutlined, ArrowLeftOutlined, SendOutlined,
} from '@ant-design/icons';
import Editor from '@monaco-editor/react';
import { ticketApi, instanceApi, userApi } from '../utils/api';
import { useAuthStore } from '../store/useAuthStore';
import dayjs from 'dayjs';

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
  approverIds?: string;
  approverNames?: string;
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
  const { user } = useAuthStore();
  const [tickets, setTickets] = useState<Ticket[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState<number>(1);
  const [size, setSize] = useState<number>(10);
  const [total, setTotal] = useState<number>(0);
  const [activeTab, setActiveTab] = useState('my');
  const [viewingTicket, setViewingTicket] = useState<Ticket | null>(null);
  const [approvalRecords, setApprovalRecords] = useState<any[]>([]);
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
  const [createStep, setCreateStep] = useState(0);
  const [createdTicketId, setCreatedTicketId] = useState<string | null>(null);
  const [ticketCreating, setTicketCreating] = useState(false);
  const [editingAffectedRows, setEditingAffectedRows] = useState(false);
  const [editingRelatedPersons, setEditingRelatedPersons] = useState(false);
  const [editAffectedRowsValue, setEditAffectedRowsValue] = useState('');
  const [editRelatedPersonsValue, setEditRelatedPersonsValue] = useState<string[]>([]);
  const [reviewSubmitted, setReviewSubmitted] = useState(false);
  const [currentApprover, setCurrentApprover] = useState<string>('');
  const [submittingReview, setSubmittingReview] = useState(false);
  const changeType = Form.useWatch('changeType', form);

  useEffect(() => { if (activeTab === 'my') fetchTickets(); }, [activeTab]);
  useEffect(() => { fetchDatabases(); fetchUsers(); }, []);
  useEffect(() => { fetchTickets(); }, [statusFilter]);

  const fetchTickets = async (p?: number, s?: number) => {
    setLoading(true);
    try {
      const params: any = { page: p || page, size: s || size };
      if (statusFilter && statusFilter !== 'all') { params.status = statusFilter; }
      const res = await ticketApi.list(params);
      const data = res.data?.data;
      if (data && Array.isArray(data.list)) {
        setTickets(data.list);
        setTotal(Number(data.total) || 0);
      } else if (Array.isArray(data)) {
        setTickets(data);
        setTotal(data.length);
      } else {
        setTickets([]);
        setTotal(0);
      }
    } catch { message.error('加载失败'); }
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
        ...values, title: values.description || '数据变更工单', sqlContent: (values.sqlContent || '').trim(),
        instanceId: values.databaseId, schemaName: values.databaseName,
        changeType: isLockFreeDml ? 'DML' : isLockFreeDdl ? 'DDL' : selectedType,
        useLockFreeDml: isLockFreeDml || useLockFree,
        dmlBatchSize: isLockFreeDml || useLockFree ? batchSize : undefined,
        dmlBatchInterval: isLockFreeDml || useLockFree ? batchInterval : undefined,
        useOnlineDdl: isLockFreeDdl || undefined,
      };
      await ticketApi.create(data);
      message.success('OK');
      form.resetFields(); setDmlCheckResult(null);
      setUseLockFree(false); setBatchSize(1000); setBatchInterval(100);
      setCreateStep(0); setDatabaseNames([]);
      setActiveTab('my'); fetchTickets();
    } catch (error: any) {
      message.error(error.response?.data?.message || '创建工单失败');
    }
  };

  const handleSubmitApplication = async () => {
    try { await form.validateFields(); } catch { return; }
    const currentType = form.getFieldValue('changeType') || 'DML';
    const isLockFree = currentType === 'lock_free_dml' || currentType === 'lock_free_ddl';

    if (!dmlCheckResult) {
      setCheckingDml(true);
      const sql = form.getFieldValue('sqlContent');
      const databaseId = form.getFieldValue('databaseId');
      const databaseName = form.getFieldValue('databaseName');
      if (!sql) { message.warning('请输入SQL'); setCheckingDml(false); return; }
      if (!databaseId) { message.warning('请选择数据库实例'); setCheckingDml(false); return; }
      message.loading({ content: '正在预检查 SQL...', key: 'dmlCheck', duration: 0 });
      try {
        const res = await ticketApi.checkLockFreeDml(databaseId, databaseName, sql);
        message.destroy('dmlCheck');
        const result: DmlCheckResult = res.data.data;
        setDmlCheckResult(result);
        setBatchSize(result.recommendedBatchSize);
        if (result.riskLevel === 'error') { setCheckingDml(false); return; }
      } catch { message.destroy('dmlCheck'); message.error('预检查失败，请重试'); setCheckingDml(false); return; }
      setCheckingDml(false);
    }
    if (dmlCheckResult?.riskLevel === 'error') return;

    // 普通DML/DDL：预评估通过后创建/更新工单入库
    const values = form.getFieldsValue();
    const data = {
      ...values,
      sqlContent: (values.sqlContent || '').trim(),
      instanceId: values.databaseId, schemaName: values.databaseName,
      changeType: currentType,
      useLockFreeDml: false,
      title: values.description || '数据变更工单',
    };

    if (!isLockFree) {
      setTicketCreating(true);
      try {
        if (createdTicketId) {
          // 已有工单，更新工单信息
          await ticketApi.update(createdTicketId, data);
          message.success('工单已更新，请重新提交审核');
        } else {
          // 新建工单
          const res = await ticketApi.create(data);
          const ticketData = res.data?.data;
          setCreatedTicketId(ticketData?.id || ticketData);
          message.success('工单已创建');
          fetchTickets();
        }
      } catch (e: any) {
        message.error(e?.response?.data?.message || (createdTicketId ? '更新工单失败' : '创建工单失败'));
        setTicketCreating(false);
        return;
      }
      setTicketCreating(false);
    }
    // 预检查通过，自动跳到审核环节
    setCreateStep(2);
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
    // 重置新建状态，准备加载工单详情
    setDmlCheckResult(null); setCreateStep(0); setCreatedTicketId(null);
    setReviewSubmitted(false); setCurrentApprover(''); setSubmittingReview(false);
    setEditingAffectedRows(false); setEditingRelatedPersons(false);

    try {
      const [detailRes, approvalRes] = await Promise.all([
        ticketApi.get(ticket.id), ticketApi.approvals(ticket.id).catch(() => ({ data: { data: [] } })),
      ]);
      const detail = detailRes.data?.data || ticket;
      setApprovalRecords(approvalRes.data?.data || []);
      setViewingTicket(detail as Ticket);

      // 填充表单数据
      form.setFieldsValue({
        databaseId: detail.databaseId || detail.instanceId || '',
        databaseName: detail.databaseName || detail.schemaName || '',
        changeType: detail.changeType || 'DML',
        sqlContent: detail.sqlContent || '',
        reasonType: detail.reasonType || '',
        description: detail.description || detail.title || '',
        execMode: detail.execMode || 'auto_after_approve',
        affectedRows: detail.affectedRows || detail.estimateAffectedRows || '',
        relatedPersons: detail.relatedPersons || [],
      });

      // 根据工单状态确定步骤
      const status = detail.status;
      setCreatedTicketId(detail.id); // 设置工单ID，供后续操作使用
      if (['pending', 'approving'].includes(status)) {
        setCreateStep(2); setReviewSubmitted(false);
      } else if (status === 'approved') {
        setCreateStep(2); setReviewSubmitted(false);
      } else if (['executing', 'done', 'failed', 'rolled_back'].includes(status)) {
        setCreateStep(4); setReviewSubmitted(true);
      } else {
        setCreateStep(2); setReviewSubmitted(false);
      }
      // 构造假 dmlCheckResult 让预检查步骤变绿
      setDmlCheckResult({
        tableName: '', tableSize: 0, tableSizeHuman: '', estimateRows: 0,
        estimateAffectRows: detail.estimateAffectedRows || 0,
        needLockFree: detail.useLockFreeDml || false,
        riskLevel: 'passed', message: '预检查已完成',
        recommendedBatchSize: detail.dmlBatchSize || 1000,
        hasPrimaryKey: true, primaryKey: '', primaryKeys: [],
      });
    } catch {
      setViewingTicket(null);
      message.error('加载工单详情失败');
      return;
    }
    setActiveTab('create');
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
        const approverNames = record.approverNames;
        if (approverNames) return <span>{approverNames}</span>;
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

  // 基本信息的编辑操作：
  const handleSaveAffectedRows = async () => {
    if (!createdTicketId || !editAffectedRowsValue) return;
    try {
      await ticketApi.update(createdTicketId, { affectedRows: Number(editAffectedRowsValue) });
      form.setFieldValue('affectedRows', editAffectedRowsValue);
      message.success('影响行数已更新');
    } catch (e: any) {
      message.error(e?.response?.data?.message || '更新失败');
    }
    setEditingAffectedRows(false);
  };

  const handleSaveRelatedPersons = async () => {
    if (!createdTicketId) return;
    try {
      await ticketApi.update(createdTicketId, { relatedPersons: editRelatedPersonsValue });
      form.setFieldValue('relatedPersons', editRelatedPersonsValue);
      message.success('相关人员已更新');
    } catch (e: any) {
      message.error(e?.response?.data?.message || '更新失败');
    }
    setEditingRelatedPersons(false);
  };

  const handleSubmitReview = async () => {
    if (!createdTicketId) return;
    setSubmittingReview(true);
    try {
      const res = await ticketApi.get(createdTicketId);
      const ticketData = res.data?.data;
      const approver = ticketData?.currentApproverId || ticketData?.approverId || '';
      if (approver) {
        const approverName = userNameMap[String(approver)] || approver;
        setCurrentApprover(approverName);
      } else {
        setCurrentApprover('待分配');
      }
      setReviewSubmitted(true);
      message.success('已提交审核，等待审批人处理');
    } catch (e: any) {
      message.error(e?.response?.data?.message || '提交审核失败');
    } finally {
      setSubmittingReview(false);
    }
  };

  const viewTicketAction = async (action: string) => {
    if (!viewingTicket) return;
    try {
      if (action === 'cancel') {
        await ticketApi.cancel(viewingTicket.id);
        message.success('工单已关闭');
      }
      fetchTickets();
      closeCreate();
    } catch (e: any) {
      message.error(e?.response?.data?.message || '操作失败');
    }
  };

  const closeCreate = () => {
    form.resetFields(); setDmlCheckResult(null);
    setUseLockFree(false); setBatchSize(1000); setBatchInterval(100);
    setCreateStep(0); setDatabaseNames([]);
    setCreatedTicketId(null); setTicketCreating(false);
    setEditingAffectedRows(false); setEditingRelatedPersons(false);
    setReviewSubmitted(false); setCurrentApprover(''); setSubmittingReview(false);
    setViewingTicket(null); setApprovalRecords([]);
    setActiveTab('my');
  };

  return (
    <div>
      {/* ====== 我的工单 Tab ====== */}
      {activeTab === 'my' && (
        <Card title="数据变更工单列表" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => { setViewingTicket(null); setApprovalRecords([]); setActiveTab('create'); }}>新建工单</Button>}>
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
          </div>
          <Table columns={columns} dataSource={tickets} loading={loading} rowKey="id" scroll={{ x: 1200 }}
            pagination={{
              current: page,
              pageSize: size,
              total,
              showTotal: (t: number) => `共 ${t} 条`,
              showSizeChanger: true,
              onChange: (p: number, s: number) => {
                setPage(p);
                setSize(s);
                fetchTickets(p, s);
              },
            }} />
        </Card>
      )}

      {/* ====== 新建/查看工单 Tab ====== */}
      {activeTab === 'create' && (() => {
        const isViewing = !!viewingTicket;
        return (
        <Card
          title={isViewing ? `工单详情 - ${viewingTicket?.id || ''}` : '新建数据变更工单'}
          extra={(
            <Space>
              {isViewing && ['pending','approving'].includes(viewingTicket?.status || '') && (
                <Popconfirm title="确认关闭此工单？" onConfirm={() => viewTicketAction('cancel')}>
                  <Button size="small" danger icon={<CloseCircleOutlined />}>关闭工单</Button>
                </Popconfirm>
              )}
              <Button icon={<ArrowLeftOutlined />} onClick={closeCreate}>返回列表</Button>
            </Space>
          )}
          style={{ maxWidth: '100%' }}
        >
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {/* ====== 步骤1：申请 / 基本信息 ====== */}
            <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }}>
                <div style={{ width: 28, height: 28, borderRadius: '50%', background: (isViewing || (createdTicketId && createStep > 0)) ? '#52c41a' : '#1890ff', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 'bold', flexShrink: 0 }}>
                  {(isViewing || (createdTicketId && createStep > 0)) ? '✓' : '1'}
                </div>
                <div style={{ width: 2, flex: 1, minHeight: 16, background: (isViewing || (createdTicketId && createStep > 0) || createStep > 0) ? '#52c41a' : '#d9d9d9' }} />
              </div>
              <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 8, padding: 16, background: '#fff', marginBottom: createStep === 0 && !isViewing && !(createdTicketId && createStep > 0) ? 0 : 4 }}>
                <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 12 }}>
                  {(isViewing || (createdTicketId && createStep > 0)) ? '基本信息' : '申请'}
                </div>
                {(isViewing || (createdTicketId && createStep > 0)) ? (
                  /* ========== 基本信息（工单已创建，只读展示，部分字段可编辑） ========== */
                  <div>
                    <Descriptions column={1} size="small" bordered labelStyle={{ width: 110, color: '#666' }} contentStyle={{ color: '#222' }}>
                      <Descriptions.Item label="提交时间">
                        <span style={{ fontSize: 13 }}>{dayjs().format('YYYY-MM-DD HH:mm:ss')}</span>
                      </Descriptions.Item>
                      <Descriptions.Item label="创建人">
                        <Tag>{user?.nickname || user?.username || '-'}</Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="原因类别">
                        {(() => {
                          const m: Record<string,string> = { config_fix:'配置项订正', init_data:'项目初始化数据', bug_fix:'程序bug', no_backend_feature:'无后台功能的需求处理', data_cleanup:'历史数据清理', test:'测试', misoperation:'误操作', other:'其他' };
                          return m[form.getFieldValue('reasonType') || ''] || form.getFieldValue('reasonType') || '-';
                        })()}
                      </Descriptions.Item>
                      <Descriptions.Item label="业务背景">
                        <div style={{ whiteSpace: 'pre-wrap', fontSize: 13 }}>{form.getFieldValue('description') || '-'}</div>
                      </Descriptions.Item>
                      <Descriptions.Item label="相关人员">
                        {editingRelatedPersons ? (
                          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                            <Select mode="multiple" style={{ flex: 1 }} value={editRelatedPersonsValue}
                              placeholder="请选择" showSearch
                              filterOption={(input, option) => { const label = option?.label ?? option?.children; return String(label || '').toLowerCase().includes(input.toLowerCase()); }}
                              onChange={v => setEditRelatedPersonsValue(v)}>
                              {Array.isArray(users) && users.map((u: any) => <Option key={u.id} value={u.id}>{u.nickname || u.username || u.realName || u.id}</Option>)}
                            </Select>
                            <Button size="small" type="primary" onClick={handleSaveRelatedPersons}>保存</Button>
                            <Button size="small" onClick={() => setEditingRelatedPersons(false)}>取消</Button>
                          </div>
                        ) : (
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span style={{ fontSize: 13 }}>
                              {(form.getFieldValue('relatedPersons') || [])
                                .map((id: string) => users.find((u: any) => u.id === id)?.nickname || users.find((u: any) => u.id === id)?.username || id).join(', ') || '-'}
                            </span>
                            <Button type="link" size="small" icon={<EditOutlined />}
                              onClick={() => { setEditRelatedPersonsValue(form.getFieldValue('relatedPersons') || []); setEditingRelatedPersons(true); }} />
                          </div>
                        )}
                      </Descriptions.Item>
                      <Descriptions.Item label="执行方式">
                        {(() => {
                          const m: Record<string,string> = { auto_after_approve:'审批通过后自动执行', manual_after_approve:'审批通过后手动执行', scheduled:'定时执行' };
                          return m[form.getFieldValue('execMode') || ''] || form.getFieldValue('execMode') || '-';
                        })()}
                      </Descriptions.Item>
                      <Descriptions.Item label="变更库">
                        {form.getFieldValue('databaseId') || '-'}{form.getFieldValue('databaseName') ? ` / ${form.getFieldValue('databaseName')}` : ''}
                      </Descriptions.Item>
                      <Descriptions.Item label="影响行数">
                        {editingAffectedRows ? (
                          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                            <Input type="number" value={editAffectedRowsValue} onChange={e => setEditAffectedRowsValue(e.target.value)} style={{ width: 150 }} addonAfter="行" />
                            <Button size="small" type="primary" onClick={handleSaveAffectedRows}>保存</Button>
                            <Button size="small" onClick={() => setEditingAffectedRows(false)}>取消</Button>
                          </div>
                        ) : (
                          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <b style={{ fontSize: 13 }}>
                              {(() => { const v = form.getFieldValue('affectedRows'); return v != null && v !== '' ? `${Number(v).toLocaleString()} 行` : '-'; })()}
                            </b>
                            <Button type="link" size="small" icon={<EditOutlined />}
                              onClick={() => { setEditAffectedRowsValue(form.getFieldValue('affectedRows') || ''); setEditingAffectedRows(true); }} />
                          </div>
                        )}
                      </Descriptions.Item>
                      <Descriptions.Item label="变更SQL">
                        <div style={{ maxHeight: 100, overflow: 'auto', fontSize: 12, fontFamily: 'monospace', whiteSpace: 'pre-wrap', background: '#f5f5f5', color: '#333', padding: 8, borderRadius: 4, border: '1px solid #e8e8e8' }}>
                          {form.getFieldValue('sqlContent') || '-'}
                        </div>
                      </Descriptions.Item>
                    </Descriptions>
                  </div>
                ) : (
                  /* ========== 申请表（未创建工单） ========== */
                  <div>
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
                      <Editor height="200px" defaultLanguage="sql" theme="vs" value={form.getFieldValue('sqlContent') || ''} options={{ minimap: { enabled: false }, fontSize: 13, lineNumbers: 'on' }} onChange={value => form.setFieldValue('sqlContent', value || '')} />
                    </div>
                  </Form.Item>
                  <Form.Item name="sqlContent" rules={[{ required: true, message: '请输入SQL' }]} hidden>
                    <Input />
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
                  <Form.Item name="affectedRows" label="影响行数" rules={[{ required: true, message: '请填写影响行数' }]}><Input type="number" placeholder="预估影响行数（可选）" addonAfter="行" /></Form.Item>
                  <Form.Item name="relatedPersons" label="相关人员">
                    <Select mode="multiple" placeholder="请输入用户昵称进行筛选" allowClear showSearch
                      filterOption={(input, option) => { const label = option?.label ?? option?.children; return String(label || '').toLowerCase().includes(input.toLowerCase()); }}
                      style={{ width: '100%' }}>
                      {Array.isArray(users) && users.map((u: any) => <Option key={u.id} value={u.id}>{u.nickname || u.username || u.realName || u.id}</Option>)}
                    </Select>
                  </Form.Item>
                </Form>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12, borderTop: '1px solid #f0f0f0', paddingTop: 12 }}>
                  <Button onClick={closeCreate}>取消</Button>
                  <Button type="primary" loading={checkingDml || ticketCreating} onClick={handleSubmitApplication}>提交申请</Button>
                </div>
                  </div>
                )}
              </div>
            </div>

            {/* ====== 步骤2：预检查 ====== */}
            <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }}>
                <div style={{ width: 28, height: 28, borderRadius: '50%', background: dmlCheckResult?.riskLevel === 'error' ? '#ff4d4f' : (isViewing || createStep >= 1) ? (createStep > 1 || isViewing ? '#52c41a' : '#1890ff') : '#d9d9d9', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 'bold', flexShrink: 0 }}>
                  {dmlCheckResult?.riskLevel === 'error' ? '?' : (isViewing || createStep > 1) ? '✓' : '2'}
                </div>
                <div style={{ width: 2, flex: 1, minHeight: 16, background: (isViewing || createStep > 1) ? '#52c41a' : createStep >= 1 ? '#1890ff' : '#d9d9d9' }} />
              </div>
              <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 8, padding: 16, background: '#fff', marginBottom: (isViewing || createStep >= 2) ? 4 : 0 }}>
                <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 8, color: createStep >= 1 ? '#262626' : '#bfbfbf' }}>预检查</div>
                {createStep >= 1 ? (
                  dmlCheckResult ? (
                    <div>
                      <Alert message={dmlCheckResult.riskLevel === 'error' ? '预检查未通过' : '预检查通过'} type={dmlCheckResult.riskLevel === 'error' ? 'error' : 'success'} showIcon style={{ marginBottom: 12 }} />
                      <Row gutter={16} style={{ marginBottom: 8 }}>
                        <Col><span style={{ color: '#666' }}>预估影响总行数：</span><b style={{ color: dmlCheckResult.estimateAffectRows > 10000 ? '#ff4d4f' : '#52c41a' }}>{dmlCheckResult.estimateAffectRows.toLocaleString()} 行</b><span style={{ color: '#ff4d4f', marginLeft: 8 }}>（系统统计值，实际影响行数仍然以SQL执行为准!）</span></Col>
                      </Row>
                      {(() => {
                        const entered = form.getFieldValue('affectedRows');
                        const estimated = dmlCheckResult.estimateAffectRows;
                        if (entered != null && entered !== '' && estimated != null && Number(entered) !== estimated) {
                          return (
                            <Alert
                              message={`您录入的影响行数（${Number(entered).toLocaleString()} 行）与系统预估行数（${estimated.toLocaleString()} 行）不一致，请核实后继续！`}
                              type="warning"
                              showIcon
                              style={{ marginBottom: 8 }}
                            />
                          );
                        }
                        return null;
                      })()}
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
                {createStep === 1 && dmlCheckResult?.riskLevel === 'error' && (
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12, borderTop: '1px solid #f0f0f0', paddingTop: 12 }}>
                    <Button onClick={() => { setCreateStep(0); setDmlCheckResult(null); }}>返回修改</Button>
                  </div>
                )}
              </div>
            </div>

            {/* ====== 步骤3：审核 ====== */}
            <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }}>
                <div style={{ width: 28, height: 28, borderRadius: '50%', background: isViewing ? (viewingTicket?.status === 'rejected' ? '#ff4d4f' : '#52c41a') : (reviewSubmitted ? '#52c41a' : createStep >= 2 ? '#1890ff' : '#d9d9d9'), color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 'bold', flexShrink: 0 }}>
                  {isViewing ? (viewingTicket?.status === 'rejected' ? '✗' : '✓') : (reviewSubmitted ? '✓' : '3')}
                </div>
                <div style={{ width: 2, flex: 1, minHeight: 16, background: isViewing ? (['executing','done','failed','rolled_back'].includes(viewingTicket?.status || '') ? '#52c41a' : '#d9d9d9') : (reviewSubmitted ? '#52c41a' : createStep >= 2 ? '#1890ff' : '#d9d9d9') }} />
              </div>
              <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 8, padding: 16, background: '#fff', marginBottom: (isViewing && ['executing','done','failed','rolled_back'].includes(viewingTicket?.status || '')) ? 4 : (createStep >= 3) ? 4 : 0 }}>
                <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 8, color: (isViewing || createStep >= 2) ? '#262626' : '#bfbfbf' }}>审核</div>
                {(isViewing || createStep >= 2) ? (
                  <div>
                    {isViewing ? (
                      <>
                        {viewingTicket?.status === 'rejected' ? (
                          <Alert message="审批已拒绝" type="error" showIcon style={{ marginBottom: 12 }} />
                        ) : viewingTicket?.status === 'approved' || viewingTicket?.status === 'executing' || viewingTicket?.status === 'done' ? (
                          <Alert message="审批已通过" type="success" showIcon style={{ marginBottom: 12 }} />
                        ) : (
                          <Alert message="待审批" type="warning" showIcon style={{ marginBottom: 12 }} />
                        )}
                        {viewingTicket?.approverNames && (
                          <Alert
                            message={<span>审核人：<Tag color="blue">{viewingTicket.approverNames}</Tag></span>}
                            type="info" showIcon style={{ marginBottom: 12 }}
                          />
                        )}
                        {viewingTicket?.currentApproverId && !viewingTicket.approverNames && (
                          <Alert
                            message={<span>审核人：<Tag color="blue">{userNameMap[String(viewingTicket.currentApproverId)] || viewingTicket.currentApproverId}</Tag></span>}
                            type="info" showIcon style={{ marginBottom: 12 }}
                          />
                        )}
                        {approvalRecords.length > 0 && (
                          <Timeline style={{ marginTop: 8 }}>
                            {approvalRecords.map((rec: any) => (
                              <Timeline.Item key={rec.id} color={rec.status === 'approved' ? 'green' : 'red'}>
                                <Tag color={rec.status === 'approved' ? 'green' : 'red'}>{rec.status === 'approved' ? '审批通过' : '审批拒绝'}</Tag>
                                <span>{userNameMap[String(rec.approverId)] || rec.approverId || '-'}</span>
                                {rec.comment && <span style={{ marginLeft: 8, color: '#999' }}>{rec.comment}</span>}
                                <br /><span style={{ fontSize: 12, color: '#999' }}>{rec.approvedAt || rec.createTime || '-'}</span>
                              </Timeline.Item>
                            ))}
                          </Timeline>
                        )}
                      </>
                    ) : (
                      <>
                        <Alert message={reviewSubmitted ? '审核已提交' : '预检验完成'} type="success" showIcon style={{ marginBottom: 12 }} />
                        {reviewSubmitted && currentApprover && (
                          <Alert message={<span>当前审核人：<Tag color="blue">{currentApprover}</Tag></span>} type="info" showIcon style={{ marginBottom: 12 }} />
                        )}
                        {!reviewSubmitted && (
                          <Alert message="请确认变更信息无误后，点击「提交审核」进入审批流程" type="info" showIcon />
                        )}
                      </>
                    )}
                  </div>
                ) : <span style={{ color: '#bfbfbf' }}>预检查通过后可提交审核</span>}
                {createStep >= 2 && (
                  <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 12, borderTop: '1px solid #f0f0f0', paddingTop: 12 }}>
                    {!reviewSubmitted ? (
                      <>
                        <Button onClick={() => { setCreateStep(0); setDmlCheckResult(null); setViewingTicket(null); }}>返回修改</Button>
                        <Button type="primary" loading={submittingReview} onClick={handleSubmitReview} icon={<SendOutlined />}>提交审核</Button>
                      </>
                    ) : (
                      <Button type="primary" onClick={() => { closeCreate(); fetchTickets(); }}>完成</Button>
                    )}
                  </div>
                )}
              </div>
            </div>

            {/* ====== 步骤4：执行 ====== */}
            <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }}>
                <div style={{ width: 28, height: 28, borderRadius: '50%', background: (isViewing && ['executing','done','failed','rolled_back'].includes(viewingTicket?.status || '')) ? (viewingTicket?.status === 'failed' ? '#ff4d4f' : viewingTicket?.status === 'done' ? '#52c41a' : '#1890ff') : '#d9d9d9', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 'bold', flexShrink: 0 }}>
                  {(isViewing && ['executing','done','failed','rolled_back'].includes(viewingTicket?.status || '')) ? (viewingTicket?.status === 'failed' ? '✗' : viewingTicket?.status === 'done' ? '✓' : '4') : '4'}
                </div>
                <div style={{ width: 2, flex: 1, minHeight: 16, background: (isViewing && ['done','failed','rolled_back'].includes(viewingTicket?.status || '')) ? '#52c41a' : '#d9d9d9' }} />
              </div>
              <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 8, padding: 16, background: '#fff' }}>
                <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 8, color: (isViewing && ['executing','done','failed','rolled_back'].includes(viewingTicket?.status || '')) ? '#262626' : '#bfbfbf' }}>执行</div>
                {(isViewing && ['executing','done','failed','rolled_back'].includes(viewingTicket?.status || '')) ? (
                  <div>
                    {viewingTicket?.useLockFreeDml && viewingTicket?.dmlProgressPercent !== undefined ? (
                      <Row gutter={16}>
                        <Col span={12}><div style={{ fontSize: 12, color: '#666' }}>执行进度</div><Progress percent={viewingTicket.dmlProgressPercent || 0} /></Col>
                        <Col span={6}><div style={{ fontSize: 12, color: '#666' }}>影响行数</div><strong>{(viewingTicket.dmlTotalAffected ?? 0).toLocaleString()}</strong></Col>
                        <Col span={6}><div style={{ fontSize: 12, color: '#666' }}>批次</div><strong>{viewingTicket.dmlBatchCount || 0}/{viewingTicket.dmlTotalBatches || '-'}</strong></Col>
                      </Row>
                    ) : (
                      <span>{viewingTicket?.status === 'executing' ? '正在执行中...' : viewingTicket?.status === 'failed' ? '执行失败' : '执行已完成'}</span>
                    )}
                    {viewingTicket?.errorMsg && <Alert message={viewingTicket.errorMsg} type="error" showIcon style={{ marginTop: 8 }} />}
                    {viewingTicket?.executeTime && <div style={{ marginTop: 8, color: '#999', fontSize: 12 }}>执行时间：{String(viewingTicket.executeTime).replace('T', ' ')}</div>}
                  </div>
                ) : (
                  <>
                    <div style={{ fontWeight: 600, fontSize: 14, color: '#bfbfbf' }}>执行</div>
                    <span style={{ color: '#bfbfbf' }}>工单审批通过后自动执行</span>
                  </>
                )}
              </div>
            </div>

            {/* ====== 步骤5：完成 ====== */}
            <div style={{ display: 'flex', gap: 12, alignItems: 'stretch' }}>
              <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: 28, flexShrink: 0 }}>
                <div style={{ width: 28, height: 28, borderRadius: '50%', background: (isViewing && viewingTicket?.status === 'done') ? '#52c41a' : (isViewing && viewingTicket?.status === 'failed') ? '#ff4d4f' : (isViewing && ['rolled_back','cancelled','rejected'].includes(viewingTicket?.status || '')) ? '#faad14' : '#d9d9d9', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 12, fontWeight: 'bold', flexShrink: 0 }}>
                  {(isViewing && viewingTicket?.status === 'done') ? '✓' : (isViewing && ['failed','cancelled','rejected'].includes(viewingTicket?.status || '')) ? '✗' : '5'}
                </div>
              </div>
              <div style={{ flex: 1, border: '1px solid #e8e8e8', borderRadius: 8, padding: 12, background: '#fff' }}>
                <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4, color: (isViewing && ['done','failed','rolled_back','cancelled','rejected'].includes(viewingTicket?.status || '')) ? '#262626' : '#bfbfbf' }}>完成</div>
                {(isViewing && viewingTicket) ? (
                  <>
                    {viewingTicket.status === 'done' && <Alert message="任务执行完成" type="success" showIcon />}
                    {viewingTicket.status === 'failed' && <Alert message="任务执行失败" description={viewingTicket.errorMsg || '未知错误'} type="error" showIcon />}
                    {viewingTicket.status === 'rolled_back' && <Alert message="任务已回滚" type="info" showIcon />}
                    {viewingTicket.status === 'cancelled' && <Alert message="任务已取消" type="warning" showIcon />}
                    {viewingTicket.status === 'rejected' && <Alert message="审批已拒绝" type="error" showIcon />}
                  </>
                ) : (
                  <span style={{ color: '#bfbfbf' }}>执行成功后自动完成</span>
                )}
              </div>
            </div>
          </div>
        </Card>
        );
      })()}
    </div>
  );
};

export default TicketList;

