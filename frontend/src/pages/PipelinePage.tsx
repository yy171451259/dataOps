import React, { useState, useEffect } from 'react';
import {
  Card, Button, Table, Modal, Form, Input, Select, Space, Steps, Tag,
  Popconfirm, message, Row, Col, Divider, Timeline, Descriptions, Alert
} from 'antd';
import {
  PlusOutlined, PlayCircleOutlined, CheckCircleOutlined,
  CloseCircleOutlined, RollbackOutlined, RightCircleOutlined
} from '@ant-design/icons';
import { pipelineApi, instanceApi } from '../utils/api';

const { Option } = Select;
const { TextArea } = Input;
const { Step } = Steps;

// 状态颜色映射
const statusColors: Record<string, string> = {
  PENDING: 'default',
  APPROVING: 'warning',
  EXECUTING: 'processing',
  SUCCESS: 'success',
  FAILED: 'error',
  SKIPPED: 'default',
  ROLLBACKED: 'warning',
  CANCELLED: 'default'
};

const statusLabels: Record<string, string> = {
  PENDING: '待执行',
  APPROVING: '审批中',
  EXECUTING: '执行中',
  SUCCESS: '成功',
  FAILED: '失败',
  SKIPPED: '跳过',
  ROLLBACKED: '已回滚',
  CANCELLED: '已取消',
  IN_PROGRESS: '进行中'
};

