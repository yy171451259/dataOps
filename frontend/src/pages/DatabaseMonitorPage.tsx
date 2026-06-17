import React, { useState, useEffect } from 'react';
import { Card, Select, Row, Col, Statistic, Table, Tag, Button, Space, message, Tabs, Alert, Spin, Descriptions } from 'antd';
import { DatabaseOutlined, ReloadOutlined, StopOutlined } from '@ant-design/icons';
import { instanceApi, dbMonitorApi } from '../utils/api';

const { Option } = Select;
const { TabPane } = Tabs;

const DatabaseMonitorPage: React.FC = () => {
  const [databases, setDatabases] = useState<any[]>([]);
  const [selectedDb, setSelectedDb] = useState<string>('');
  const [dbNames, setDbNames] = useState<string[]>([]);
  const [selectedDbName, setSelectedDbName] = useState<string>('');
  const [loading, setLoading] = useState(false);
  const [activeTab, setActiveTab] = useState('status');

  const [status, setStatus] = useState<any>(null);
  const [slowQueries, setSlowQueries] = useState<any[]>([]);
  const [lockInfo, setLockInfo] = useState<any>(null);
  const [tableStats, setTableStats] = useState<any[]>([]);
  const [diagnosis, setDiagnosis] = useState<any>(null);

  useEffect(() => {
    instanceApi.list().then(res => setDatabases(res.data.data || []));
  }, []);

  useEffect(() => {
    if (selectedDb) {
      instanceApi.getSchemas(selectedDb)
        .then(res => setDbNames(res.data.data || []))
        .catch(() => setDbNames([]));
    }
  }, [selectedDb]);

  useEffect(() => {
    if (selectedDb && activeTab) {
      loadData();
    }
  }, [selectedDb, selectedDbName, activeTab]);

  const loadData = async () => {
    if (!selectedDb) return;
    setLoading(true);
    try {
      switch (activeTab) {
        case 'status':
          const statusRes = await dbMonitorApi.status(selectedDb, selectedDbName);
          setStatus(statusRes.data.data);
          break;
        case 'slow':
          const slowRes = await dbMonitorApi.slowQueries(selectedDb, selectedDbName);
          setSlowQueries(slowRes.data.data || []);
          break;
        case 'locks':
          const lockRes = await dbMonitorApi.locks(selectedDb, selectedDbName);
          setLockInfo(lockRes.data.data);
          break;
        case 'tables':
          const tableRes = await dbMonitorApi.tableStats(selectedDb, selectedDbName);
          setTableStats(tableRes.data.data || []);
          break;
        case 'diagnosis':
          const diagRes = await dbMonitorApi.diagnosis(selectedDb, selectedDbName);
          setDiagnosis(diagRes.data.data);
          break;
      }
    } catch (e: any) {
      message.error(e?.response?.data?.message || '加载失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div>
      <Card>
        <Row gutter={16} align="middle">
          <Col flex="none">
            <Select
              style={{ width: 200 }}
              placeholder="选择数据库实例"
              value={selectedDb}
              onChange={v => {
                setSelectedDb(v);
                setSelectedDbName('');
              }}
            >
              {databases.map(db => (
                <Option key={db.id} value={db.id}>{db.name}</Option>
              ))}
            </Select>
          </Col>
          {selectedDb && dbNames.length > 0 && (
            <Col flex="none">
              <Select
                style={{ width: 180 }}
                placeholder="选择数据库"
                value={selectedDbName}
                onChange={setSelectedDbName}
              >
                <Option value="">全部</Option>
                {dbNames.map(name => (
                  <Option key={name} value={name}>{name}</Option>
                ))}
              </Select>
            </Col>
          )}
          <Col flex="none">
            <Button onClick={loadData} icon={<ReloadOutlined />} loading={loading}>
              刷新
            </Button>
          </Col>
        </Row>
      </Card>

      {!selectedDb ? (
        <Card>
          <Alert message="请先选择数据库实例" type="info" showIcon />
        </Card>
      ) : (
        <Tabs activeKey={activeTab} onChange={setActiveTab} style={{ marginTop: 16 }}>
          <TabPane tab="运行状态" key="status">
            <Spin spinning={loading}>
              {status ? (
                <div>
                  <Row gutter={16}>
                    <Col span={6}>
                      <Card>
                        <Statistic title="当前连接数" value={status.currentConnections || 0} suffix={`/ ${status.maxConnections || 0}`} />
                      </Card>
                    </Col>
                    <Col span={6}>
                      <Card>
                        <Statistic title="运行线程数" value={status.runningThreads || 0} />
                      </Card>
                    </Col>
                    <Col span={6}>
                      <Card>
                        <Statistic title="QPS" value={status.avgQps || 0} />
                      </Card>
                    </Col>
                    <Col span={6}>
                      <Card>
                        <Statistic title="慢查询数" value={status.slowQueries || 0} />
                      </Card>
                    </Col>
                  </Row>
                  <Card title="详细信息" style={{ marginTop: 16 }}>
                    <Descriptions column={3} size="small">
                      <Descriptions.Item label="最大连接数">{status.maxConnections || '-'}</Descriptions.Item>
                      <Descriptions.Item label="运行时间(秒)">{status.uptime || '-'}</Descriptions.Item>
                      <Descriptions.Item label="缓冲命中率">{status.bufferHitRate || '-'}</Descriptions.Item>
                      <Descriptions.Item label="查询总数">{status.questions || '-'}</Descriptions.Item>
                      <Descriptions.Item label="慢查询阈值">{status.longQueryTime || '-'}</Descriptions.Item>
                      <Descriptions.Item label="状态">
                        <Tag color={status.status === 'healthy' ? 'green' : 'red'}>
                          {status.status === 'healthy' ? '健康' : '异常'}
                        </Tag>
                      </Descriptions.Item>
                      <Descriptions.Item label="SELECT次数">{status.comSelect || '-'}</Descriptions.Item>
                      <Descriptions.Item label="INSERT次数">{status.comInsert || '-'}</Descriptions.Item>
                      <Descriptions.Item label="UPDATE次数">{status.comUpdate || '-'}</Descriptions.Item>
                      <Descriptions.Item label="DELETE次数">{status.comDelete || '-'}</Descriptions.Item>
                      <Descriptions.Item label="InnoDB读行数">{status.innodbRowsRead || '-'}</Descriptions.Item>
                      <Descriptions.Item label="InnoDB写行数">{status.innodbRowsInserted + (status.innodbRowsUpdated || 0) + (status.innodbRowsDeleted || 0) || '-'}</Descriptions.Item>
                    </Descriptions>
                  </Card>
                </div>
              ) : (
                <Alert message="暂无数据" type="info" />
              )}
            </Spin>
          </TabPane>

          <TabPane tab="慢查询" key="slow">
            <Spin spinning={loading}>
              {slowQueries.length > 0 ? (
                <Table
                  dataSource={slowQueries}
                  rowKey={(record, index) => index}
                  columns={[
                    { title: 'SQL', dataIndex: 'sql', ellipsis: true, width: 400 },
                    { title: '执行次数', dataIndex: 'execCount', width: 100 },
                    { title: '总耗时(ms)', dataIndex: 'totalTimeMs', width: 120 },
                    { title: '平均耗时(ms)', dataIndex: 'avgTimeMs', width: 120 },
                    { title: '扫描行数', dataIndex: 'rowsExamined', width: 100 },
                    { title: '返回行数', dataIndex: 'rowsSent', width: 100 },
                    { title: '首次出现', dataIndex: 'firstSeen', width: 160 },
                    { title: '最后出现', dataIndex: 'lastSeen', width: 160 },
                  ]}
                />
              ) : (
                <Alert message="暂无慢查询数据" type="info" />
              )}
            </Spin>
          </TabPane>

          <TabPane tab="锁信息" key="locks">
            <Spin spinning={loading}>
              {lockInfo ? (
                <div>
                  <Card title="锁等待信息" style={{ marginBottom: 16 }}>
                    <div style={{ marginBottom: 12 }}>
                      <Tag color={lockInfo.lockWaitCount > 0 ? 'red' : 'green'}>
                        {lockInfo.lockWaitCount > 0 ? `存在 ${lockInfo.lockWaitCount} 个锁等待` : '暂无锁等待'}
                      </Tag>
                    </div>
                    {lockInfo.lockWaits && lockInfo.lockWaits.length > 0 ? (
                      <Table
                        dataSource={lockInfo.lockWaits}
                        rowKey={(record, index) => index}
                        columns={[
                          { title: '等待事务ID', dataIndex: 'waitingTrxId', width: 160 },
                          { title: '等待线程', dataIndex: 'waitingThread', width: 100 },
                          { title: '等待SQL', dataIndex: 'waitingQuery', ellipsis: true },
                          { title: '阻塞事务ID', dataIndex: 'blockingTrxId', width: 160 },
                          { title: '阻塞线程', dataIndex: 'blockingThread', width: 100 },
                          { title: '阻塞SQL', dataIndex: 'blockingQuery', ellipsis: true },
                        ]}
                      />
                    ) : (
                      <Alert message="暂无锁等待" type="success" />
                    )}
                  </Card>
                  <Card title="长事务（运行超过10秒）">
                    <div style={{ marginBottom: 12 }}>
                      <Tag color={lockInfo.longTransactionCount > 0 ? 'orange' : 'green'}>
                        {lockInfo.longTransactionCount > 0 ? `存在 ${lockInfo.longTransactionCount} 个长事务` : '暂无长事务'}
                      </Tag>
                    </div>
                    {lockInfo.longTransactions && lockInfo.longTransactions.length > 0 ? (
                      <Table
                        dataSource={lockInfo.longTransactions}
                        rowKey={(record, index) => index}
                        columns={[
                          { title: '事务ID', dataIndex: 'trxId', width: 160 },
                          { title: '状态', dataIndex: 'state', width: 100 },
                          { title: '持续时间(秒)', dataIndex: 'durationSec', width: 120 },
                          { title: '锁定行数', dataIndex: 'rowsLocked', width: 100 },
                          { title: '修改行数', dataIndex: 'rowsModified', width: 100 },
                          { title: '事务SQL', dataIndex: 'query', ellipsis: true },
                        ]}
                      />
                    ) : (
                      <Alert message="暂无长事务" type="success" />
                    )}
                  </Card>
                </div>
              ) : (
                <Alert message="暂无锁信息" type="info" />
              )}
            </Spin>
          </TabPane>

          <TabPane tab="表统计" key="tables">
            <Spin spinning={loading}>
              {tableStats.length > 0 ? (
                <Table
                  dataSource={tableStats}
                  rowKey="tableName"
                  columns={[
                    { title: '表名', dataIndex: 'tableName' },
                    { title: '行数', dataIndex: 'tableRows' },
                    { title: '数据大小(MB)', dataIndex: 'dataSizeMb' },
                    { title: '索引大小(MB)', dataIndex: 'indexSizeMb' },
                    { title: '总大小(MB)', dataIndex: 'totalSizeMb' },
                    { title: '存储引擎', dataIndex: 'engine', width: 100 },
                    { title: '表备注', dataIndex: 'comment', ellipsis: true },
                  ]}
                />
              ) : (
                <Alert message="暂无表统计数据" type="info" />
              )}
            </Spin>
          </TabPane>

          <TabPane tab="诊断报告" key="diagnosis">
            <Spin spinning={loading}>
              {diagnosis ? (
                <div>
                  <Card title="健康评分" style={{ marginBottom: 16 }}>
                    <Row gutter={16} align="middle">
                      <Col span={8}>
                        <Statistic 
                          value={diagnosis.healthScore || 0} 
                          suffix="/100"
                          valueStyle={{ color: diagnosis.healthScore >= 80 ? '#52c41a' : diagnosis.healthScore >= 60 ? '#faad14' : '#ff4d4f' }}
                        />
                      </Col>
                      <Col span={16}>
                        <Tag color={diagnosis.healthLevel === 'healthy' ? 'green' : diagnosis.healthLevel === 'warning' ? 'orange' : 'red'}>
                          {diagnosis.healthLevel === 'healthy' ? '健康' : diagnosis.healthLevel === 'warning' ? '警告' : '严重'}
                        </Tag>
                        <div style={{ marginTop: 8, color: '#666' }}>{diagnosis.summary || '-'}</div>
                      </Col>
                    </Row>
                  </Card>
                  <Card title="发现的问题" style={{ marginBottom: 16 }}>
                    {diagnosis.issues && diagnosis.issues.length > 0 ? (
                      <div>
                        {diagnosis.issues.map((issue: any, index: number) => (
                          <div key={index} style={{ marginBottom: 12, padding: 8, backgroundColor: '#fafafa' }}>
                            <Tag color={issue.level === 'critical' ? 'red' : issue.level === 'warning' ? 'orange' : 'blue'}>
                              {issue.level === 'critical' ? '严重' : issue.level === 'warning' ? '警告' : '提示'}
                            </Tag>
                            <div style={{ marginTop: 4, fontWeight: 500 }}>{issue.title}</div>
                            <div style={{ marginTop: 2, color: '#666', fontSize: 12 }}>{issue.detail}</div>
                          </div>
                        ))}
                      </div>
                    ) : (
                      <Alert message="未发现问题" type="success" />
                    )}
                  </Card>
                  <Card title="关键配置">
                    {diagnosis.keyVariables ? (
                      <Descriptions column={3} size="small">
                        {Object.entries(diagnosis.keyVariables).map(([key, value]) => (
                          <Descriptions.Item key={key} label={key}>{value}</Descriptions.Item>
                        ))}
                      </Descriptions>
                    ) : (
                      <Alert message="暂无配置信息" type="info" />
                    )}
                  </Card>
                </div>
              ) : (
                <Alert message="暂无诊断数据" type="info" />
              )}
            </Spin>
          </TabPane>
        </Tabs>
      )}
    </div>
  );
};

export default DatabaseMonitorPage;