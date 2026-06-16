import React, { useState, useEffect } from 'react';
import {
  Button, Table, message, Tabs, Tag, Form, Select, Checkbox, Input, Modal,
  Space, Card, Descriptions, Badge, Empty, Popconfirm, Tooltip,
  Steps, Row, Col, Timeline,
} from 'antd';
import {
  PlusOutlined, CheckOutlined, CloseOutlined, EyeOutlined,
  SendOutlined, ReloadOutlined, UndoOutlined,
  SearchOutlined, ArrowRightOutlined, ArrowLeftOutlined,
  CheckCircleFilled, CloseCircleFilled,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { permissionRequestApi, instanceApi } from '../utils/api';

const { Option } = Select;
const { TextArea } = Input;

interface PermissionRequest {
  id: string;
  resourceType: string;
  resourceId: string;
  resourceName: string;
  permissions: string[];
  reason: string;
  status: 'pending' | 'approved' | 'rejected' | 'cancelled';
  applicantId: string;
  applicantName: string;
  approverId?: string;
  approverName?: string;
  approverComment?: string;
  createTime: string;
  updateTime?: string;
}

const statusMap: Record<string, { color: string; text: string }> = {
  pending: { color: 'processing', text: '待审批' },
  approved: { color: 'success', text: '已通过' },
  rejected: { color: 'error', text: '已拒绝' },
  cancelled: { color: 'default', text: '已撤销' },
};

const resourceTypeLabels: Record<string, string> = {
  instance: '实例',
  database: 'Schema',
  table: '数据表',
  column: '字段',
};

const permissionOptions = [
  { label: '查询 (read)', value: 'read', description: '仅允许查看和查询数据，不能修改或导出' },
  { label: '导出 (export)', value: 'export', description: '允许将数据导出为文件格式' },
  { label: '变更 (write)', value: 'write', description: '允许增删改数据，但不包括结构变更' },
  { label: '结构变更 (ddl)', value: 'ddl', description: '允许执行CREATE、ALTER、DROP等结构变更操作' },
];

const expireOptions = [
  { label: '一个月', value: 30 },
  { label: '半年', value: 180 },
  { label: '一年', value: 365 },
  { label: '两年', value: 730 },
  { label: '三年', value: 1095 },
];

const PermissionRequestPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState('my');

  // Tab 1: 我的申请
  const [myRequests, setMyRequests] = useState<PermissionRequest[]>([]);
  const [myLoading, setMyLoading] = useState(false);

  // Tab 2: 新建申请
  const [submitForm] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const [instances, setInstances] = useState<any[]>([]);
  const [selectedResources, setSelectedResources] = useState<any[]>([]);
  const [checkedPerms, setCheckedPerms] = useState<string[]>(['read']);
  const [expireDays, setExpireDays] = useState<number>(30);
  const [expandedInstances, setExpandedInstances] = useState<Set<string>>(new Set());
  const [databaseCache, setDatabaseCache] = useState<Record<string, any[]>>({});
  const [searchKeyword, setSearchKeyword] = useState('');

  // Approve/Reject modal
  const [approveVisible, setApproveVisible] = useState(false);
  const [approveAction, setApproveAction] = useState<'approve' | 'reject'>('approve');
  const [selectedRequest, setSelectedRequest] = useState<PermissionRequest | null>(null);
  const [commentForm] = Form.useForm();

  // Detail modal
  const [detailVisible, setDetailVisible] = useState(false);
  const [detailRequest, setDetailRequest] = useState<PermissionRequest | null>(null);

  useEffect(() => {
    if (activeTab === 'my') loadMyRequests();
  }, [activeTab]);

  useEffect(() => {
    loadDatabases();
  }, []);

  const loadDatabases = async () => {
    try {
      const res = await instanceApi.listAll();
      const data = Array.isArray(res?.data?.data) ? res.data.data : [];
      setInstances(data.map((db: any) => ({
        id: db.id || db.name, name: db.name || db.id,
        type: db.type || 'mysql', host: db.host || '', port: db.port || 3306,
      })));
    } catch {
      // ignore
    }
  };

  const loadMyRequests = async () => {
    setMyLoading(true);
    try {
      const res = await permissionRequestApi.my();
      setMyRequests(Array.isArray(res?.data?.data) ? res.data.data : []);
    } catch {
      setMyRequests([]);
    } finally {
      setMyLoading(false);
    }
  };

  const toggleInstance = async (id: string) => {
    const s = new Set(expandedInstances);
    const isExpanding = !s.has(id);
    s.has(id) ? s.delete(id) : s.add(id);
    setExpandedInstances(s);

    // 展开时加载数据库列表
    if (isExpanding && !databaseCache[id]) {
      try {
        const res = await instanceApi.getSchemasAll(id);
        const dbs = Array.isArray(res?.data?.data) ? res.data.data : [];
        // 后端返回的是字符串数组，转换为对象数组格式
        const formattedDbs = dbs.map((db: string | { name: string }) =>
          typeof db === 'string' ? { name: db } : db
        );
        setDatabaseCache(prev => ({ ...prev, [id]: formattedDbs }));
      } catch {
        setDatabaseCache(prev => ({ ...prev, [id]: [] }));
      }
    }
  };

  const toggleResourceCheck = (instanceId: string, databaseId: string, databaseName: string, checked: boolean, resourceType: 'instance' | 'database') => {
    if (!checked) {
      if (resourceType === 'instance') {
        // 取消实例级：移除该实例下所有已选资源
        setSelectedResources(prev => prev.filter(r => r.instanceId !== instanceId));
      } else {
        // 取消数据库级：移除匹配 instanceId + databaseId + resourceType 的那一项
        setSelectedResources(prev =>
          prev.filter(r => !(r.instanceId === instanceId && r.databaseId === databaseId && r.resourceType === 'database'))
        );
      }
      return;
    }

    const inst = instances.find(i => i.id === instanceId);
    if (inst) {
      // 如果选择的是实例级别，先取消该实例下所有已选的数据库
      if (resourceType === 'instance') {
        setSelectedResources(prev => {
          const filtered = prev.filter(r => r.instanceId !== instanceId);
          return [...filtered, { instanceId: inst.id, instanceName: inst.name, databaseId: inst.id, databaseName: inst.name, resourceType: 'instance' }];
        });
      } else {
        // 如果选择的是数据库级别，先取消实例级别的选中状态
        setSelectedResources(prev => {
          const filtered = prev.filter(r => !(r.instanceId === instanceId && r.resourceType === 'instance'));
          return [...filtered, { instanceId: inst.id, instanceName: inst.name, databaseId: databaseId, databaseName: databaseName, resourceType: 'database' }];
        });
      }
    }
  };

  const handleSubmit = async (values: any) => {
    if (selectedResources.length === 0) { message.warning('OK'); return; }
    if (checkedPerms.length === 0) { message.warning('OK'); return; }
    setSubmitting(true);
    try {
      await permissionRequestApi.submitTicket({
        reason: values.reason || '',
        ticketType: 'database',
        resources: selectedResources.map(r => ({
          resourceType: r.resourceType || 'database',
          instanceId: r.instanceId,
          instanceName: r.instanceName,
          schemaName: r.resourceType === 'instance' ? undefined : r.databaseName,
        })),
        permissionTypes: checkedPerms,
        expireDays: expireDays > 0 ? expireDays : undefined,
      });
      message.success('OK');
      setSelectedResources([]);
      setCheckedPerms(['read']);
      setExpireDays(30);
      submitForm.resetFields();
      setActiveTab('my');
      loadMyRequests();
    } catch (e: any) {
      message.error(e?.response?.data?.message || '提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  const handleCancel = async (id: string) => {
    try {
      await permissionRequestApi.cancel(id);
      message.success('OK');
      loadMyRequests();
    } catch (e: any) {
      message.error(e?.response?.data?.message || '撤销失败');
    }
  };

  const openApproveModal = (record: PermissionRequest, action: 'approve' | 'reject') => {
    setSelectedRequest(record);
    setApproveAction(action);
    setApproveVisible(true);
    commentForm.resetFields();
  };

  const handleApproveOrReject = async (values: { comment?: string }) => {
    if (!selectedRequest) return;
    try {
      if (approveAction === 'approve') {
        await permissionRequestApi.approve(selectedRequest.id, { comment: values.comment || '' });
        message.success('OK');
      } else {
        await permissionRequestApi.reject(selectedRequest.id, { comment: values.comment || '' });
        message.success('OK');
      }
      setApproveVisible(false);
      commentForm.resetFields();
    } catch (e: any) {
      message.error(e?.response?.data?.message || '操作失败');
    }
  };

  const showDetail = (record: PermissionRequest) => {
    setDetailRequest(record);
    setDetailVisible(true);
  };

  const myColumns = [
    {
      title: '工单号', dataIndex: 'id', key: 'id', width: 150,
      render: (id: string) => <span style={{ fontFamily: 'monospace' }}>{id ? id.slice(0, 10) : '-'}</span>,
    },
    {
      title: '原因', dataIndex: 'resourceName', key: 'reason', width: 240,
      render: (_: string, r: PermissionRequest) => (
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <Tag color="default" style={{ marginRight: 0 }}>{resourceTypeLabels[r.resourceType] || r.resourceType}</Tag>
          <span style={{ color: '#222' }}>{r.resourceName || '-'}</span>
          {r.reason && (
            <Tooltip title={r.reason}>
              <span style={{ color: '#999', fontSize: 12, marginLeft: 8 }}>
                ({r.reason.length > 20 ? r.reason.slice(0, 20) + '...' : r.reason})
              </span>
            </Tooltip>
          )}
        </div>
      ),
    },
    {
      title: '工单类型', dataIndex: 'resourceType', key: 'ticketType', width: 120,
      render: (t: string) => {
        const label = resourceTypeLabels[t] || t;
        return <Tag color="geekblue">{label}权限</Tag>;
      },
    },
    {
      title: '当前状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => {
        const st = statusMap[s] || { color: 'default', text: s };
        return <Badge status={st.color as any} text={st.text} />;
      },
    },
    {
      title: '发起人', dataIndex: 'applicantName', key: 'applicantName', width: 110,
      render: (n: string, r: PermissionRequest) => n || r.applicantId || '-',
    },
    {
      title: '当前处理人', dataIndex: 'approverName', key: 'approverName', width: 110,
      render: (n: string, r: PermissionRequest) => n || r.approverId || '-',
    },
    {
      title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170,
      render: (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-',
      sorter: (a: any, b: any) => dayjs(a.createTime).valueOf() - dayjs(b.createTime).valueOf(),
    },
    {
      title: '最后操作时间', dataIndex: 'updateTime', key: 'updateTime', width: 170,
      render: (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-',
      sorter: (a: any, b: any) => dayjs(a.updateTime || a.createTime).valueOf() - dayjs(b.updateTime || b.createTime).valueOf(),
    },
    {
      title: '操作', key: 'action', width: 130, fixed: 'right' as const,
      render: (_: any, record: PermissionRequest) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => showDetail(record)}>
            详情
          </Button>
          {record.status === 'pending' && (
            <Popconfirm title="确定撤销这申请？" onConfirm={() => handleCancel(record.id)}>
              <Button type="link" danger size="small">撤销</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  const tabItems: any[] = [
    {
      key: 'my',
      label: '我的申请',
      children: (
        <Card>
          <div style={{ marginBottom: 16 }}>
            <Button icon={<ReloadOutlined />} onClick={loadMyRequests} loading={myLoading}>
              刷新
            </Button>
          </div>
          <Table
            dataSource={myRequests}
            columns={myColumns}
            rowKey="id"
            loading={myLoading}
            pagination={{ pageSize: 15, showTotal: (t: number) => `共 ${t} 条申请` }}
            scroll={{ x: 1500 }}
            size="small"
            locale={{ emptyText: <Empty description="暂无申请记录" /> }}
          />
        </Card>
      ),
    },
    {
      key: 'create',
      label: (
        <span><PlusOutlined /> 新建申请</span>
      ),
      children: (
        <>
        <Card style={{ maxWidth: '100%' }}>
          <Form
            form={submitForm}
            layout="horizontal"
            labelCol={{ span: 4 }}
            wrapperCol={{ span: 20 }}
            onFinish={handleSubmit}
            initialValues={{ resourceType: undefined, permissions: [] }}
          >
            {/* 左右分栏选择资源 */}
            <Form.Item label="选择要申请的实例/Schema" required>
              <div style={{ display: 'flex', gap: 16, height: 320 }}>
                {/* 左侧：实例列表 */}
                <div style={{ width: 500, display: 'flex', flexDirection: 'column' }}>
                  <div style={{ flex: 1, border: '1px solid #d9d9d9', borderRadius: 4, padding: '4px 8px', overflow: 'auto' }}>
                    {instances.length === 0 ? (
                      <Empty description="暂无可用数据库实例" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                      instances
                        .filter(inst => {
                          if (!searchKeyword) return true;
                          const kw = searchKeyword.toLowerCase();
                          // 搜索实例名称、地址
                          const matchInstance = inst.name.toLowerCase().includes(kw) ||
                                               `${inst.host}:${inst.port}`.toLowerCase().includes(kw);
                          if (matchInstance) return true;
                          // 搜索数据库名称
                          const dbs = databaseCache[inst.id] || [];
                          return dbs.some((db: any) => db.name && db.name.toLowerCase().includes(kw));
                        })
                        .map(inst => (
                          <div key={inst.id} style={{ marginBottom: 2 }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: 6, padding: 4 }}>
                              <Checkbox
                                checked={selectedResources.some(r => r.instanceId === inst.id && r.resourceType === 'instance')}
                                onChange={e => toggleResourceCheck(inst.id, inst.id, inst.name, e.target.checked, 'instance')}
                              />
                              <span style={{ fontWeight: 500, cursor: 'pointer', fontSize: 13 }} onClick={() => toggleInstance(inst.id)}>
                                {expandedInstances.has(inst.id) ? '▼' : '▶'} {inst.name}
                              </span>
                              <span style={{ color: '#999', fontSize: 12 }}>{inst.host}:{inst.port}</span>
                            </div>
                            {/* 展开显示数据库列表 */}
                            {expandedInstances.has(inst.id) && (
                              <div style={{ marginLeft: 28, paddingLeft: 8, borderLeft: '1px dashed #d9d9d9' }}>
                                {databaseCache[inst.id] && databaseCache[inst.id].length > 0 ? (
                                  databaseCache[inst.id].map((db: any) => (
                                    <div key={db.name} style={{ display: 'flex', alignItems: 'center', gap: 6, padding: 2 }}>
                                      <Checkbox
                                        checked={selectedResources.some(r => r.databaseId === db.name && r.instanceId === inst.id && r.resourceType === 'database')}
                                        onChange={e => toggleResourceCheck(inst.id, db.name, db.name, e.target.checked, 'database')}
                                      />
                                      <span style={{ fontSize: 12 }}>{db.name}</span>
                                    </div>
                                  ))
                                ) : (
                                  <span style={{ color: '#999', fontSize: 11 }}>加载中...</span>
                                )}
                              </div>
                            )}
                          </div>
                        ))
                    )}
                  </div>
                </div>

                {/* 中间：添加/移除按钮 */}
                <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: 8 }}>
                  <Button
                    type="primary"
                    size="small"
                    icon={<ArrowRightOutlined />}
                    disabled={selectedResources.length === 0}
                    onClick={() => {}}
                    style={{ opacity: 0.4, cursor: 'not-allowed' }}
                  >
                  </Button>
                  <Button
                    type="default"
                    size="small"
                    icon={<ArrowLeftOutlined />}
                    disabled={selectedResources.length === 0}
                    onClick={() => setSelectedResources([])}
                  >
                  </Button>
                </div>

                {/* 右侧：已选择的资源 */}
                <div style={{ width: 350, display: 'flex', flexDirection: 'column' }}>
                  <div style={{ marginBottom: 8, display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                    <span style={{ fontWeight: 500 }}>已选择的实例/Schema</span>
                    <Input
                      placeholder="搜索"
                      style={{ width: 120 }}
                      size="small"
                      prefix={<SearchOutlined />}
                    />
                  </div>
                  <div style={{ flex: 1, border: '1px solid #d9d9d9', borderRadius: 4, padding: '4px 8px', overflow: 'auto' }}>
                    {selectedResources.length === 0 ? (
                      <Empty description="没有查询到符合条件的数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                    ) : (
                      <div>
                        {/* 已选资源列表 */}
                        {selectedResources.map(r => (
                          <div key={`${r.instanceId}-${r.databaseId}-${r.resourceType}`}
                            style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: 4, borderBottom: '1px solid #f0f0f0' }}
                          >
                            <span style={{ fontSize: 12 }}>
                              {r.resourceType === 'instance'
                                ? r.instanceName
                                : `${r.instanceName} / ${r.databaseName}`
                              }
                            </span>
                            <Button
                              type="link"
                              size="small"
                              danger
                              onClick={() => toggleResourceCheck(r.instanceId, r.databaseId, r.databaseName, false, r.resourceType)}
                            >
                              删除
                            </Button>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                </div>
              </div>
            </Form.Item>

            {/* 权限类型选择 */}
            <Form.Item label="选择权限" required>
              <Checkbox.Group value={checkedPerms} onChange={v => setCheckedPerms(v as string[])}>
                {permissionOptions.map(option => (
                  <Tooltip key={option.value} title={option.description}>
                    <Checkbox value={option.value}>{option.label}</Checkbox>
                  </Tooltip>
                ))}
              </Checkbox.Group>
            </Form.Item>

            <Form.Item label="期限">
              <Select value={expireDays} onChange={setExpireDays} style={{ width: 150 }}>
                {expireOptions.map(o => <Option key={o.value} value={o.value}>{o.label}</Option>)}
              </Select>
              <span style={{ marginLeft: 8, color: '#999', fontSize: 12 }}>
                {expireDays > 0 ? `到期: ${dayjs().add(expireDays, 'day').format('YYYY-MM-DD')}` : '⚠ 永久有效，请谨慎授予'}
              </span>
            </Form.Item>

            <Form.Item name="reason" label="申请原因" rules={[{ required: true, message: '请填写申请原因' }]}>
              <TextArea rows={3} placeholder="请输入申请原因以及背景，以减少审批流程中的沟通成本。"
                showCount
                maxLength={500}
              />
            </Form.Item>

            <Form.Item style={{ textAlign: 'center', padding: '16px 0' }}>
              <Button
                type="primary"
                htmlType="submit"
                icon={<SendOutlined />}
                loading={submitting}
                size="large"
              >
                提交申请
              </Button>
            </Form.Item>

          </Form>
        </Card>
      </>
      ),
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>
        <SendOutlined /> 权限申请
      </h2>
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={tabItems}
      />

      {/* 审批/拒绝 Modal */}
      <Modal
        title={approveAction === 'approve' ? '通过申请' : '拒绝申请'}
        open={approveVisible}
        onCancel={() => setApproveVisible(false)}
        onOk={() => commentForm.submit()}
        okText={approveAction === 'approve' ? '确认通过' : '确认拒绝'}
        okButtonProps={{
          danger: approveAction === 'reject',
        }}
        destroyOnClose
      >
        {selectedRequest && (
          <div style={{ marginBottom: 16 }}>
            <Descriptions size="small" column={2}>
              <Descriptions.Item label="申请人">
                {selectedRequest.applicantName || selectedRequest.applicantId}
              </Descriptions.Item>
              <Descriptions.Item label="资源类型">
                <Tag>{resourceTypeLabels[selectedRequest.resourceType] || selectedRequest.resourceType}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="资源名称" span={2}>
                {selectedRequest.resourceName}
              </Descriptions.Item>
              <Descriptions.Item label="申请权限" span={2}>
                <Space size={[2, 2]} wrap>
                  {(selectedRequest.permissions || []).map((p) => (
                    <Tag key={p} color="blue">{p}</Tag>
                  ))}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="申请原因" span={2}>
                {selectedRequest.reason || '-'}
              </Descriptions.Item>
            </Descriptions>
          </div>
        )}
        <Form form={commentForm} layout="vertical" onFinish={handleApproveOrReject}>
          <Form.Item
            name="comment"
            label="审批意见"
            rules={approveAction === 'reject' ? [{ required: true, message: '请填写拒绝原因' }] : []}
          >
            <TextArea
              rows={3}
              placeholder={approveAction === 'approve' ? '可选，填写审批意见' : '请填写拒绝原因'}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 详情 Modal */}
      <Modal
        title="申请详情"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={null}
        width={820}
        destroyOnClose
      >
        {detailRequest && <RequestDetail request={detailRequest} />}
      </Modal>
    </div>
  );
};

export default PermissionRequestPage;

/** ---------------- 详情组件：步骤流风格 ---------------- */

interface DetailResourceInfo {
  instanceId?: string;
  instanceName?: string;
  databaseId?: string;
  databaseName?: string;
  environment?: string;
  tables?: string[];
}

function parseReasonExtra(reason: string): { info: DetailResourceInfo | null; plainText: string } {
  if (!reason) return { info: null, plainText: '' };
  if (reason.startsWith('[JSON]')) {
    const end = reason.indexOf('}', 6);
    if (end > 0) {
      try {
        const jsonStr = reason.substring(6, end + 1);
        const obj = JSON.parse(jsonStr);
        const info: DetailResourceInfo = {
          instanceId: obj.instanceId || obj.databaseId,
          instanceName: obj.instanceName || obj.databaseName,
          databaseName: obj.databaseName || obj.schemaName,
          environment: obj.environment,
          tables: obj.tables,
        };
        const rest = reason.substring(end + 1).trim();
        return { info, plainText: rest };
      } catch {
        return { info: null, plainText: reason };
      }
    }
  }
  return { info: null, plainText: reason };
}

const RequestDetail: React.FC<{ request: PermissionRequest }> = ({ request }) => {
  const { info, plainText } = parseReasonExtra(request.reason || '');
  const createTime = (request as any).createdAt || (request as any).createTime;
  const updateTime = (request as any).approvedAt || (request as any).updateTime;
  const perms = request.permissions && request.permissions.length > 0
    ? request.permissions
    : (request as any).requestedPermissions
      ? String((request as any).requestedPermissions).split(',').map((s: string) => s.trim()).filter(Boolean)
      : [];
  const permText = perms
    .map((p: string) => ({
      read: '查询', query: '查询',
      export: '导出', write: '变更', update: '变更',
      ddl: '结构变更'
    } as any)[p] || p)
    .join(' / ');
  const typeText = (resourceTypeLabels as any)[request.resourceType] || request.resourceType || '-';
  const resourceText = request.resourceName
    ? request.resourceName
    : (info?.instanceName || request.resourceId || '-');

  const sectionStyle: React.CSSProperties = {
    background: '#fff', border: '1px solid #e8e8e8', borderRadius: 4
  };
  const sectionHeader: React.CSSProperties = {
    padding: '8px 16px', borderBottom: '1px solid #f0f0f0', fontSize: 13, fontWeight: 500,
    display: 'flex', alignItems: 'center', justifyContent: 'space-between'
  };

  let approveBody: React.ReactNode;
  let approveDotColor: string = 'gray';
  if (request.status === 'pending') {
    const approver = (request as any).approverName || (request as any).approverId;
    approveBody = (
      <div>
        <Tag color="processing">待审批</Tag>
        <span style={{ color: '#666', marginLeft: 8 }}>
          {approver ? `等待 ${approver} 审批中...` : '等待资源 Owner 或管理员审批...'}
        </span>
      </div>
    );
    approveDotColor = 'blue';
  } else if (request.status === 'approved') {
    approveBody = (
      <div>
        <Tag color="success">审批通过</Tag>
        <span style={{ color: '#222', marginLeft: 8 }}>
          审批人：{request.approverName || request.approverId || '-'}
        </span>
        {updateTime && (
          <span style={{ color: '#999', marginLeft: 12 }}>
            ({dayjs(updateTime).format('YYYY-MM-DD HH:mm:ss')})
          </span>
        )}
        {request.approverComment && (
          <div style={{ marginTop: 6, color: '#555', background: '#fafafa', padding: '6px 10px', borderRadius: 4 }}>
            审批意见：{request.approverComment}
          </div>
        )}
      </div>
    );
    approveDotColor = 'green';
  } else if (request.status === 'rejected') {
    approveBody = (
      <div>
        <Tag color="error">审批拒绝</Tag>
        <span style={{ color: '#222', marginLeft: 8 }}>
          审批人：{request.approverName || request.approverId || '-'}
        </span>
        {updateTime && (
          <span style={{ color: '#999', marginLeft: 12 }}>
            ({dayjs(updateTime).format('YYYY-MM-DD HH:mm:ss')})
          </span>
        )}
        {request.approverComment && (
          <div style={{ marginTop: 6, color: '#555', background: '#fff2f0', padding: '6px 10px', borderRadius: 4 }}>
            审批意见：{request.approverComment}
          </div>
        )}
      </div>
    );
    approveDotColor = 'red';
  } else {
    approveBody = (
      <div>
        <Tag>已撤销</Tag>
        <span style={{ color: '#666', marginLeft: 8 }}>申请人主动撤销了该申请</span>
      </div>
    );
    approveDotColor = 'gray';
  }

  let finishText = '';
  let finishColor = '#999';
  if (request.status === 'approved') { finishText = '✓ 分配权限成功'; finishColor = '#52c41a'; }
  else if (request.status === 'rejected') { finishText = '✗ 申请已拒绝，未分配权限'; finishColor = '#f5222d'; }
  else if (request.status === 'pending') { finishText = '等待审批完成...'; }
  else if (request.status === 'cancelled') { finishText = '申请已撤销'; }

  return (
    <div style={{ background: '#f5f7fa', padding: 16, borderRadius: 4 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', marginBottom: 8 }}>
        <Badge
          status={(statusMap[request.status]?.color || 'default') as any}
          text={statusMap[request.status]?.text || request.status}
        />
      </div>

      <Timeline
        mode="left"
        style={{ paddingLeft: 4 }}
        items={[
          {
            color: 'green',
            dot: <CheckCircleFilled style={{ color: '#52c41a', fontSize: 16 }} />,
            children: (
              <div style={sectionStyle}>
                <div style={sectionHeader}><span>基本信息</span></div>
                <div style={{ padding: 12, fontSize: 12, lineHeight: 1.9 }}>
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>提交时间</Col>
                    <Col span={18} style={{ color: '#222' }}>
                      {createTime ? dayjs(createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
                    </Col>
                  </Row>
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>基本信息</Col>
                    <Col span={18} style={{ color: '#222' }}>
                      {request.applicantName || request.applicantId} 申请 {resourceText} 的 {permText || '权限'}
                    </Col>
                  </Row>
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>背景描述</Col>
                    <Col span={18} style={{ color: '#222' }}>{plainText || '-'}</Col>
                  </Row>

                  <div style={{ marginTop: 6 }}>
                    <div style={{ color: '#666', marginBottom: 4 }}>权限对象：{typeText}，共 1 个</div>
                    <Table
                      size="small"
                      pagination={false}
                      dataSource={[{
                        key: request.id,
                        instance: info?.instanceName || request.resourceName || '-',
                        environment: info?.environment || '-',
                        dba: request.approverName || '-'
                      }]}
                      columns={[
                        { title: '序号', dataIndex: 'index', width: 60, render: (_v, _r, i) => i + 1 },
                        { title: '实例地址 / 资源', dataIndex: 'instance' },
                        { title: '环境', dataIndex: 'environment', width: 100, render: v => v && v !== '-' ? (
                          <Tag color="orange">{v}</Tag>
                        ) : '-' },
                        { title: 'DBA', dataIndex: 'dba', width: 160 },
                      ]}
                    />
                  </div>
                </div>
              </div>
            )
          },
          {
            color: approveDotColor,
            dot: (request.status === 'approved' || request.status === 'rejected') ? (
              request.status === 'approved'
                ? <CheckCircleFilled style={{ color: '#52c41a', fontSize: 16 }} />
                : <CloseCircleFilled style={{ color: '#f5222d', fontSize: 16 }} />
            ) : undefined,
            children: (
              <div style={sectionStyle}>
                <div style={sectionHeader}><span>审批</span></div>
                <div style={{ padding: 12, fontSize: 12, lineHeight: 1.8 }}>{approveBody}</div>
              </div>
            )
          },
          {
            color: request.status === 'approved' ? 'green' : (request.status === 'rejected' ? 'red' : 'gray'),
            dot: request.status === 'approved'
              ? <CheckCircleFilled style={{ color: '#52c41a', fontSize: 16 }} />
              : request.status === 'rejected'
                ? <CloseCircleFilled style={{ color: '#f5222d', fontSize: 16 }} />
                : undefined,
            children: (
              <div style={sectionStyle}>
                <div style={sectionHeader}><span>完成</span></div>
                <div style={{ padding: 12, fontSize: 12, lineHeight: 1.8 }}>
                  <span style={{ color: finishColor }}>{finishText}</span>
                </div>
              </div>
            )
          }
        ]}
      />
    </div>
  );
};
