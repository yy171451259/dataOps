import React, { useState, useEffect } from 'react';
import {
  Button, Table, message, Tabs, Tag, Form, Select, Checkbox, Input, Modal,
  Space, Card, Descriptions, Badge, Empty, Popconfirm, Tooltip,
  Steps, Row, Col, Timeline, DatePicker,
} from 'antd';
import {
  PlusOutlined, CheckOutlined, CloseOutlined, EyeOutlined,
  SendOutlined, ReloadOutlined, UndoOutlined,
  SearchOutlined, ArrowRightOutlined, ArrowLeftOutlined,
  CheckCircleFilled, CloseCircleFilled,
} from '@ant-design/icons';
import dayjs from 'dayjs';
import { permissionRequestApi, instanceApi, userApi } from '../utils/api';
import RequestDetail, { PermissionRequest, resourceTypeLabels } from '../components/RequestDetail';

const { Option } = Select;
const { TextArea } = Input;

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
  const [myPage, setMyPage] = useState<number>(1);
  const [mySize, setMySize] = useState<number>(10);
  const [myTotal, setMyTotal] = useState<number>(0);
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [searchText, setSearchText] = useState('');
  const [users, setUsers] = useState<any[]>([]);

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
    loadUsers();
  }, []);

  const loadUsers = async () => {
    try {
      const res = await userApi.list();
      setUsers(Array.isArray(res?.data?.data) ? res.data.data : []);
    } catch {
      setUsers([]);
    }
  };

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

  const loadMyRequests = async (page?: number, size?: number) => {
    setMyLoading(true);
    try {
      const params: any = {
        page: page || myPage,
        size: size || mySize,
      };
      if (statusFilter && statusFilter !== 'all') {
        params.status = statusFilter;
      }
      if (searchText) {
        params.keyword = searchText;
      }
      const res = await permissionRequestApi.my(params);
      const data = res?.data?.data;
      if (data && Array.isArray(data.list)) {
        setMyRequests(data.list);
        setMyTotal(Number(data.total) || 0);
      } else if (Array.isArray(data)) {
        setMyRequests(data);
        setMyTotal(data.length);
      } else {
        setMyRequests([]);
        setMyTotal(0);
      }
    } catch {
      setMyRequests([]);
      setMyTotal(0);
    } finally {
      setMyLoading(false);
    }
  };

  useEffect(() => {
    if (activeTab === 'my') loadMyRequests(1);
  }, [activeTab, statusFilter]);

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
      title: '工单编号', dataIndex: 'id', key: 'id', width: 160,
      render: (id: string) => <a onClick={() => { const r = myRequests.find(req => req.id === id); if (r) showDetail(r); }} style={{ color: '#1890ff' }}>{id}</a>,
    },
    {
      title: '资源类型', dataIndex: 'resourceType', key: 'resourceType', width: 120,
      render: (type: string) => <span>{resourceTypeLabels[type] || (type || '-').toUpperCase()}</span>,
    },
    {
      title: '资源名称', dataIndex: 'resourceName', key: 'resourceName', width: 160, ellipsis: true, render: (v: string) => v || '-',
    },
    {
      title: '操作类型', key: 'permissions', width: 160,
      render: (_: any, record: PermissionRequest) => {
        const perms = record.permissions && record.permissions.length > 0
          ? record.permissions
          : (record as any).requestedPermissions
            ? String((record as any).requestedPermissions).split(',').map((s: string) => s.trim()).filter(Boolean)
            : [];
        const permText = perms
          .map((p: string) => ({
            read: '查询', query: '查询',
            export: '导出', write: '变更', update: '变更',
            ddl: '结构变更'
          } as any)[p] || p)
          .join(' / ');
        return <span>{permText || '-'}</span>;
      },
    },
    {
      title: '当前状态', dataIndex: 'status', key: 'status', width: 100,
      render: (status: string) => {
        const s: Record<string, { color: string; text: string }> = {
          pending: { color: '#faad14', text: '待审批' },
          approved: { color: '#52c41a', text: '已通过' },
          rejected: { color: '#ff4d4f', text: '已拒绝' },
          cancelled: { color: '#999', text: '已撤销' },
        };
        const { color, text } = s[status] || { color: '#999', text: status || '未知' };
        return <Tag style={{ color }}>{text}</Tag>;
      },
    },
    {
      title: '发起人', dataIndex: 'applicantName', key: 'applicantName', width: 100,
      render: (n: string, r: PermissionRequest) => n || r.applicantId || '-',
    },
    {
      title: '当前处理人', dataIndex: 'approverName', key: 'approverName', width: 100,
      render: (n: string, r: PermissionRequest) => n || r.approverId || '-',
    },
    {
      title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160,
      render: (t: string) => t ? String(t).replace('T', ' ').slice(0, 19) : '-',
    },
    {
      title: '操作', key: 'action', width: 80, fixed: 'right' as const,
      render: (_: any, record: PermissionRequest) => (
        <span>
          <a onClick={() => showDetail(record)} style={{ color: '#1890ff' }}>详情</a>
          {record.status === 'pending' && (
            <Popconfirm title="确定撤销这申请？" onConfirm={() => handleCancel(record.id)}>
              <a style={{ color: '#ff4d4f', marginLeft: 8 }}>撤销</a>
            </Popconfirm>
          )}
        </span>
      ),
    },
  ];

  const tabItems: any[] = [
    {
      key: 'my',
      label: '我的申请',
      children: (
        <Card title="我的权限申请工单" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setActiveTab('create')}>新建申请</Button>}>
          <div style={{ marginBottom: 16 }}>
            <Space><span>状态：</span>
              <Select value={statusFilter} onChange={v => setStatusFilter(v)} style={{ width: 140 }} size="middle">
                <Option value="all">全部</Option><Option value="pending">待审批</Option>
                <Option value="approved">已通过</Option><Option value="rejected">已拒绝</Option>
                <Option value="cancelled">已撤销</Option>
              </Select>
            </Space>
          </div>
          <Table
            dataSource={myRequests}
            columns={myColumns}
            rowKey="id"
            loading={myLoading}
            scroll={{ x: 1200 }}
            pagination={{
              current: myPage,
              pageSize: mySize,
              total: myTotal,
              showTotal: (t: number) => `共 ${t} 条`,
              showSizeChanger: true,
              onChange: (p: number, s: number) => {
                setMyPage(p);
                setMySize(s);
                loadMyRequests(p, s);
              },
            }}
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
        <Card title="新建权限申请" extra={<Button icon={<ArrowLeftOutlined />} onClick={() => setActiveTab('my')}>返回列表</Button>} style={{ maxWidth: '100%' }}>
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
                <div style={{ width: 350, display: 'flex', flexDirection: 'column' }}>
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
    <div style={{ height: 'calc(100vh - 64px)', overflow: 'auto' }}>
      {tabItems.filter(item => item.key === activeTab).map(item => (
        <div key={item.key}>{item.children}</div>
      ))}

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
