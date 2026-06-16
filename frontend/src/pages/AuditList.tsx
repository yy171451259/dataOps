import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Input,
  Select,
  DatePicker,
  Tag,
  Space,
  Button,
  Descriptions,
  Modal
} from 'antd';
import { SearchOutlined, EyeOutlined } from '@ant-design/icons';
const { RangePicker } = DatePicker;
const { Option } = Select;

interface AuditLog {
  id: string;
  userId: string;
  action: string;
  resourceType: string;
  resourceId: string;
  detail: string;
  clientIp: string;
  riskLevel: string;
  createTime: string;
}

const AuditList: React.FC = () => {
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loading] = useState(false);
  const [detailVisible, setDetailVisible] = useState(false);
  const [selectedLog, setSelectedLog] = useState<AuditLog | null>(null);

  useEffect(() => {
    // 模拟数据
    const mockLogs: AuditLog[] = [
      {
        id: '1',
        userId: 'admin',
        action: 'SQL查询',
        resourceType: 'database',
        resourceId: 'mysql_test',
        detail: '{"sql":"SELECT * FROM users","rows":100}',
        clientIp: '192.168.1.100',
        riskLevel: 'low',
        createTime: '2024-01-15 10:30:00'
      },
      {
        id: '2',
        userId: 'developer1',
        action: 'SQL变更',
        resourceType: 'database',
        resourceId: 'mysql_test',
        detail: '{"sql":"UPDATE users SET status = 1","rows":50}',
        clientIp: '192.168.1.101',
        riskLevel: 'medium',
        createTime: '2024-01-15 11:20:00'
      },
      {
        id: '3',
        userId: 'admin',
        action: '创建工单',
        resourceType: 'ticket',
        resourceId: 'T001',
        detail: '{"title":"用户表结构变更","type":"ddl"}',
        clientIp: '192.168.1.100',
        riskLevel: 'low',
        createTime: '2024-01-15 14:00:00'
      },
      {
        id: '4',
        userId: 'dba1',
        action: '审批工单',
        resourceType: 'ticket',
        resourceId: 'T001',
        detail: '{"result":"approved","comment":"同意执行"}',
        clientIp: '192.168.1.102',
        riskLevel: 'low',
        createTime: '2024-01-15 14:30:00'
      },
      {
        id: '5',
        userId: 'hacker',
        action: '登录失败',
        resourceType: 'system',
        resourceId: '',
        detail: '{"reason":"密码错误"}',
        clientIp: '10.0.0.1',
        riskLevel: 'high',
        createTime: '2024-01-15 15:00:00'
      }
    ];
    setLogs(mockLogs);
  }, []);

  const riskColors: Record<string, string> = {
    low: 'green',
    medium: 'orange',
    high: 'red'
  };

  const riskText: Record<string, string> = {
    low: '低',
    medium: '中',
    high: '高'
  };

  const columns = [
    {
      title: '时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180,
      sorter: (a: AuditLog, b: AuditLog) => 
        new Date(a.createTime).getTime() - new Date(b.createTime).getTime()
    },
    {
      title: '用户',
      dataIndex: 'userId',
      key: 'userId',
      width: 120
    },
    {
      title: '操作',
      dataIndex: 'action',
      key: 'action',
      width: 120
    },
    {
      title: '资源类型',
      dataIndex: 'resourceType',
      key: 'resourceType',
      width: 100
    },
    {
      title: 'IP地址',
      dataIndex: 'clientIp',
      key: 'clientIp',
      width: 140
    },
    {
      title: '风险等级',
      dataIndex: 'riskLevel',
      key: 'riskLevel',
      width: 100,
      render: (level: string) => (
        <Tag color={riskColors[level] || 'default'}>
          {riskText[level] || level}
        </Tag>
      )
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: any, record: AuditLog) => (
        <Button
          type="link"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => {
            setSelectedLog(record);
            setDetailVisible(true);
          }}
        >
          详情
        </Button>
      )
    }
  ];

  return (
    <div>
      <Card title="审计日志">
        <div style={{ marginBottom: 16 }}>
          <Space>
            <Input
              placeholder="搜索用户/操作"
              prefix={<SearchOutlined />}
              style={{ width: 200 }}
            />
            <Select placeholder="风险等级" style={{ width: 120 }} allowClear>
              <Option value="low">低风险</Option>
              <Option value="medium">中风险</Option>
              <Option value="high">高风险</Option>
            </Select>
            <RangePicker
              showTime
              placeholder={['开始时间', '结束时间']}
              style={{ width: 350 }}
            />
            <Button type="primary">查询</Button>
          </Space>
        </div>

        <Table
          columns={columns}
          dataSource={logs}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 20, showSizeChanger: true }}
        />
      </Card>

      <Modal
        title="审计详情"
        open={detailVisible}
        onCancel={() => setDetailVisible(false)}
        footer={null}
        width={600}
      >
        {selectedLog && (
          <Descriptions column={1} bordered>
            <Descriptions.Item label="操作时间">
              {selectedLog.createTime}
            </Descriptions.Item>
            <Descriptions.Item label="操作用户">
              {selectedLog.userId}
            </Descriptions.Item>
            <Descriptions.Item label="操作类型">
              {selectedLog.action}
            </Descriptions.Item>
            <Descriptions.Item label="资源类型">
              {selectedLog.resourceType}
            </Descriptions.Item>
            <Descriptions.Item label="资源ID">
              {selectedLog.resourceId || '-'}
            </Descriptions.Item>
            <Descriptions.Item label="客户端IP">
              {selectedLog.clientIp}
            </Descriptions.Item>
            <Descriptions.Item label="风险等级">
              <Tag color={riskColors[selectedLog.riskLevel]}>
                {riskText[selectedLog.riskLevel]}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="操作详情">
              <pre style={{ 
                background: '#f5f5f5', 
                padding: 8, 
                borderRadius: 4,
                margin: 0,
                maxHeight: 200,
                overflow: 'auto'
              }}>
                {JSON.stringify(JSON.parse(selectedLog.detail), null, 2)}
              </pre>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  );
};

export default AuditList;
