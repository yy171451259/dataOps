import React, { useState, useEffect } from 'react';
import {
  Card,
  Form,
  Input,
  Button,
  Switch,
  Select,
  message,
  Row,
  Col,
  Alert,
  Tabs,
  InputNumber,
  Tag,
} from 'antd';
import {
  SettingOutlined,
  BellOutlined,
  SafetyOutlined,
  InfoCircleOutlined,
  SaveOutlined,
  ReloadOutlined,
} from '@ant-design/icons';

const { Option } = Select;

const SystemSettingsPage: React.FC = () => {
  const [generalForm] = Form.useForm();
  const [notifyForm] = Form.useForm();
  const [securityForm] = Form.useForm();
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    // 加载已保存的配置
    const saved = localStorage.getItem('dms_system_settings');
    if (saved) {
      try {
        const settings = JSON.parse(saved);
        generalForm.setFieldsValue(settings.general || {});
        notifyForm.setFieldsValue(settings.notify || {});
        securityForm.setFieldsValue(settings.security || {});
      } catch (e) {
        // ignore
      }
    } else {
      // 设置默认值
      generalForm.setFieldsValue({
        systemName: 'DataOps DMS',
        language: 'zh-CN',
        theme: 'light',
        pageSize: 20,
        autoRefresh: true,
        refreshInterval: 30,
      });
      securityForm.setFieldsValue({
        sessionTimeout: 24,
        requireApproval: true,
        enableAudit: true,
        enableBackup: true,
      });
    }
  }, []);

  const handleSave = (section: string) => {
    setLoading(true);
    try {
      const saved = localStorage.getItem('dms_system_settings');
      const settings = saved ? JSON.parse(saved) : {};

      if (section === 'general') {
        settings.general = generalForm.getFieldsValue();
      } else if (section === 'notify') {
        settings.notify = notifyForm.getFieldsValue();
      } else if (section === 'security') {
        settings.security = securityForm.getFieldsValue();
      }

      localStorage.setItem('dms_system_settings', JSON.stringify(settings));
      message.success('OK');
    } catch (e) {
      message.error('OK');
    } finally {
      setLoading(false);
    }
  };

  const handleReset = () => {
    localStorage.removeItem('dms_system_settings');
    generalForm.resetFields();
    notifyForm.resetFields();
    securityForm.resetFields();
    message.info('OK');
  };

  const tabItems = [
    {
      key: 'general',
      label: (
        <span><SettingOutlined /> 常规设置</span>
      ),
      children: (
        <Form form={generalForm} layout="vertical" style={{ maxWidth: 600 }}>
          <Form.Item name="systemName" label="系统名称">
            <Input placeholder="请输入系统名称" />
          </Form.Item>

          <Form.Item name="language" label="界面语言">
            <Select>
              <Option value="zh-CN">简体中文</Option>
              <Option value="en-US">English</Option>
            </Select>
          </Form.Item>

          <Form.Item name="theme" label="主题模式">
            <Select>
              <Option value="light">浅色模式</Option>
              <Option value="dark">深色模式</Option>
            </Select>
          </Form.Item>

          <Form.Item name="pageSize" label="默认分页大小">
            <InputNumber min={10} max={100} step={10} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="autoRefresh" label="自动刷新监控数据" valuePropName="checked">
            <Switch checkedChildren="开启" unCheckedChildren="关闭" />
          </Form.Item>

          <Form.Item name="refreshInterval" label="自动刷新间隔（秒）">
            <InputNumber min={5} max={300} step={5} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item>
            <Button type="primary" icon={<SaveOutlined />} loading={loading} onClick={() => handleSave('general')}>
              保存设置
            </Button>
          </Form.Item>
        </Form>
      ),
    },
    {
      key: 'security',
      label: (
        <span><SafetyOutlined /> 安全设置</span>
      ),
      children: (
        <Form form={securityForm} layout="vertical" style={{ maxWidth: 600 }}>
          <Form.Item name="sessionTimeout" label="会话超时时间（小时）">
            <InputNumber min={1} max={168} style={{ width: '100%' }} />
          </Form.Item>

          <Form.Item name="requireApproval" label="数据变更强制审批" valuePropName="checked">
            <Switch checkedChildren="开启" unCheckedChildren="关闭" />
          </Form.Item>

          <Form.Item name="enableAudit" label="操作审计日志" valuePropName="checked">
            <Switch checkedChildren="开启" unCheckedChildren="关闭" />
          </Form.Item>

          <Form.Item name="enableBackup" label="变更前自动备份" valuePropName="checked">
            <Switch checkedChildren="开启" unCheckedChildren="关闭" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" icon={<SaveOutlined />} loading={loading} onClick={() => handleSave('security')}>
              保存设置
            </Button>
          </Form.Item>
        </Form>
      ),
    },
    {
      key: 'notify',
      label: (
        <span><BellOutlined /> 通知设置</span>
      ),
      children: (
        <Form form={notifyForm} layout="vertical" style={{ maxWidth: 600 }}>
          <Form.Item name="enableEmail" label="邮件通知" valuePropName="checked">
            <Switch checkedChildren="开启" unCheckedChildren="关闭" />
          </Form.Item>

          <Form.Item name="smtpHost" label="SMTP 服务器地址">
            <Input placeholder="例如：smtp.example.com" />
          </Form.Item>

          <Form.Item name="smtpPort" label="SMTP 端口">
            <InputNumber min={1} max={65535} style={{ width: '100%' }} placeholder="例如：87" />
          </Form.Item>

          <Form.Item name="smtpUser" label="发件人邮箱">
            <Input placeholder="例如：noreply@example.com" />
          </Form.Item>

          <Form.Item name="enableWebhook" label="Webhook 通知" valuePropName="checked">
            <Switch checkedChildren="开启" unCheckedChildren="关闭" />
          </Form.Item>

          <Form.Item name="webhookUrl" label="Webhook URL">
            <Input placeholder="例如：https://hooks.example.com/dms" />
          </Form.Item>

          <Form.Item>
            <Button type="primary" icon={<SaveOutlined />} loading={loading} onClick={() => handleSave('notify')}>
              保存设置
            </Button>
          </Form.Item>
        </Form>
      ),
    },
    {
      key: 'about',
      label: (
        <span><InfoCircleOutlined /> 关于系统</span>
      ),
      children: (
        <div style={{ maxWidth: 600 }}>
          <Alert
            message="DataOps DMS"
            description="一站式数据管理平台，提供SQL 查询、数据变更工单、元数据管理、数据脱敏、数据质量等核心功能。"
            type="info"
            showIcon
            style={{ marginBottom: 24 }}
          />
          <Row gutter={[16, 12]}>
            <Col span={12}><Tag color="blue">版本</Tag> v1.0.0</Col>
            <Col span={12}><Tag color="green">后端</Tag> Spring Boot 2.7</Col>
            <Col span={12}><Tag color="purple">前端</Tag> React 18 + Ant Design</Col>
            <Col span={12}><Tag color="orange">数据库</Tag> MySQL 8.0+</Col>
            <Col span={12}><Tag color="cyan">工作流</Tag> Flowable 6.6.0</Col>
            <Col span={12}><Tag color="magenta">ORM</Tag> MyBatis-Plus 3.5</Col>
          </Row>
        </div>
      ),
    },
  ];

  return (
    <div>
      <Card
        title="系统设置"
        extra={
          <Button icon={<ReloadOutlined />} onClick={handleReset}>
            恢复默认
          </Button>
        }
      >
        <Tabs items={tabItems} defaultActiveKey="general" tabPosition="left" />
      </Card>
    </div>
  );
};

export default SystemSettingsPage;
