import React, { useEffect, useState } from 'react';
import { Card, Row, Col, Statistic, Table, Tag, Spin } from 'antd';
import {
  DatabaseOutlined, CheckSquareOutlined, AuditOutlined,
  FileTextOutlined, ThunderboltOutlined
} from '@ant-design/icons';
import { monitorApi, instanceApi, ticketApi } from '../utils/api';

const DashboardPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<any>({});
  const [recentTickets, setRecentTickets] = useState<any[]>([]);

  useEffect(() => {
    loadData();
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const [sysRes, dbRes, ticketRes] = await Promise.all([
        monitorApi.system(),
        instanceApi.list(),
        ticketApi.my(),
      ]);
      setStats({
        system: sysRes.data.data,
        databases: dbRes.data.data?.length || 0,
        tickets: ticketRes.data.data?.records?.length || 0,
      });
      setRecentTickets(ticketRes.data.data?.records?.slice(0, 5) || []);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  const statusMap: Record<string, { color: string; text: string }> = {
    pending: { color: 'orange', text: '待审批' },
    approved: { color: 'blue', text: '已通过' },
    rejected: { color: 'red', text: '已拒绝' },
    done: { color: 'green', text: '已完成' },
  };

  const columns = [
    { title: '标题', dataIndex: 'title', key: 'title' },
    { title: '类型', dataIndex: 'type', key: 'type' },
    {
      title: '状态', dataIndex: 'status', key: 'status',
      render: (s: string) => <Tag color={statusMap[s]?.color}>{statusMap[s]?.text || s}</Tag>
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime' },
  ];

  if (loading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  return (
    <div>
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} md={6}>
          <Card><Statistic title="数据库实例" value={stats.databases} prefix={<DatabaseOutlined />} /></Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card><Statistic title="我的工单" value={stats.tickets} prefix={<CheckSquareOutlined />} /></Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card><Statistic title="堆内存使用" value={stats.system?.heapMemoryUsed || 'N/A'} prefix={<ThunderboltOutlined />} /></Card>
        </Col>
        <Col xs={24} sm={12} md={6}>
          <Card><Statistic title="系统运行时间" value={stats.system?.jvmUptime || 'N/A'} prefix={<FileTextOutlined />} /></Card>
        </Col>
      </Row>
      <Card title={<span><AuditOutlined /> 最近工单</span>} style={{ marginTop: 16 }}>
        <Table dataSource={recentTickets} columns={columns} rowKey="id" pagination={false} size="small" />
      </Card>
    </div>
  );
};

export default DashboardPage;
