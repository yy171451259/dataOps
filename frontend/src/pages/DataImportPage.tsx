import React, { useState, useEffect } from 'react';
import { Card, Upload, Button, Select, Input, Table, message, Space, Row, Col, Steps, Alert, Tag, Form, Switch, Descriptions, Result, Statistic } from 'antd';
import { ImportOutlined, InboxOutlined } from '@ant-design/icons';
import { instanceApi, importApi } from '../utils/api';

const { Option } = Select;
const { Dragger } = Upload;
const { Step } = Steps;

const DataImportPage: React.FC = () => {
  const [currentStep, setCurrentStep] = useState(0);
  const [databases, setDatabases] = useState<any[]>([]);
  const [selectedDb, setSelectedDb] = useState<string>('');
  const [dbNames, setDbNames] = useState<string[]>([]);
  const [selectedDbName, setSelectedDbName] = useState<string>('');
  const [tableNames, setTableNames] = useState<string[]>([]);
  const [selectedTable, setSelectedTable] = useState<string>('');
  const [file, setFile] = useState<File | null>(null);
  const [format, setFormat] = useState<string>('csv');
  const [preview, setPreview] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);
  const [result, setResult] = useState<any>(null);
  const [createTable, setCreateTable] = useState(false);
  const [truncateFirst, setTruncateFirst] = useState(false);
  const [batchSize, setBatchSize] = useState(500);
  const [newTableName, setNewTableName] = useState('');

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
    if (selectedDb && selectedDbName) {
      instanceApi.getTableNames(selectedDb, selectedDbName)
        .then(res => setTableNames(res.data.data || []))
        .catch(() => setTableNames([]));
    }
  }, [selectedDb, selectedDbName]);

  const handleFileUpload = async (info: any) => {
    const uploadedFile = info.file?.originFileObj || info.file;
    if (!uploadedFile) return;
    setFile(uploadedFile);
    
    // Auto detect format
    const ext = uploadedFile.name.split('.').pop()?.toLowerCase() || 'csv';
    const detectedFormat = ['csv', 'json', 'tsv', 'sql'].includes(ext) ? ext : 'csv';
    setFormat(detectedFormat);

    setLoading(true);
    try {
      const res = await importApi.preview(uploadedFile, detectedFormat);
      setPreview(res.data.data);
      if (res.data.data?.columns) {
        message.success("OK");
      }
    } catch (e: any) {
      message.error('OK');
      return;
    }

    const tableName = createTable ? (newTableName || `import_${Date.now()}`) : selectedTable;
    if (!tableName) {
      message.warning('OK');
      return;
    }

    setImporting(true);
    try {
      const res = await importApi.execute({
        file,
        instanceId: selectedDb,
        schemaName: selectedDbName,
        tableName,
        format,
        batchSize,
        createTable,
        truncateFirst,
      });
      setResult(res.data.data);
      setCurrentStep(3);
      if (res.data.data?.success) {
        message.success('OK');
      } else {
        message.warning(res.data.data?.message || '导入完成，但有部分错误');
      }
    } catch (e: any) {
      message.error('OK');
    setNewTableName('');
    setCreateTable(false);
    setTruncateFirst(false);
  };

  return (
    <div>
      <Card size="small" style={{ marginBottom: 16 }}>
        <Steps current={currentStep} size="small">
          <Step title="选择数据源" />
          <Step title="上传文件" />
          <Step title="配置导入" />
          <Step title="导入结果" />
        </Steps>
      </Card>

      {/* Step 0: Select Database */}
      {currentStep === 0 && (
        <Card title="选择目标数据库">
          <Row gutter={16}>
            <Col span={8}>
              <Form layout="vertical">
                <Form.Item label="数据库实例">
                  <Select style={{ width: '100%' }} value={selectedDb || undefined} placeholder="请选择"
                    onChange={(v) => { setSelectedDb(v); setSelectedDbName(''); setSelectedTable(''); }}>
                    {databases.map((db: any) => <Option key={db.id} value={db.id}>{db.name} ({db.host}:{db.port})</Option>)}
                  </Select>
                </Form.Item>
                {selectedDb && (
                  <Form.Item label="Schema">
                    <Select style={{ width: '100%' }} value={selectedDbName || undefined} placeholder="请选择"
                      onChange={(v) => { setSelectedDbName(v); setSelectedTable(''); }}>
                      {dbNames.map((n: string) => <Option key={n} value={n}>{n}</Option>)}
                    </Select>
                  </Form.Item>
                )}
              </Form>
            </Col>
          </Row>
          <Button type="primary" disabled={!selectedDb || !selectedDbName} onClick={() => setCurrentStep(1)}>
            下一步
          </Button>
        </Card>
      )}

      {/* Step 1: Upload File */}
      {currentStep === 1 && (
        <Card title="上传数据文件">
          <Dragger
            accept=".csv,.json,.tsv,.sql,.xlsx"
            showUploadList={false}
            customRequest={handleFileUpload}
            disabled={loading}
          >
            <p className="ant-upload-drag-icon"><InboxOutlined /></p>
            <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
            <p className="ant-upload-hint">支持 CSV、JSON、TSV、SQL 格式</p>
          </Dragger>

          {preview && (
            <Card size="small" title="文件预览" style={{ marginTop: 16 }}>
              <Descriptions column={3} size="small" style={{ marginBottom: 16 }}>
                <Descriptions.Item label="文件名">{preview.fileName}</Descriptions.Item>
                <Descriptions.Item label="大小">{(preview.fileSize / 1024).toFixed(1)} KB</Descriptions.Item>
                <Descriptions.Item label="格式"><Tag color="blue">{preview.format.toUpperCase()}</Tag></Descriptions.Item>
                <Descriptions.Item label="字段数">{preview.columns?.length}</Descriptions.Item>
                <Descriptions.Item label="预览行数">{preview.totalPreviewRows}</Descriptions.Item>
              </Descriptions>

              <Alert message={`检测到 ${preview.columns?.length} 个字段：${preview.columns?.join(', ')}`} type="info" showIcon style={{ marginBottom: 12 }} />

              <Table size="small" scroll={{ x: 'max-content' }}
                dataSource={preview.sampleData?.slice(0, 5)}
                rowKey={(_, i) => String(i)}
                columns={preview.columns?.map((col: string) => ({
                  title: (
                    <Space direction="vertical" size={0}>
                      <span>{col}</span>
                      {preview.columnTypeHints?.[col] && (
                        <Tag color="cyan" style={{ fontSize: 10 }}>{preview.columnTypeHints[col]}</Tag>
                      )}
                    </Space>
                  ),
                  dataIndex: col,
                  ellipsis: true,
                  width: 150,
                  render: (v: any) => v != null ? String(v) : <span style={{ color: '#ccc' }}>NULL</span>,
                }))}
              />
            </Card>
          )}

          <Space style={{ marginTop: 16 }}>
            <Button onClick={() => setCurrentStep(0)}>上一步</Button>
            <Button type="primary" disabled={!preview} onClick={() => setCurrentStep(2)}>下一步</Button>
          </Space>
        </Card>
      )}

      {/* Step 2: Configure Import */}
      {currentStep === 2 && (
        <Card title="配置导入参数">
          <Row gutter={24}>
            <Col span={12}>
              <Form layout="vertical">
                <Form.Item label="目标表">
                  <Space>
                    <Switch checked={createTable} onChange={setCreateTable} />
                    <span>{createTable ? '创建新表' : '导入已有表'}</span>
                  </Space>
                </Form.Item>

                {createTable ? (
                  <Form.Item label="新表名">
                    <Input value={newTableName} onChange={e => setNewTableName(e.target.value)}
                      placeholder={`import_${Date.now()}`} />
                  </Form.Item>
                ) : (
                  <Form.Item label="选择已有表">
                    <Select style={{ width: '100%' }} value={selectedTable || undefined} placeholder="请选择"
                      onChange={setSelectedTable} showSearch>
                      {tableNames.map((n: string) => <Option key={n} value={n}>{n}</Option>)}
                    </Select>
                  </Form.Item>
                )}

                <Form.Item label="批次大小">
                  <Input type="number" value={batchSize} onChange={e => setBatchSize(Number(e.target.value))} />
                </Form.Item>

                <Form.Item label="导入前清空表">
                  <Switch checked={truncateFirst} onChange={setTruncateFirst} />
                  {truncateFirst && <Alert message="⚠️ 将清空目标表所有数据！" type="warning" showIcon style={{ marginTop: 8 }} />}
                </Form.Item>
              </Form>
            </Col>

            <Col span={12}>
              <Card size="small" title="字段映射预览">
                <Table size="small" pagination={false}
                  dataSource={preview?.columns?.map((col: string, idx: number) => ({
                    key: idx,
                    source: col,
                    target: col,
                    type: preview.columnTypeHints?.[col] || 'VARCHAR(255)',
                  }))}
                  columns={[
                    { title: '源字段', dataIndex: 'source', render: (v: string) => <Tag>{v}</Tag> },
                    { title: '→', width: 40, render: () => <span style={{ color: '#999' }}>→</span> },
                    { title: '目标字段', dataIndex: 'target' },
                    { title: '推断类型', dataIndex: 'type', render: (v: string) => <Tag color="blue">{v}</Tag> },
                  ]}
                />
              </Card>
            </Col>
          </Row>

          <Space style={{ marginTop: 16 }}>
            <Button onClick={() => setCurrentStep(1)}>上一步</Button>
            <Button type="primary" loading={importing} onClick={handleImport}
              disabled={createTable ? false : !selectedTable}>
              <ImportOutlined /> 开始导入
            </Button>
          </Space>
        </Card>
      )}

      {/* Step 3: Result */}
      {currentStep === 3 && result && (
        <Card title="导入结果">
          <Result
            status={result.success ? 'success' : 'warning'}
            title={result.success ? '导入成功' : '导入完成（有错误）'}
            subTitle={result.message}
            extra={[
              <Button type="primary" onClick={reset}>导入更多</Button>,
              <Button onClick={() => setCurrentStep(0)}>重新开始</Button>,
            ]}
          />

          <Row gutter={16} style={{ marginTop: 24 }}>
            <Col span={6}><Card size="small"><Statistic title="总行数" value={result.totalRows} /></Card></Col>
            <Col span={6}><Card size="small"><Statistic title="成功行数" value={result.successRows} valueStyle={{ color: '#3f8600' }} /></Card></Col>
            <Col span={6}><Card size="small"><Statistic title="失败行数" value={result.errorRows} valueStyle={{ color: result.errorRows > 0 ? '#cf1322' : '#3f8600' }} /></Card></Col>
            <Col span={6}><Card size="small"><Statistic title="耗时" value={((result.endTime - result.startTime) / 1000).toFixed(1)} suffix="秒" /></Card></Col>
          </Row>

          {result.errors && result.errors.length > 0 && (
            <Card size="small" title="错误详情" style={{ marginTop: 16 }}>
              {result.errors.map((err: string, idx: number) => (
                <Alert key={idx} message={err} type="error" showIcon style={{ marginBottom: 4 }} />
              ))}
            </Card>
          )}
        </Card>
      )}
    </div>
  );
};

export default DataImportPage;
