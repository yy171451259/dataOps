import React, { useEffect, useState } from 'react';
import { Table, Card, Select, Button, message, Tag, Row, Col, Descriptions, Spin, Space } from 'antd';
const { Option } = Select;
import { ReloadOutlined, SearchOutlined, DatabaseOutlined, TableOutlined } from '@ant-design/icons';
import { metadataApi, instanceApi } from '../utils/api';

const formatSize = (bytes: number): string => {
  if (bytes == null || bytes === 0) return '-';
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
  if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  return (bytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB';
};

const MetadataPage: React.FC = () => {
  const [databases, setDatabases] = useState<any[]>([]);
  const [selectedDb, setSelectedDb] = useState<string>();
  const [selectedSchemaName, setSelectedSchemaName] = useState<string>();
  const [dbNames, setDbNames] = useState<string[]>([]);
  const [loadingDbNames, setLoadingDbNames] = useState(false);
  const [tables, setTables] = useState<any[]>([]);
  const [columns, setColumns] = useState<any[]>([]);
  const [selectedTable, setSelectedTable] = useState<any>(null);
  const [loading, setLoading] = useState(false);
  const [collecting, setCollecting] = useState(false);

  useEffect(() => {
    instanceApi.list().then(res => setDatabases(res.data.data || []));
  }, []);

  // 当实例变化时加载数据库列表
  useEffect(() => {
    if (selectedDb) {
      setLoadingDbNames(true);
      instanceApi.getSchemas(selectedDb)
        .then(res => setDbNames(res.data.data || []))
        .catch(() => setDbNames([]))
        .finally(() => setLoadingDbNames(false));
    } else {
      setDbNames([]);
    }
    setSelectedSchemaName(undefined);
  }, [selectedDb]);

  const handleCollect = async () => {
    if (!selectedDb) return;
    setCollecting(true);
    try {
      const res = await metadataApi.collect(selectedDb, selectedSchemaName);
      message.success(res.data.message || '采集完成');
      loadTables();
    } catch {
      message.error('OK');
    } finally {
      setCollecting(false);
    }
  };

  const loadTables = async () => {
    if (!selectedDb) return;
    setLoading(true);
    try {
      const params: any = { databaseId: selectedDb, page: 1, size: 9999 };
      if (selectedSchemaName) {
        params.schemaName = selectedSchemaName;
      }
      const res = await metadataApi.listTables(params);
      setTables(res.data.data?.records || []);
      setSelectedTable(null);
      setColumns([]);
    } finally {
      setLoading(false);
    }
  };

  const handleTableClick = async (record: any) => {
    setSelectedTable(record);
    setLoading(true);
    try {
      const res = await metadataApi.listColumns(record.id);
      setColumns(res.data.data || []);
    } finally {
      setLoading(false);
    }
  };

  const columnCols = [
    { title: '序号', dataIndex: 'ordinalPosition', width: 60 },
    { title: '字段名', dataIndex: 'columnName', width: 150 },
    { title: '数据类型', dataIndex: 'dataType', width: 100 },
    { title: '长度', dataIndex: 'columnLength', width: 80 },
    { title: '可空', dataIndex: 'isNullable', width: 60, render: (v: boolean) => v ? '是' : '否' },
    { title: '主键', dataIndex: 'isPrimaryKey', width: 60, render: (v: boolean) => v ? <Tag color="blue">PK</Tag> : '' },
    { title: '注释', dataIndex: 'columnComment', ellipsis: true },
    { title: '业务名', dataIndex: 'businessName', width: 120 },
    {
      title: '敏感', dataIndex: 'isSensitive', width: 60,
      render: (v: boolean) => v ? <Tag color="red">是</Tag> : <Tag>否</Tag>
    },
  ];

  if (loading && !selectedTable && tables.length === 0) {
    return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;
  }

  return (
    <div style={{ height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      <Card title={<><DatabaseOutlined /> 元数据管理</>} style={{ marginBottom: 16, flexShrink: 0 }}>
        <Row gutter={16} align="middle">
          <Col>
            <Select
              style={{ width: 200 }}
              placeholder="选择实例"
              value={selectedDb}
              onChange={(v) => { setSelectedDb(v); }}
              showSearch optionFilterProp="children"
            >
              {databases.map((d: any) => (
                <Option key={d.id} value={d.id}>{d.name} ({d.host}:{d.port})</Option>
              ))}
            </Select>
          </Col>
          <Col>
            <Select
              style={{ width: 160 }}
              placeholder="选择数据库"
              value={selectedSchemaName}
              onChange={(v) => { setSelectedSchemaName(v); }}
              options={dbNames.map(d => ({ label: d, value: d }))}
              loading={loadingDbNames}
              disabled={!selectedDb}
              allowClear
            />
          </Col>
          <Col>
            <Space>
              <Button type="primary" icon={<SearchOutlined />} onClick={loadTables}>查询</Button>
              <Button icon={<ReloadOutlined />} onClick={handleCollect} loading={collecting}>采集元数据</Button>
            </Space>
          </Col>
        </Row>
      </Card>

      <Row gutter={16} style={{ flex: 1, overflow: 'hidden' }}>
        <Col span={selectedTable ? 10 : 24} style={{ height: '100%' }}>
          <Card title={<><TableOutlined /> 数据表</>} size="small" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
            bodyStyle={{ flex: 1, overflow: 'hidden', padding: 0 }}>
            <Table
              dataSource={tables}
              size="small"
              scroll={{ x: 500, y: 'calc(100vh - 280px)' }}
              columns={[
                { title: '表名', dataIndex: 'tableName', key: 'tableName', width: 150, ellipsis: true },
                { title: '注释', dataIndex: 'tableComment', key: 'tableComment', width: 180, ellipsis: true },
                { title: '类型', dataIndex: 'tableType', key: 'tableType', width: 70 },
                {
                  title: '行数', dataIndex: 'rowCount', key: 'rowCount', width: 90,
                  sorter: (a: any, b: any) => (a.rowCount || 0) - (b.rowCount || 0),
                  showSorterTooltip: false,
                  render: (v: number) => v != null ? v.toLocaleString() : '-',
                },
                {
                  title: '大小', dataIndex: 'dataSize', key: 'dataSize', width: 90,
                  sorter: (a: any, b: any) => (a.dataSize || 0) - (b.dataSize || 0),
                  showSorterTooltip: false,
                  render: (v: number) => v != null ? formatSize(v) : '-',
                },
              ]}
              rowKey="id"
              pagination={{ pageSize: 100, showSizeChanger: true, pageSizeOptions: ['50', '100', '200', '500'], showTotal: (t) => `共${t} 张表` }}
              onRow={(record) => ({
                onClick: () => handleTableClick(record),
                style: { cursor: 'pointer', background: selectedTable?.id === record.id ? '#e6f7ff' : undefined },
              })}
            />
          </Card>
        </Col>
        {selectedTable && (
          <Col span={14} style={{ height: '100%' }}>
            <Card title={`${selectedTable.tableName} - 字段列表`} size="small" style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
              bodyStyle={{ flex: 1, overflow: 'hidden', padding: 0 }}>
              <Descriptions size="small" column={2} style={{ marginBottom: 8, padding: '0 12px' }}>
                <Descriptions.Item label="注释">{selectedTable.tableComment}</Descriptions.Item>
                <Descriptions.Item label="类型">{selectedTable.tableType}</Descriptions.Item>
              </Descriptions>
              <Table dataSource={columns} columns={columnCols} rowKey="id" size="small" pagination={false}
                scroll={{ y: 'calc(100vh - 360px)' }} />
            </Card>
          </Col>
        )}
      </Row>
    </div>
  );
};

export default MetadataPage;