const PipelinePage: React.FC = () => {
  const [tab, setTab] = useState<'pipelines' | 'executions'>('pipelines');
  const [pipelines, setPipelines] = useState<any[]>([]);
  const [executions, setExecutions] = useState<any[]>([]);
  const [databases, setDatabases] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  
  const [createModalVisible, setCreateModalVisible] = useState(false);
  const [executionModalVisible, setExecutionModalVisible] = useState(false);
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedPipeline, setSelectedPipeline] = useState<any>(null);
  const [executionDetail, setExecutionDetail] = useState<any>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    loadPipelines();
    loadDatabases();
  }, []);

  const loadPipelines = async () => {
    setLoading(true);
    try {
      const res = await pipelineApi.list(1, 50);
      setPipelines(res.data?.data?.records || []);
    } catch (e) {
      message.error('OK');
    } finally {
      setLoading(false);
    }
  };

  const loadExecutions = async () => {
    setLoading(true);
    try {
      const res = await pipelineApi.listExecutions('', 1, 50);
      setExecutions(res.data?.data?.records || []);
    } catch (e) {
      message.error('OK');
    } finally {
      setLoading(false);
    }
  };

  const loadDatabases = async () => {
    try {
      const res = await instanceApi.list();
      setDatabases(res.data?.data?.records || []);
    } catch (e) {}
  };

  const handleCreatePipeline = async (values: any) => {
    try {
      const stages = [
        { stageName: 'DEV', stageOrder: 1, databaseInstanceId: values.devDbId, requireApproval: false, autoExecute: true },
        { stageName: 'TEST', stageOrder: 2, databaseInstanceId: values.testDbId, requireApproval: false, autoExecute: true },
        { stageName: 'STAGING', stageOrder: 3, databaseInstanceId: values.stagingDbId, requireApproval: false, autoExecute: false },
        { stageName: 'PROD', stageOrder: 4, databaseInstanceId: values.prodDbId, requireApproval: true, autoExecute: false },
      ];
      await pipelineApi.create({ name: values.name, description: values.description, stages });
      message.success('OK');
      setCreateModalVisible(false);
      form.resetFields();
      loadPipelines();
    } catch (e: any) {
      message.error(e.response?.data?.message || '创建失败');
    }
  };

  const handleStartExecution = async (values: any) => {
    try {
      await pipelineApi.startExecution({
        pipelineId: selectedPipeline.id,
        title: values.title,
        description: values.description,
        sqlContent: values.sqlContent
      });
      message.success('OK');
      setExecutionModalVisible(false);
      form.resetFields();
      loadExecutions();
    } catch (e: any) {
      message.error(e.response?.data?.message || '启动失败');
    }
  };

  const loadExecutionDetail = async (id: string) => {
    try {
      const res = await pipelineApi.getExecution(id);
      setExecutionDetail(res.data?.data);
      setDetailModalVisible(true);
    } catch (e) {
      message.error('OK');
    }
  };

  const handleStageAction = async (action: string, stageId: string) => {
    try {
      if (action === 'execute') {
        await pipelineApi.executeStage(stageId);
      } else if (action === 'approve') {
        await pipelineApi.approveStage(stageId, '同意');
      } else if (action === 'reject') {
        await pipelineApi.rejectStage(stageId, '拒绝');
      } else if (action === 'rollback') {
        await pipelineApi.rollbackStage(stageId);
      }
      message.success('OK');
      loadExecutionDetail(executionDetail.id);
    } catch (e: any) {
      message.error(e.response?.data?.message || '操作失败');
    }
  };

  const handleDeletePipeline = async (id: string) => {
    try {
      await pipelineApi.delete(id);
      message.success('OK');
      loadPipelines();
    } catch (e) {
      message.error('OK');
    }
  };

  const pipelineColumns = [
    { title: '流水线名称', dataIndex: 'name', key: 'name' },
    { title: '描述', dataIndex: 'description', key: 'description' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (v: string) => 
      <Tag color={v === 'ACTIVE' ? 'success' : 'default'}>{v === 'ACTIVE' ? '启用' : '禁用'}</Tag> 
    },
    { title: '创建人', dataIndex: 'createdBy', key: 'createdBy' },
    { 
      title: '操作', 
      key: 'actions', 
      render: (_: any, record: any) => (
        <Space>
          <Button size="small" type="primary" icon={<PlayCircleOutlined />} onClick={() => {
            setSelectedPipeline(record);
            setExecutionModalVisible(true);
          }}>发起变更</Button>
          <Popconfirm title="确认删除" onConfirm={() => handleDeletePipeline(record.id)}>
            <Button size="small" danger>删除</Button>
          </Popconfirm>
        </Space>
      ) 
    },
  ];

  const executionColumns = [
    { title: '变更标题', dataIndex: 'title', key: 'title' },
    { title: '状态', dataIndex: 'status', key: 'status', render: (v: string) => 
      <Tag color={statusColors[v]}>{statusLabels[v]}</Tag> 
    },
    { title: '创建人', dataIndex: 'createdBy', key: 'createdBy' },
    { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt' },
    { 
      title: '操作', 
      key: 'actions', 
      render: (_: any, record: any) => (
        <Button size="small" onClick={() => loadExecutionDetail(record.id)}>查看详情</Button>
      ) 
    },
  ];

  return (
    <div style={{ padding: 24 }}>
      <Card 
        tabList={[
          { key: 'pipelines', tab: '流水线管理' },
          { key: 'executions', tab: '执行记录' }
        ]}
        activeTabKey={tab}
        onTabChange={(k) => { setTab(k as any); if (k === 'executions') loadExecutions(); }}
        extra={tab === 'pipelines' ? (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateModalVisible(true)}>
            创建流水线
          </Button>
        ) : null}
      >
        {tab === 'pipelines' && (
          <Table 
            columns={pipelineColumns} 
            dataSource={pipelines} 
            rowKey="id" 
            loading={loading}
            pagination={{ pageSize: 10 }}
          />
        )}
        {tab === 'executions' && (
          <Table 
            columns={executionColumns} 
            dataSource={executions} 
            rowKey="id" 
            loading={loading}
            pagination={{ pageSize: 10 }}
          />
        )}
      </Card>

      {/* 创建流水线弹窗 */}
      <Modal 
        title="创建DDL部署流水线" 
        open={createModalVisible} 
        onCancel={() => setCreateModalVisible(false)} 
        onOk={() => form.submit()}
        width={600}
      >
        <Form form={form} layout="vertical" onFinish={handleCreatePipeline}>
          <Form.Item name="name" label="流水线名称" rules={[{ required: true }]}>
            <Input placeholder="例如：用户服务表结构变更流水线" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={2} placeholder="描述流水线用途" />
          </Form.Item>
          <Divider>配置环境数据库（DEV → TEST → STAGING → PROD）</Divider>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="devDbId" label="开发环境数据库" rules={[{ required: true }]}>
                <Select placeholder="选择数据库">
                  {databases.map(db => <Option key={db.id} value={db.id}>{db.name}</Option>)}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="testDbId" label="测试环境数据库" rules={[{ required: true }]}>
                <Select placeholder="选择数据库">
                  {databases.map(db => <Option key={db.id} value={db.id}>{db.name}</Option>)}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="stagingDbId" label="预发环境数据库" rules={[{ required: true }]}>
                <Select placeholder="选择数据库">
                  {databases.map(db => <Option key={db.id} value={db.id}>{db.name}</Option>)}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="prodDbId" label="生产环境数据库" rules={[{ required: true }]}>
                <Select placeholder="选择数据库">
                  {databases.map(db => <Option key={db.id} value={db.id}>{db.name}</Option>)}
                </Select>
              </Form.Item>
            </Col>
          </Row>
          <p style={{ color: '#999', fontSize: 12, marginBottom: 0 }}>
            💡 DEV/TEST 自动执行，STAGING 手动执行，PROD 需要审批后手动执行
          </p>
        </Form>
      </Modal>

      {/* 发起变更弹窗 */}
      <Modal 
        title="发起DDL变更" 
        open={executionModalVisible} 
        onCancel={() => setExecutionModalVisible(false)} 
        onOk={() => form.submit()}
        width={700}
      >
        <Form form={form} layout="vertical" onFinish={handleStartExecution}>
          <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
            <Descriptions.Item label="流水线">{selectedPipeline?.name}</Descriptions.Item>
          </Descriptions>
          <Form.Item name="title" label="变更标题" rules={[{ required: true }]}>
            <Input placeholder="简要描述本次DDL变更" />
          </Form.Item>
          <Form.Item name="description" label="变更说明">
            <TextArea rows={2} placeholder="变更原因、影响范围等" />
          </Form.Item>
          <Form.Item name="sqlContent" label="DDL SQL" rules={[{ required: true }]}>
            <TextArea 
              rows={8} 
              placeholder={"输入要执行的DDL语句，例如：\nALTER TABLE user ADD COLUMN phone VARCHAR(20) COMMENT '手机号';"} 
              style={{ fontFamily: 'monospace' }}
            />
          </Form.Item>
          <Alert 
            message="SQL 将按顺序在 DEV → TEST → STAGING → PROD 各环境自动流转执行，生产环境需要审批后执行" 
            type="info" 
            showIcon 
          />
        </Form>
      </Modal>

      {/* 执行详情弹窗 */}
      <Modal 
        title="变更执行详情" 
        open={detailModalVisible} 
        onCancel={() => setDetailModalVisible(false)} 
        footer={null}
        width={900}
      >
        {executionDetail && (
          <div>
            <Descriptions column={2} bordered size="small" style={{ marginBottom: 16 }}>
              <Descriptions.Item label="变更标题">{executionDetail.title}</Descriptions.Item>
              <Descriptions.Item label="状态">
                <Tag color={statusColors[executionDetail.status]}>{statusLabels[executionDetail.status]}</Tag>
              </Descriptions.Item>
              <Descriptions.Item label="流水线">{executionDetail.pipelineName}</Descriptions.Item>
              <Descriptions.Item label="创建人">{executionDetail.createdBy}</Descriptions.Item>
              <Descriptions.Item label="创建时间" span={2}>{executionDetail.createdAt}</Descriptions.Item>
              {executionDetail.description && (
                <Descriptions.Item label="变更说明" span={2}>{executionDetail.description}</Descriptions.Item>
              )}
            </Descriptions>

            <Card title="SQL内容" size="small" style={{ marginBottom: 16 }}>
              <pre style={{ background: '#f5f5f5', padding: 12, borderRadius: 4, margin: 0, fontSize: 12, maxHeight: 200, overflow: 'auto' }}>
                {executionDetail.sqlContent}
              </pre>
            </Card>

            <Card title="执行进度" size="small">
              <Steps direction="vertical" current={executionDetail.stageExecutions?.findIndex((s: any) => 
                ['PENDING', 'APPROVING', 'EXECUTING'].includes(s.status)
              ) || 0}>
                {executionDetail.stageExecutions?.map((stage: any) => (
                  <Step 
                    key={stage.id}
                    status={
                      stage.status === 'SUCCESS' ? 'finish' :
                      ['FAILED', 'ROLLBACKED'].includes(stage.status) ? 'error' :
                      stage.status === 'SKIPPED' ? 'finish' : 'process'
                    }
                    title={
                      <Space>
                        <span style={{ fontWeight: 500 }}>{stage.stageName}</span>
                        <Tag color={statusColors[stage.status]}>{statusLabels[stage.status]}</Tag>
                        <span style={{ color: '#999', fontSize: 12 }}>{stage.databaseName} ({stage.databaseHost})</span>
                      </Space>
                    }
                    description={
                      <div style={{ padding: '8px 0' }}>
                        {['SUCCESS', 'FAILED', 'ROLLBACKED'].includes(stage.status) && (
                          <Timeline mode="left" style={{ fontSize: 12 }}>
                            <Timeline.Item color="green">
                              执行人: {stage.executedBy} | 执行时间: {stage.executedAt} | 耗时: {stage.durationSeconds}秒
                            </Timeline.Item>
                            {stage.resultMessage && (
                              <Timeline.Item color={stage.status === 'SUCCESS' ? 'green' : 'red'}>
                                结果: {stage.resultMessage}
                              </Timeline.Item>
                            )}
                          </Timeline>
                        )}
                        {stage.status === 'APPROVING' && (
                          <Space>
                            <Button type="primary" size="small" icon={<CheckCircleOutlined />} 
                              onClick={() => handleStageAction('approve', stage.id)}>审批通过</Button>
                            <Button size="small" danger icon={<CloseCircleOutlined />}
                              onClick={() => handleStageAction('reject', stage.id)}>拒绝</Button>
                          </Space>
                        )}
                        {stage.status === 'PENDING' && (
                          <Button type="primary" size="small" icon={<RightCircleOutlined />}
                            onClick={() => handleStageAction('execute', stage.id)}>执行</Button>
                        )}
                        {stage.status === 'SUCCESS' && stage.rollbackSql && (
                          <Popconfirm title="确认回滚？" onConfirm={() => handleStageAction('rollback', stage.id)}>
                            <Button size="small" danger icon={<RollbackOutlined />} style={{ marginTop: 8 }}>回滚</Button>
                          </Popconfirm>
                        )}
                      </div>
                    }
                  />
                ))}
              </Steps>
            </Card>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default PipelinePage;
