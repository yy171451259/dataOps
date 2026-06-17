import React, { useEffect, useState } from 'react';
import { Card, Table, Tag, Spin, Badge, Button, Space, Modal, Form, Input, Descriptions, Tooltip, message } from 'antd';
import {
  AuditOutlined, EyeOutlined,
  CheckOutlined, CloseOutlined, ReloadOutlined,
} from '@ant-design/icons';
import { ticketApi, permissionRequestApi } from '../utils/api';
import { useAuthStore } from '../store/useAuthStore';
import { useNavigate } from 'react-router-dom';
import dayjs from 'dayjs';

const { TextArea } = Input;

const DashboardPage: React.FC = () => {
  const navigate = useNavigate();
  const { hasPermission } = useAuthStore();

  const [loading, setLoading] = useState(true);
  const [pendingItems, setPendingItems] = useState<any[]>([]);
  const [pendingLoading, setPendingLoading] = useState(false);

  const [approveVisible, setApproveVisible] = useState(false);
  const [approveAction, setApproveAction] = useState<'approve' | 'reject'>('approve');
  const [selectedItem, setSelectedItem] = useState<any | null>(null);
  const [commentForm] = Form.useForm();

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    setPendingLoading(true);
    try {
      const [pendingTicketRes, permRes] = await Promise.all([
        ticketApi.pending().catch(() => ({ data: { data: [] } })),
        permissionRequestApi.pending().catch(() => ({ data: { data: [] } })),
      ]);

      const tickets = (() => {
        const data = pendingTicketRes.data?.data;
        return Array.isArray(data) ? data : data?.records || [];
      })();
      const permissions = (() => {
        const data = permRes.data?.data;
        return Array.isArray(data) ? data : data?.records || [];
      })();

      const merged = [
        ...tickets.map((t: any) => ({
          ...t,
          source: 'ticket' as const,
          typeLabel: t.changeType === 'DML' ? '数据变更' : t.changeType === 'DDL' ? '结构变更' : '数据变更',
          applicantName: t.creatorName || t.creatorId || '-',
          content: t.description || t.title || '-',
          createTime: t.createTime,
        })),
        ...permissions.map((p: any) => ({
          ...p,
          source: 'permission' as const,
          typeLabel: '权限申请',
          applicantName: p.applicantName || p.applicantId || '-',
          content: `${p.resourceName || p.resourceId || '-'} (${p.permissions?.join(', ') || '-'})`,
          createTime: p.createTime,
        })),
      ].sort((a, b) => new Date(b.createTime || 0).getTime() - new Date(a.createTime || 0).getTime());

      setPendingItems(merged);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
      setPendingLoading(false);
    }
  };

  const openApproveModal = (record: any, action: 'approve' | 'reject') => {
    setSelectedItem(record);
    setApproveAction(action);
    setApproveVisible(true);
    commentForm.resetFields();
  };

  const handleApproveOrReject = async (values: { comment?: string }) => {
    if (!selectedItem) return;
    try {
      if (selectedItem.source === 'permission') {
        if (approveAction === 'approve') {
          await permissionRequestApi.approve(selectedItem.id, { comment: values.comment || '' });
        } else {
          await permissionRequestApi.reject(selectedItem.id, { comment: values.comment || '' });
        }
      } else {
        if (approveAction === 'approve') {
          await ticketApi.approve(selectedItem.id, values.comment || '');
        } else {
          await ticketApi.reject(selectedItem.id, values.comment || '');
        }
      }
      message.success(approveAction === 'approve' ? '已通过' : '已拒绝');
      setApproveVisible(false);
      commentForm.resetFields();
      loadData();
    } catch (e: any) {
      message.error(e?.response?.data?.message || '操作失败');
    }
  };

  const pendingColumns = [
    {
      title: '类型',
      key: 'type',
      width: 110,
      render: (_: any, record: any) => (
        <Tag color={record.source === 'permission' ? 'blue' : 'orange'}>{record.typeLabel}</Tag>
      ),
    },
    {
      title: '申请内容',
      key: 'content',
      ellipsis: true,
      render: (_: any, record: any) => (
        <Tooltip title={record.content}>
          <span>{record.content}</span>
        </Tooltip>
      ),
    },
    {
      title: '申请人',
      key: 'applicant',
      width: 110,
      render: (_: any, record: any) => record.applicantName || '-',
    },
    {
      title: '申请时间',
      key: 'createTime',
      width: 170,
      render: (_: any, record: any) => record.createTime ? dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      fixed: 'right' as const,
      render: (_: any, record: any) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            icon={<CheckOutlined />}
            style={{ color: '#52c41a' }}
            onClick={() => openApproveModal(record, 'approve')}
          >
            通过
          </Button>
          <Button
            type="link"
            size="small"
            danger
            icon={<CloseOutlined />}
            onClick={() => openApproveModal(record, 'reject')}
          >
            拒绝
          </Button>
          {record.source === 'ticket' && (
            <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => navigate('/tickets')}>
              查看
            </Button>
          )}
        </Space>
      ),
    },
  ];

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  return (
    <div>
      {hasPermission('ticket:approve') && (
        <Card
          title={
            <span>
              <AuditOutlined /> 待我审批
              <Badge count={pendingItems.length} style={{ marginLeft: 8 }} />
            </span>
          }
          style={{ marginTop: 16 }}
          extra={
            <Button icon={<ReloadOutlined />} onClick={loadData} loading={pendingLoading} size="small">
              刷新
            </Button>
          }
        >
          <Table
            dataSource={pendingItems}
            columns={pendingColumns}
            rowKey={(r: any) => `${r.source}-${r.id}`}
            loading={pendingLoading}
            pagination={{ pageSize: 10, showTotal: (t: number) => `共 ${t} 条待审批` }}
            size="small"
            scroll={{ x: 900 }}
            locale={{ emptyText: '暂无待审批事项' }}
          />
        </Card>
      )}

      <Modal
        title={approveAction === 'approve' ? '通过申请' : '拒绝申请'}
        open={approveVisible}
        onCancel={() => setApproveVisible(false)}
        onOk={() => commentForm.submit()}
        okText={approveAction === 'approve' ? '确认通过' : '确认拒绝'}
        okButtonProps={{ danger: approveAction === 'reject' }}
        destroyOnClose
      >
        {selectedItem && (
          <div style={{ marginBottom: 16 }}>
            <Descriptions size="small" column={2}>
              <Descriptions.Item label="类型">{selectedItem.typeLabel}</Descriptions.Item>
              <Descriptions.Item label="申请人">{selectedItem.applicantName}</Descriptions.Item>
              <Descriptions.Item label="申请内容" span={2}>{selectedItem.content}</Descriptions.Item>
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
    </div>
  );
};

export default DashboardPage;
