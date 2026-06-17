import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';

import { useNavigate } from 'react-router-dom';

import Editor, { OnMount } from '@monaco-editor/react';

import {

  Button, Table, message, Select, Tabs, Dropdown, Tooltip, Tag,

  Drawer, Modal, Popconfirm, Divider, Empty, Space, Input, Rate,

  Form, Alert, Radio, Row, Col, Spin,

} from 'antd';

import {

  PlayCircleOutlined, FormatPainterOutlined,

  HistoryOutlined, StarOutlined, StarFilled, ThunderboltOutlined, ExperimentOutlined,

  SafetyOutlined, CheckCircleOutlined, WarningOutlined,

  CopyOutlined, DeleteOutlined, EditOutlined,

  SearchOutlined, PlusOutlined, ClockCircleOutlined, FireOutlined,

  DatabaseOutlined, CloseOutlined, SettingOutlined, DownOutlined,

  FileTextOutlined, TableOutlined, UpOutlined,

  ProfileOutlined, DownloadOutlined,

  StopOutlined, SendOutlined,

} from '@ant-design/icons';

import dayjs from 'dayjs';

import { useAppStore } from '../store/useAppStore';

import { sqlApi, instanceApi, exportApi, ticketApi } from '../utils/api';

import { createSqlCompletionProvider, SchemaInfo } from './SqlCompletionProvider';

import ObjectBrowser from './ObjectBrowser';



const { TextArea } = Input;



// ==================== Types ====================

interface QueryResult {

  columns: string[];

  data: any[];

  affectRows?: number;

  executionTime?: number;

  success?: boolean;

  errorMessage?: string;

  hasMore?: boolean;

  executedSql?: string;

  _dataChangeBlocked?: boolean;

}



interface SqlEditorProps {

  databaseId?: string;

}



const MIN_EDITOR_HEIGHT = 150;



// ==================== 底部面板：我的SQL/收藏/执行历史 ====================

interface BottomPanelProps {

  visible: boolean;

  activeTabData: any;

  onUseQuery: (sql: string) => void;
  editorRef: React.MutableRefObject<any>;
}



const BottomPanel: React.FC<BottomPanelProps> = ({ visible, activeTabData, onUseQuery, editorRef }) => {
  const [panelTab, setPanelTab] = useState<string>('favorites');
  const [searchText, setSearchText] = useState('');
  const [editModalOpen, setEditModalOpen] = useState(false);
  const [editingQuery, setEditingQuery] = useState<any>(null);
  const [editForm, setEditForm] = useState({ name: '', sql: '' });

  const { savedQueries, addSavedQuery, updateSavedQuery, deleteSavedQuery, toggleFavorite, executionHistory, clearExecutionHistory } = useAppStore();

  const dbFilteredQueries = useMemo(() => {
    const dbId = activeTabData?.databaseId;
    const dbName = activeTabData?.databaseName;
    if (!dbId) return savedQueries.filter(q => !q.databaseId);
    return savedQueries.filter(q => q.databaseId === dbId && q.databaseName === dbName);
  }, [savedQueries, activeTabData?.databaseId, activeTabData?.databaseName]);

  const dbFilteredHistory = useMemo(() => {
    const dbId = activeTabData?.databaseId;
    const dbName = activeTabData?.databaseName;
    if (!dbId) return executionHistory.filter(h => !h.databaseId);
    return executionHistory.filter(h => h.databaseId === dbId && h.databaseName === dbName);
  }, [executionHistory, activeTabData?.databaseId, activeTabData?.databaseName]);

  const filteredSavedQueries = useMemo(() => {
    if (!searchText.trim()) return dbFilteredQueries;
    const kw = searchText.toLowerCase();
    return dbFilteredQueries.filter(q => q.name.toLowerCase().includes(kw) || q.sql.toLowerCase().includes(kw));
  }, [dbFilteredQueries, searchText]);

  const filteredHistory = useMemo(() => {
    if (!searchText.trim()) return dbFilteredHistory;
    const kw = searchText.toLowerCase();
    return dbFilteredHistory.filter(h => h.sql.toLowerCase().includes(kw));
  }, [dbFilteredHistory, searchText]);

  const getPopularityRate = (count: number): number => {
    if (count >= 20) return 5; if (count >= 10) return 4;
    if (count >= 5) return 3; if (count >= 2) return 2;
    return count >= 1 ? 1 : 0;
  };

  const handleAddFavorite = () => {
    const editor = editorRef.current;
    let sql = activeTabData?.sql || '';
    // 优先取编辑器选中文本
    if (editor) {
      const selection = editor.getModel()?.getValueInRange(editor.getSelection() || editor.getModel()!.getFullModelRange());
      if (selection?.trim()) sql = selection;
    }
    if (!sql.trim()) { message.warning('请先输入SQL或选中SQL'); return; }
    setEditingQuery(null);
    setEditForm({ name: `Query ${savedQueries.length + 1}`, sql });
    setEditModalOpen(true);
  };

  const handleEdit = (q: any) => { setEditingQuery(q); setEditForm({ name: q.name, sql: q.sql }); setEditModalOpen(true); };
  const handleDelete = (id: string) => { deleteSavedQuery(id); };
  const handleSaveEdit = () => {
    if (editingQuery) {
      updateSavedQuery(editingQuery.id, { ...editingQuery, ...editForm });
    } else {
      addSavedQuery({
        id: Date.now().toString(), name: editForm.name || `Query ${savedQueries.length + 1}`,
        sql: editForm.sql, databaseId: activeTabData?.databaseId, databaseName: activeTabData?.databaseName,
        isFavorite: true, createdAt: Date.now(), executionCount: 1, totalDuration: 0,
      });
    }
    setEditModalOpen(false);
  };
  const handleClearHistory = () => { clearExecutionHistory(); };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden', background: '#fff' }}>
      <div style={{ display: 'flex', alignItems: 'center', padding: '4px 8px', borderBottom: '1px solid #e8e8e8', background: '#fafafa', gap: 6, flexShrink: 0 }}>
        <Input size="small" placeholder="Filter..." prefix={<SearchOutlined style={{ color: '#bbb' }} />} value={searchText} onChange={e => setSearchText(e.target.value)} allowClear style={{ width: 130, fontSize: 11 }} />
        <div style={{ flex: 1 }} />
        <Tooltip title="添加收藏">
          <Button type="text" size="small" icon={<StarOutlined />} onClick={handleAddFavorite}></Button>
        </Tooltip>
        <Tabs activeKey={panelTab} onChange={setPanelTab} size="small" tabBarStyle={{ margin: 0, minHeight: 24, borderBottom: 'none' }} style={{ flexShrink: 0 }}
          items={[
            { key: 'favorites', label: <span style={{ fontSize: 11 }}>收藏</span>, children: null },
            { key: 'history', label: <span style={{ fontSize: 11 }}>历史</span>, children: null },
          ]}
        />
      </div>
      <div style={{ flex: 1, overflow: 'auto' }}>
        <Table
          dataSource={panelTab === 'favorites' ? filteredSavedQueries : filteredHistory}
          rowKey="id"
          size="small"
          pagination={{ size: 'small', pageSize: 20, showSizeChanger: false, showTotal: (t: number) => `${t} 条` }}
          showHeader={false}
          locale={{ emptyText: <div style={{ color: '#bbb', padding: 20 }}>{panelTab === 'favorites' ? '暂无收藏' : '暂无历史'}</div> }}
          onRow={(record: any) => ({
            onClick: () => onUseQuery(record.sql),
            style: { cursor: 'pointer' },
          })}
          columns={panelTab === 'favorites' ? [
            {
              title: '', dataIndex: 'name', key: 'name',
              render: (_: any, q: any) => (
                <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                  {q.isFavorite && <StarFilled style={{ fontSize: 10, color: '#faad14' }} />}
                  <span style={{ fontWeight: 500, fontSize: 12 }}>{q.name}</span>
                  <Rate disabled value={getPopularityRate(q.executionCount || 0)} count={5} style={{ fontSize: 10 }} />
                </div>
              ),
            },
            {
              title: '', key: 'sql',
              render: (_: any, q: any) => (
                <div style={{ fontSize: 11, color: '#888', fontFamily: 'Consolas, Monaco, monospace', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {q.sql.slice(0, 100)}{q.sql.length > 100 ? '\u2026' : ''}
                </div>
              ),
            },
            {
              title: '', key: 'info', width: 100,
              render: (_: any, q: any) => (
                <span style={{ fontSize: 10, color: '#bbb' }}>{q.executionCount || 0}次 · {dayjs(q.createdAt).format('MM-DD HH:mm')}</span>
              ),
            },
            {
              title: '', key: 'actions', width: 60,
              render: (_: any, q: any) => (
                <Space size={0}>
                  <Button type="text" size="small" icon={<EditOutlined style={{ fontSize: 12 }} />} onClick={(e) => { e.stopPropagation(); handleEdit(q); }} />
                  <Popconfirm title="Delete?" onConfirm={() => handleDelete(q.id)}>
                    <Button type="text" size="small" danger icon={<DeleteOutlined style={{ fontSize: 12 }} />} onClick={e => e.stopPropagation()} />
                  </Popconfirm>
                </Space>
              ),
            },
          ] : [
            {
              title: '', key: 'sql',
              render: (_: any, h: any) => (
                <div style={{ fontSize: 11, color: '#666', fontFamily: 'Consolas, Monaco, monospace', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {h.sql.slice(0, 120)}{h.sql.length > 120 ? '\u2026' : ''}
                </div>
              ),
            },
            {
              title: '', key: 'info', width: 140,
              render: (_: any, h: any) => (
                <span style={{ fontSize: 10, color: '#bbb' }}>
                  {dayjs(h.timestamp || h.executedAt).format('MM-DD HH:mm:ss')} · {h.duration || h.executionTime}ms
                </span>
              ),
            },
          ]}
        />
      </div>
      <Modal title={editingQuery ? '编辑SQL' : '添加收藏'} open={editModalOpen} onOk={handleSaveEdit} onCancel={() => setEditModalOpen(false)} okText={editingQuery ? '保存' : '添加'}>
        <div style={{ marginBottom: 12 }}>
          <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>Name</label>
          <Input value={editForm.name} onChange={e => setEditForm(prev => ({ ...prev, name: e.target.value }))} />
        </div>
        <div>
          <label style={{ display: 'block', fontSize: 12, color: '#666', marginBottom: 4 }}>SQL</label>
          <Input.TextArea value={editForm.sql} rows={6} onChange={e => setEditForm(prev => ({ ...prev, sql: e.target.value }))} style={{ fontFamily: 'Consolas, monospace' }} />
        </div>
      </Modal>
    </div>
  );
};

const ExecutionStats: React.FC<{ result: any }> = ({ result }) => {
  const isDataSet = result?.data?.length > 0;
  const statsRows: { name: string; value: any }[] = [];

  if (isDataSet) {

    statsRows.push(

      { name: 'Rows fetched', value: result.data?.length ?? 0 },

      { name: 'Columns', value: result.columns?.length ?? 0 },

    );

  }

  if (result.affectRows !== undefined) {

    statsRows.push({ name: 'Updated Rows', value: result.affectRows });

  }

  statsRows.push(

    { name: 'Execute time', value: `${result.executionTime ?? 0}s` },

    { name: 'Start time', value: dayjs().format('YYYY-MM-DD HH:mm:ss') },

    { name: 'Finish time', value: dayjs().format('YYYY-MM-DD HH:mm:ss') },

  );



  if (!isDataSet && result.errorMessage) {

    statsRows.push({ name: 'Error', value: result.errorMessage });

  }



  const statColumns = [

    { title: 'Name', dataIndex: 'name', key: 'name', width: 140,

      render: (text: string) => <span style={{ fontFamily: '"Segoe UI", sans-serif', fontSize: 12, fontWeight: 500 }}>{text}</span> },

    { title: 'Value', dataIndex: 'value', key: 'value',

      render: (val: any) => <span style={{ fontFamily: 'Consolas, Monaco, monospace', fontSize: 12 }}>{val}</span> },

  ];



  return (

    <Table

      dataSource={statsRows.map((r, i) => ({ ...r, _key: i }))}

      columns={statColumns}

      rowKey="_key"

      size="small"

      pagination={false}

      showHeader={true}

      bordered={false}

      style={{ fontSize: 12 }}

      className="dbeaver-exec-stats-table"

    />

  );

};



// ==================== SQL 类型检测 ====================

const READ_ONLY_KEYWORDS = ['SELECT', 'SHOW', 'DESCRIBE', 'DESC', 'EXPLAIN', 'WITH'];



/** 检测SQL是否为数据变更操作（DML/DDL），需要走工单流程 */

const isDataChangeSQL = (sql: string): boolean => {
  let cleaned = sql
    .replace(/--.*$/gm, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .trim();
  cleaned = cleaned.replace(/^[(\s]+/, '');
  const firstWord = cleaned.match(/^\w+/)?.[0]?.toUpperCase() || '';
  if (!firstWord) return false;
  return !READ_ONLY_KEYWORDS.includes(firstWord);
};



/** 根据SQL自动判断变更类型 */

const detectChangeType = (sql: string): string => {

  const cleaned = sql.replace(/--.*$/gm, '').replace(/\/\*[\s\S]*?\*\//g, '').trim().replace(/^[(\s]+/, '');

  const firstWord = cleaned.match(/^\w+/)?.[0]?.toUpperCase() || '';

  // DDL类关键词

  if (['ALTER', 'CREATE', 'DROP', 'TRUNCATE', 'RENAME'].includes(firstWord)) return 'DDL';

  // DML类关键词

  if (['INSERT', 'UPDATE', 'DELETE', 'REPLACE', 'MERGE'].includes(firstWord)) return 'DML';

  return 'DML'; // 默认DML

};



// ==================== SQL 工作区 - DBeaver 风格布局 ====================



const SqlEditor: React.FC<SqlEditorProps> = () => {

  const navigate = useNavigate();

  const { sqlTabs, activeTab, setActiveTab, addTab, removeTab, updateTab, savedQueries } = useAppStore();

  const activeTabData = sqlTabs.find(t => t.key === activeTab) || sqlTabs[0] || null;



  const [loading, setLoading] = useState(false);

  const [results, setResults] = useState<Record<string, QueryResult[]>>({});

  const [paginationLoading, setPaginationLoading] = useState(false);

  const resultContainerRef = useRef<HTMLDivElement>(null);

  const [explainLoading, setExplainLoading] = useState(false);

  const [explainResult, setExplainResult] = useState<QueryResult | null>(null);

  const [explainDrawerOpen, setExplainDrawerOpen] = useState(false);

  const [auditResult, setAuditResult] = useState<any>(null);

  const [auditDrawerOpen, setAuditDrawerOpen] = useState(false);

  const [auditLoading, setAuditLoading] = useState(false);

  const [databases, setDatabases] = useState<any[]>([]);

  const [schemaNames, setSchemaNames] = useState<string[]>([]);

  const [loadingSchemaNames, setLoadingSchemaNames] = useState(false);

  const [schemaLoaded, setSchemaLoaded] = useState(false);

  const [editorHeight, setEditorHeight] = useState(300);

  const containerRef = useRef<HTMLDivElement>(null);

  const isDragging = useRef(false);

  const editorRef = useRef<any>(null);

  const monacoRef = useRef<any>(null);

  const schemaRef = useRef<SchemaInfo[]>([]);

  const completionDisposableRef = useRef<any>(null);



  const [activeResultKey, setActiveResultKey] = useState<string>('my-sql');



  //
  const [ticketModalSql, setTicketModalSql] = useState('');

  const [ticketModalChangeType, setTicketModalChangeType] = useState('DML');

  const [savingTicket, setSavingTicket] = useState(false);

  const [ticketForm] = Form.useForm();

  const [ticketModalVisible, setTicketModalVisible] = useState(false);


  useEffect(() => {
    instanceApi.list().then(res => {
      const list = res.data.data || [];
      setDatabases(list);
      if (list.length > 0 && !activeTabData?.databaseId) {
        const firstDb = list[0];
        updateTab(activeTab, { databaseId: firstDb.id, databaseName: undefined });
      }
    });
  }, []);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { e.preventDefault(); handleExecute(); }
      if (e.key === 'F8') { e.preventDefault(); handleExecute(); }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [activeTab, activeTabData?.sql]);



  // 自动选择第一个Schema

  useEffect(() => {

    if (activeTabData?.databaseId) {

      setLoadingSchemaNames(true);

      instanceApi.getSchemas(activeTabData.databaseId)

        .then(res => {

          const names = res.data.data || [];

          setSchemaNames(names);

          // Auto-select first schema if current selection is invalid
          const currentDbNameExists = names.includes(activeTabData?.databaseName);

          if (names.length > 0 && (!activeTabData?.databaseName || !currentDbNameExists)) {

            updateTab(activeTab, { databaseName: names[0] });

          }

        })

        .catch(() => setSchemaNames([]))

        .finally(() => setLoadingSchemaNames(false));

    } else { setSchemaNames([]); }

  }, [activeTabData?.databaseId]);



  // Schema 加载用于补全

  const loadSchema = useCallback(async (databaseId: string, databaseName?: string) => {

    if (!databaseId) return;

    try {

      const res = await (databaseName

        ? instanceApi.getSchema(databaseId, databaseName)

        : instanceApi.getSchema(databaseId));

      if (res.data?.data) { schemaRef.current = res.data.data; setSchemaLoaded(true); registerCompletionProvider(); }

    } catch { console.warn('加载数据库Schema失败'); }

  }, []);



  const registerCompletionProvider = useCallback(() => {

    const monaco = monacoRef.current;

    if (!monaco) return;

    if (completionDisposableRef.current) completionDisposableRef.current.dispose();

    completionDisposableRef.current = monaco.languages.registerCompletionItemProvider(

      'mysql', createSqlCompletionProvider(() => schemaRef.current)

    );

  }, []);



  useEffect(() => {

    if (activeTabData?.databaseId) { setSchemaLoaded(false); loadSchema(activeTabData.databaseId, activeTabData.databaseName); }

    else { schemaRef.current = []; setSchemaLoaded(false); }

  }, [activeTabData?.databaseId, activeTabData?.databaseName, loadSchema]);



  const handleEditorMount: OnMount = (editor, monaco) => {

    editorRef.current = editor; monacoRef.current = monaco; registerCompletionProvider();

  };

  const MIN_EDITOR_HEIGHT = 100;

  const handleResizeStart = (e: React.MouseEvent) => {
    e.preventDefault(); isDragging.current = true;
    const startY = e.clientY; const startH = editorHeight;
    const onMouseMove = (ev: MouseEvent) => {
      if (!isDragging.current) return;
      setEditorHeight(Math.max(MIN_EDITOR_HEIGHT, Math.min(600, startH + (ev.clientY - startY))));
    };
    const onMouseUp = () => {
      isDragging.current = false;
      document.removeEventListener('mousemove', onMouseMove); document.removeEventListener('mouseup', onMouseUp);
    };
    document.addEventListener('mousemove', onMouseMove); document.addEventListener('mouseup', onMouseUp);
  };



  const getSqlToExecute = () => {

    const editor = editorRef.current;

    if (editor) { const s = editor.getSelection(); if (s && !s.isEmpty()) return editor.getModel().getValueInRange(s); }

    return activeTabData?.sql || '';

  };



  const handleExecute = async () => {

    if (!activeTabData?.databaseId) { message.warning('OK'); return; }

    setLoading(true);

    const sqlToRun = getSqlToExecute();

    try {

      // 检测数据变更操作，拦截并提示走工单

      if (isDataChangeSQL(sqlToRun)) {

        const blockedResult: QueryResult = {

          columns: [],

          data: [],

          success: true,

          executionTime: 0,

          executedSql: sqlToRun,

          _dataChangeBlocked: true,

        };

        setResults(prev => ({ ...prev, [activeTab]: [...(prev[activeTab] || []), blockedResult] }));

        const newIdx = (results[activeTab]?.length || 0);

        setActiveResultIdx(newIdx);

        setActiveResultKey(String(newIdx));

        message.warning('OK');

        const { addExecutionRecord } = useAppStore.getState();

        addExecutionRecord({ sql: getSqlToExecute(), databaseId: activeTabData.databaseId!, databaseName: activeTabData.databaseName, executionTime: 0, success: false });

        return;

      }

      // 正常执行 SQL 查询

      const startTime = Date.now();

      const res = await sqlApi.execute(activeTabData.databaseId, sqlToRun, activeTabData.databaseName, 0, 1000);

      // 后端返回 code!=200 视为错误
      if (res.data?.code && res.data.code !== 200) {
        throw new Error(res.data.message || '执行失败');
      }

      const executionTime = Date.now() - startTime;

      const resultArr = res.data?.data || [];

      const queryResults: QueryResult[] = resultArr.map((result: any) => ({
        columns: result.columns || [],
        data: result.data || [],
        affectRows: result.affectRows,
        success: true,
        executionTime: result.executionTime || executionTime,
        executedSql: sqlToRun,
      }));

      setResults(prev => ({ ...prev, [activeTab]: [...(prev[activeTab] || []), ...queryResults] }));

      const newIdx = (results[activeTab]?.length || 0) + queryResults.length - 1;

      setActiveResultIdx(newIdx);

      setActiveResultKey(String(newIdx));

      const { addExecutionRecord } = useAppStore.getState();

      addExecutionRecord({ sql: sqlToRun, databaseId: activeTabData.databaseId!, databaseName: activeTabData.databaseName, executionTime, success: true });

    } catch (err: any) {
      const errorMsg = err.response?.data?.message || err.message || '执行失败';
      const errorResult: QueryResult = {
        columns: [],
        data: [],
        success: false,
        errorMessage: errorMsg,
        executedSql: sqlToRun,
      };
      setResults(prev => ({ ...prev, [activeTab]: [...(prev[activeTab] || []), errorResult] }));
      const newIdx = (results[activeTab]?.length || 0);
      setActiveResultIdx(newIdx);
      setActiveResultKey(String(newIdx));
    } finally { setLoading(false); }

  };



  /** 快速创建数据变更工单 */

  const handleOpenTicketModal = (sql: string) => {

    if (!activeTabData?.databaseId || !activeTabData?.databaseName) {

      message.warning('OK');

      return;

    }

    setTicketModalSql(sql);

    setTicketModalChangeType(detectChangeType(sql));

    ticketForm.resetFields();

    ticketForm.setFieldsValue({

      title: `数据变更 - ${new Date().toLocaleString('zh-CN')}`,

      priority: 'normal',

    });

    setTicketModalVisible(true);

  };



  /** 提交快速工单 */

  const handleSubmitTicket = async () => {

    try {

      const values = await ticketForm.validateFields();

      setSavingTicket(true);

      await ticketApi.create({

        title: values.title,

        databaseId: activeTabData?.databaseId,

        databaseName: activeTabData?.databaseName,

        sqlContent: ticketModalSql,

        changeType: ticketModalChangeType,

        priority: values.priority || 'normal',

        description: '',

        approvalTimeoutHours: 0,

      });

      message.success('OK');

    } catch (err: any) {

      if (err?.errorFields) return; // 表单校验失败

      message.error(err.response?.data?.message || '创建工单失败');

    } finally {

      setSavingTicket(false);

    }

  };



  const [activeResultIdx, setActiveResultIdx] = useState<number>(0);

  const [resultViewMode, setResultViewMode] = useState<'grid' | 'text' | 'record'>('grid');

  const [textViewRow, setTextViewRow] = useState(0);



  // 表结构查看器

  const [tableStructureVisible, setTableStructureVisible] = useState(false);

  const [tableStructureInfo, setTableStructureInfo] = useState<{ tableName: string; columns: any[]; indexes?: any[]; ddl?: string } | null>(null);

  // 打开表结构查看器
  const openTableStructure = async (databaseId: string, databaseName: string, tableName: string) => {
    setTableStructureVisible(true);
    setTableStructureInfo({ tableName, columns: [] });
    try {
      // columns from getBrowserSchema
      const [schemaRes, detailRes] = await Promise.all([
        instanceApi.getBrowserSchema(databaseId, databaseName).catch(() => null),
        instanceApi.getTableDetail(databaseId, tableName, databaseName).catch(() => null),
      ]);
      // extract columns of the specific table from schema
      const schema = schemaRes?.data?.data || {};
      const allTables = schema.tables || [];
      const tableInfo = allTables.find((t: any) => t.name === tableName);
      const detail = detailRes?.data?.data || {};
      setTableStructureInfo({
        tableName,
        columns: tableInfo?.columns || [],
        indexes: detail.indexes || [],
        ddl: detail.ddl || null,
      });
    } catch {
      setTableStructureInfo({ tableName, columns: [] });
    }
  };

  const currentResults = results[activeTab] || [];

  const currentResult = currentResults[activeResultIdx];

  const currentDatabase = databases.find((db: any) => db.id === activeTabData?.databaseId);


  /** 导出辅助：生成文件并触发下载 */

  const downloadFile = useCallback((content: string, filename: string, mimeType: string) => {

    const bom = '\uFEFF';

    const blob = new Blob([bom + content], { type: `${mimeType};charset=utf-8` });

    const url = URL.createObjectURL(blob);

    const a = document.createElement('a');

    a.href = url; a.download = filename; document.body.appendChild(a);

    a.click(); document.body.removeChild(a);

    URL.revokeObjectURL(url);

  }, []);



  /** 导出处理 */

  const handleExport = useCallback((format: string) => {

    const cols: string[] = currentResult?.columns || [];

    const rows: any[] = currentResult?.data || [];

    if (cols.length === 0 || (cols.length === 1 && cols[0] === 'result')) {

      // 非数据结果集（如执行统计）也尝试导出

      if (rows.length === 0) { message.warning('OK'); return; }

    }

    const dbName = activeTabData?.databaseName || 'export';

    const ts = dayjs().format('YYYYMMDD_HHmmss');

    const baseName = `${dbName}_${ts}`;



    const escapeCSV = (v: any) => {

      if (v === null || v === undefined) return '';

      const s = String(v);

      if (s.includes(',') || s.includes('"') || s.includes('\n')) {

        return '"' + s.replace(/"/g, '""') + '"';

      }

      return s;

    };



    const escapeSQL = (v: any) => {

      if (v === null || v === undefined) return 'NULL';

      if (typeof v === 'number') return String(v);

      return "'" + String(v).replace(/'/g, "''").replace(/\\/g, '\\\\') + "'";

    };



    switch (format) {

      case 'csv': {

        const header = cols.map(c => escapeCSV(c)).join(',');

        const body = rows.map((row: any) => cols.map(c => escapeCSV(row[c])).join(',')).join('\n');

        downloadFile(header + '\n' + body, `${baseName}.csv`, 'text/csv');

        break;

      }

      case 'excel': {

        const header = '<tr>' + cols.map(c => `<th>${c}</th>`).join('') + '</tr>';

        const body = rows.map((row: any) =>

          '<tr>' + cols.map(c => {

            const v = row[c];

            const d = v === null || v === undefined ? '' : String(v).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

            return `<td>${d}</td>`;

          }).join('') + '</tr>'

        ).join('');

        const html = `<html xmlns:o="urn:schemas-microsoft-com:office:office" xmlns:x="urn:schemas-microsoft-com:office:excel"><head><meta charset="utf-8"><!--[if gte mso 9]><xml><x:ExcelWorkbook><x:ExcelWorksheets><x:ExcelWorksheet><x:Name>Sheet1</x:Name><x:WorksheetOptions><x:DisplayGridlines/></x:WorksheetOptions></x:ExcelWorksheet></x:ExcelWorksheets></x:ExcelWorkbook></xml><![endif]--></head><body><table border="1">${header}${body}</table></body></html>`;

        downloadFile(html, `${baseName}.xls`, 'application/vnd.ms-excel');

        break;

      }

      case 'json': {

        const arr = rows.map((row: any) => { const o: any = {}; cols.forEach(c => { o[c] = row[c]; }); return o; });

        downloadFile(JSON.stringify(arr, null, 2), `${baseName}.json`, 'application/json');

        break;

      }

      case 'txt': {

        const header = cols.join('\t');

        const body = rows.map((row: any) => cols.map(c => {

          const v = row[c]; return v === null || v === undefined ? '\\N' : String(v);

        }).join('\t')).join('\n');

        downloadFile(header + '\n' + body, `${baseName}.txt`, 'text/plain');

        break;

      }

      case 'sql': {

        const colList = '`' + cols.join('`, `') + '`';

        const body = rows.map((row: any) =>

          `INSERT INTO \`${dbName}\` (${colList}) VALUES (${cols.map(c => escapeSQL(row[c])).join(', ')});`

        ).join('\n');

        downloadFile(body, `${baseName}.sql`, 'text/plain');

        break;

      }

    }

    message.success("OK");

  }, [currentResult, activeTabData?.databaseName, downloadFile, message]);



  /** 加载更多行（分页滚动） */

  const loadMoreRows = useCallback(async () => {

    if (!activeTabData?.databaseId || !currentResult || paginationLoading) return;

    if (!currentResult.hasMore) return;

    const offset = (currentResult.data || []).length;

    const sql = currentResult.executedSql || getSqlToExecute();

    if (!sql?.trim()) return;

    setPaginationLoading(true);

    try {

      const res = await sqlApi.execute(activeTabData.databaseId, sql, activeTabData.databaseName, offset, 200);

      const newResults: QueryResult[] = (res.data.data || []).map((r: any) => ({ ...r }));

      if (newResults[0]?.data?.length) {

        setResults(prev => {

          const tabResults = [...(prev[activeTab] || [])];

          if (!tabResults[activeResultIdx]) return prev;

          const merged = { ...tabResults[activeResultIdx] };

          merged.data = [...(merged.data || []), ...newResults[0].data];

          merged.hasMore = newResults[0].hasMore;

          tabResults[activeResultIdx] = merged;

          return { ...prev, [activeTab]: tabResults };

        });

      } else {

        setResults(prev => {

          const tabResults = [...(prev[activeTab] || [])];

          if (tabResults[activeResultIdx]) {

            tabResults[activeResultIdx] = { ...tabResults[activeResultIdx], hasMore: false };

          }

          return { ...prev, [activeTab]: tabResults };

        });

      }

    } catch { message.warning('OK'); }

    finally { setPaginationLoading(false); }

  }, [activeTab, activeResultIdx, activeTabData, currentResult, paginationLoading, getSqlToExecute]);



  /** 结果容器滚动检测 - 触底自动加载更多 */

  const handleResultScroll = useCallback((e: React.UIEvent<HTMLDivElement>) => {

    const el = e.currentTarget;

    if (el.scrollHeight - el.scrollTop - el.clientHeight < 40) {

      loadMoreRows();

    }

  }, [loadMoreRows]);



  useEffect(() => { if (activeResultIdx >= currentResults.length) setActiveResultIdx(0); }, [currentResults.length]);

  // 切换结果集时重置文本视图行号

  useEffect(() => { setTextViewRow(0); }, [activeResultIdx]);



  const handleExplain = async () => {

    if (!activeTabData?.databaseId) { message.warning('OK'); return; }

    setExplainLoading(true);

    try {

      const sqlToRun = getSqlToExecute();

      const res = await sqlApi.explain(activeTabData.databaseId, sqlToRun, activeTabData.databaseName);

      const plan = res.data.data; plan.executionTime = Date.now();

      setExplainResult(plan); setExplainDrawerOpen(true);

    } catch (err: any) { message.error(err.response?.data?.message || '获取执行计划失败'); }

    finally { setExplainLoading(false); }

  };



  const handleAudit = async () => {

    const sqlToRun = getSqlToExecute();

    if (!sqlToRun.trim()) { message.warning('OK'); return; }

    setAuditLoading(true);

    try { const res = await sqlApi.audit(sqlToRun); setAuditResult(res.data.data); setAuditDrawerOpen(true); }

    catch (err: any) { message.error('OK'); }

    finally { setAuditLoading(false); }

  };



  const handleCopySql = () => {
    const sql = activeTabData?.sql || '';
    navigator.clipboard.writeText(sql).then(() => message.success('OK')).catch(() => message.error('OK'));
  };

  const handleUseFromBottom = (sql: string) => {
    if (!activeTabData) return;
    updateTab(activeTab, { ...activeTabData, sql });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', width: '100%', background: '#f5f5f5', overflow: 'hidden' }}>

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* 对象浏览器侧边栏 - DBeaver 风格 Database Navigator */}
        <div style={{ width: 260, borderRight: '1px solid #d5d5d5', display: 'flex', flexDirection: 'column', background: '#fff', overflow: 'hidden' }}>
          <div style={{ flex: 1, overflow: 'auto' }}>
            <ObjectBrowser
              databaseId={activeTabData?.databaseId}
              databaseName={activeTabData?.databaseName}
              schemas={schemaNames}
              loadingSchemas={loadingSchemaNames}
              onNewTab={(databaseId, databaseName, tableName) => {
                // 同名编辑器存在则复用，追加SQL
                const existing = sqlTabs.find(t => t.databaseName === databaseName);
                if (existing) {
                  const appendSql = tableName ? `\nSELECT * FROM \`${tableName}\` LIMIT 100;` : '';
                  updateTab(existing.key, { sql: (existing.sql || '') + appendSql });
                  setActiveTab(existing.key);
                  return;
                }
                const newKey = `tab-${Date.now()}`;
                const title = tableName || databaseName || 'Query';
                addTab({ key: newKey, title, sql: tableName ? `SELECT * FROM \`${tableName}\` LIMIT 100;` : '', databaseId, databaseName });
              }}
              onViewTableStructure={(databaseId, databaseName, tableName) => {
                openTableStructure(databaseId, databaseName, tableName);
              }}
              onInsertToEditor={(text) => {
                updateTab(activeTab, { ...activeTabData, sql: (activeTabData?.sql || '') + text });
              }}
            />
          </div>
        </div>

        {/* 编辑器工具栏 - DBeaver 风格，编辑器左侧竖排 */}
        <div style={{
          width: 40, borderRight: '1px solid #d5d5d5', background: '#f9f9f9',
          display: 'flex', flexDirection: 'column', alignItems: 'center',
          padding: '8px 0', gap: 4, flexShrink: 0,
        }}>
          <Tooltip title="执行 (Ctrl+Enter)" placement="right">
            <Button type="text" size="small"
              icon={<PlayCircleOutlined style={{ color: '#52c41a', fontSize: 16 }} />}
              loading={loading} onClick={handleExecute} style={{ padding: '4px 8px' }} />
          </Tooltip>
          <Tooltip title="执行计划" placement="right">
            <Button type="text" size="small" icon={<ExperimentOutlined />}
              onClick={handleExplain} style={{ padding: '4px 8px' }} />
          </Tooltip>
          <Tooltip title="格式化" placement="right">
            <Button type="text" size="small" icon={<FormatPainterOutlined />}
              onClick={() => {
                const editor = editorRef.current;
                if (editor) {
                  const sql = activeTabData?.sql || '';
                  if (sql.trim()) {
                    const formatted = sql
                      .replace(/\s+/g, ' ')
                      .replace(/\b(select|from|where|and|or|join|left|right|inner|outer|on|group|by|order|having|limit|offset|insert|into|values|update|set|delete|create|alter|table|drop|index)\b/gi, '\n$1')
                      .replace(/^\n/, '')
                      .replace(/\n/g, '\n  ')
                      .replace(/\b(select|from|where|and|or|join|on|group|order|having|insert|update|delete|create|alter|drop)\b/gi, (m: string) => m.toUpperCase());
                    updateTab(activeTab, { ...activeTabData, sql: formatted });
                  }
                }
              }} style={{ padding: '4px 8px' }} />
          </Tooltip>
          <Divider style={{ margin: '4px 0', width: 20, minWidth: 20 }} />
          <Tooltip title="复制" placement="right">
            <Button type="text" size="small" icon={<CopyOutlined />}
              onClick={() => {
                navigator.clipboard.writeText(activeTabData?.sql || '');
                message.success('OK');
              }} style={{ padding: '4px 8px' }} />
          </Tooltip>
        </div>

        {/* 主工作区 */}
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, background: '#fff', overflow: 'hidden' }}>
          {/* 编辑器标签栏 */}
          <div style={{
            display: 'flex', alignItems: 'center',
            background: '#ececec', borderBottom: '1px solid #d5d5d5',
            minHeight: 32, userSelect: 'none', flexShrink: 0,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', flex: 1, overflow: 'hidden', paddingLeft: 2 }}>
              {sqlTabs.map((tab: any, idx: number) => {
                const inst = tab.databaseId ? databases.find((d: any) => d.id === tab.databaseId) : null;
                const tip = tab.databaseId ? `${inst?.name || tab.databaseId} / ${tab.databaseName || '-'}` : 'SQL 编辑器';
                return (
                <Tooltip key={tab.key} title={tip} mouseEnterDelay={0.4}>
                <div
                  onClick={() => setActiveTab(tab.key)}
                  onMouseDown={(e) => { if (e.button === 1) { e.preventDefault(); removeTab(tab.key); } }}
                  style={{
                    display: 'flex', alignItems: 'center', gap: 4,
                    padding: '5px 12px 3px', cursor: 'pointer', whiteSpace: 'nowrap',
                    background: tab.key === activeTab ? '#fff' : '#e8e8e8',
                    color: tab.key === activeTab ? '#333' : '#888',
                    borderTop: tab.key === activeTab ? '3px solid #52c41a' : '3px solid transparent',
                    borderRadius: tab.key === activeTab ? '3px 3px 0 0' : '0',
                    marginRight: 1, marginTop: tab.key === activeTab ? 0 : 3,
                    fontSize: 12, fontWeight: tab.key === activeTab ? 500 : 400,
                    transition: 'all 0.15s',
                  }}
                >
                  <FileTextOutlined style={{ fontSize: 12, color: tab.key === activeTab ? '#52c41a' : '#aaa' }} />
                  <span style={{ maxWidth: 130, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {tab.databaseName || `SQL ${idx + 1}`}
                  </span>
                  {sqlTabs.length > 1 && (
                    <CloseOutlined
                      style={{ fontSize: 10, marginLeft: 2, color: '#aaa', padding: 1 }}
                      onClick={(e) => { e.stopPropagation(); removeTab(tab.key); }}
                    />
                  )}
                </div>
                </Tooltip>
                );
              })}
            </div>
          </div>
          {/* SQL 编辑器 */}
          <div style={{ height: editorHeight, minHeight: MIN_EDITOR_HEIGHT, borderBottom: '1px solid #e8e8e8' }}>
            <Editor
              height="100%"
              defaultLanguage="mysql"
              language="mysql"
              value={activeTabData?.sql || ''}
              onChange={(value: string) => updateTab(activeTab, { ...activeTabData, sql: value })}
              onMount={handleEditorMount}
              theme="vs-light"
              options={{
                lineNumbers: 'off',
                minimap: { enabled: false },
                fontSize: 13,
                fontFamily: 'Consolas, Monaco, "Courier New", monospace',
                automaticLayout: true,
                scrollBeyondLastLine: false,
                wordWrap: 'on',
                tabSize: 2,
              }}
            />
          </div>

          {/* 可拖拽分隔条 */}
          <div
            onMouseDown={handleResizeStart}
            style={{
              height: 6, cursor: 'row-resize', background: '#f0f0f0', borderTop: '1px solid #e0e0e0',
              borderBottom: '1px solid #e0e0e0', display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            <div style={{ width: 40, height: 2, background: '#ccc', borderRadius: 2 }} />
          </div>

          {/* 结果面板 */}
          <div ref={resultContainerRef} onScroll={handleResultScroll} style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
            {/* 结果页签头 */}
            <div style={{ display: 'flex', alignItems: 'center', borderBottom: '1px solid #e8e8e8', background: '#f5f5f5', minHeight: 28 }}>
              <Tabs type="editable-card" hideAdd size="small"
                activeKey={activeResultKey}
                onChange={(key) => {
                  setActiveResultKey(key);
                  setActiveResultIdx(key === 'my-sql' ? 0 : Number(key));
                }}
                onEdit={(targetKey) => {
                  if (targetKey === 'my-sql') return;
                  const idx = Number(targetKey);
                  const newResults = currentResults.filter((_, i) => i !== idx);
                  setResults(prev => ({ ...prev, [activeTab]: newResults }));
                  if (newResults.length === 0) { setActiveResultKey('my-sql'); setActiveResultIdx(0); }
                  else if (String(activeResultIdx) === targetKey) { const ni = Math.min(idx, newResults.length - 1); setActiveResultIdx(ni); setActiveResultKey(String(ni)); }
                }}
                items={[
                  {
                    key: 'my-sql',
                    label: <span style={{ fontSize: 11 }}>
                      我的SQL
                    </span>,
                    closable: false,
                    children: null,
                  },
                  ...currentResults.map((r, i) => ({
                    key: String(i),
                    label: <span style={{ fontSize: 11 }}>
                      {r._dataChangeBlocked ? (
                        <span>变更 #{i + 1} <Tag color="warning" style={{ fontSize: 9, lineHeight: '14px', padding: '0 3px', borderRadius: 3, marginLeft: 2 }}>需审批</Tag></span>
                      ) : (
                        `结果 #${i + 1}${r.data?.length ? (r.hasMore ? ` (${r.data.length}+行)` : ` (${r.data.length}行)`) : r.affectRows ? ` (${r.affectRows}行)` : ''}`
                      )}
                    </span>,
                    closable: true,
                    children: null,
                  })),
                ]}
                  tabBarStyle={{ margin: 0, minHeight: 28 }}
                  style={{ flex: 1, overflow: 'hidden' }}
                />
              {currentResults.length > 0 && (
                <Button size="small" type="link" danger style={{ marginRight: 4, flexShrink: 0 }} onClick={() => {
                  setResults(prev => ({ ...prev, [activeTab]: [] })); setActiveResultIdx(0); setActiveResultKey('my-sql');
                }}>清除全部</Button>
              )}
            </div>



            {/* 内容区：我的SQL 面板 / 结果数据 */}

            {activeResultKey === 'my-sql' ? (

              <BottomPanel visible={true} activeTabData={activeTabData} onUseQuery={handleUseFromBottom} editorRef={editorRef} />

            ) : currentResult?._dataChangeBlocked ? (

              /* ========== 数据变更拦截视图 ========== */

              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'auto' }}>

                <Alert

                  type="warning"

                  showIcon

                  icon={<StopOutlined />}

                  message={<span style={{ fontWeight: 600, fontSize: 14 }}>数据变更操作被拦截</span>}

                  description={

                    <div style={{ marginTop: 4 }}>

                      <p style={{ marginBottom: 8 }}>查询编辑器不支持直接执行数据变更操作（INSERT / UPDATE / DELETE / ALTER / DROP / CREATE 等），请通过<strong>数据变更工单</strong>流程执行，确保数据安全与合规。</p>

                    </div>

                  }

                  style={{ margin: '12px 12px 0', borderRadius: 6 }}

                />

                {/* SQL预览 */}

                <div style={{ margin: '12px 12px 0' }}>

                  <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>拟执行的SQL语句：</div>

                  <pre style={{

                    margin: 0, padding: '10px 12px', background: '#fafafa', color: '#333',

                    borderRadius: 6, fontSize: 12, fontFamily: 'Consolas, Monaco, monospace',

                    maxHeight: 160, overflow: 'auto', lineHeight: 1.6,

                    whiteSpace: 'pre-wrap', wordBreak: 'break-all',

                  }}>

                    {currentResult.executedSql}

                  </pre>

                </div>

                {/* 操作按钮 */}

                <div style={{ padding: '16px 12px', display: 'flex', gap: 8 }}>

                  <Button type="primary" icon={<SendOutlined />} size="middle" onClick={() => handleOpenTicketModal(currentResult.executedSql || '')}>申请数据变更</Button>

                  <Button icon={<CopyOutlined />} size="middle" onClick={() => { navigator.clipboard.writeText(currentResult.executedSql || ''); message.success('OK'); }}>复制 SQL</Button>

                </div>

              </div>

            ) : currentResult?.columns?.length ? (
              /* 有列名就是查询结果（含空结果），始终展示表格 */

              (() => {

                const cols: string[] = currentResult?.columns || [];

                const rows: any[] = currentResult?.data || [];

                const rowCount = rows.length;

                const maxColWidth = 50;

                const colWidths = cols.map((c: string) => {

                  let max = c.length;

                  for (const row of rows) {

                    const v = row[c];

                    if (v !== null && v !== undefined) {

                      const s = String(v);

                      if (s.length > max) max = s.length;

                    }

                  }

                  return Math.min(max + 2, maxColWidth);

                });

                const headerLine = cols.map((c: string, ci: number) => c.padEnd(colWidths[ci])).join(' | ');

                return (

                  <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>

                    {/* 结果工具栏 */}

                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '6px 12px', borderBottom: '1px solid #eee', background: '#fafafa' }}>

                      <span style={{ fontSize: 11, color: '#999' }}>{rowCount}{currentResult?.hasMore ? '+' : ''} 行 / {cols.length} 列</span>

                      <Divider type="vertical" style={{ height: 14 }} />

                      <Button size="small" icon={<TableOutlined />} type={resultViewMode === 'grid' ? 'primary' : 'default'} onClick={() => setResultViewMode('grid')} style={{ fontSize: 11 }}>表格</Button>

                      <Button size="small" type={resultViewMode === 'text' ? 'primary' : 'default'} onClick={() => setResultViewMode('text')} style={{ fontSize: 11 }}>文本</Button>

                      <Button size="small" type={resultViewMode === 'record' ? 'primary' : 'default'} onClick={() => setResultViewMode('record')} style={{ fontSize: 11 }}>记录</Button>

                      <div style={{ flex: 1 }} />

                      <Dropdown menu={{ items: [

                        { key: 'csv', label: '导出 CSV', onClick: () => handleExport('csv') },

                        { key: 'xls', label: '导出 Excel', onClick: () => handleExport('xls') },

                        { key: 'json', label: '导出 JSON', onClick: () => handleExport('json') },

                        { key: 'txt', label: '导出 TXT', onClick: () => handleExport('txt') },

                        { key: 'sql', label: '导出 SQL INSERT', onClick: () => handleExport('sql') },

                      ]}}>

                        <Button size="small" icon={<DownloadOutlined />} style={{ fontSize: 11 }}>导出</Button>

                      </Dropdown>

                    </div>

                    {resultViewMode === 'grid' ? (

                      <div style={{ flex: 1, overflow: 'auto' }}>

                        <Table

                          columns={cols.map((col: string) => ({ title: col, dataIndex: col, key: col, ellipsis: true, width: Math.max(120, col.length * 12) }))}

                          dataSource={rows.map((row: any, idx: number) => ({ ...row, _key: idx }))}

                          rowKey="_key"

                          size="small"

                          pagination={false}

                          scroll={{ x: Math.max(800, cols.length * 150), y: Math.max(200, 300) }}

                          bordered

                        />

                      </div>

                    ) : resultViewMode === 'text' ? (

                      <div style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>

                        <pre style={{

                          margin: 0, flex: 1, overflow: 'auto',

                          padding: '6px 10px', whiteSpace: 'pre', fontFamily: 'Consolas, Monaco, "Courier New", monospace',

                          fontSize: 12, lineHeight: 1.5, color: '#333', background: '#fff',

                        }}>

                          {[headerLine, ...rows.map((row: any) =>

                            cols.map((c: string, ci: number) => {

                              const v = row[c];

                              if (v === null || v === undefined) return '\\N'.padEnd(colWidths[ci]);

                              const s = String(v);

                              const display = s.length > maxColWidth ? s.slice(0, maxColWidth - 3) + '...' : s;

                              return display.padEnd(colWidths[ci]);

                            }).join(' | ')

                          )].join('\n')}

                        </pre>

                      </div>

                    ) : (

                      <div style={{ display: 'flex', flexDirection: 'column', height: '100%', overflow: 'hidden' }}>

                        <div style={{ display: 'flex', alignItems: 'center', padding: '4px 12px', borderBottom: '1px solid #eee', background: '#fafafa' }}>

                          <Button size="small" type="text" icon={<UpOutlined />} onClick={() => setTextViewRow(r => Math.max(0, r - 1))} disabled={textViewRow <= 0} />

                          <Button size="small" type="text" icon={<DownOutlined />} onClick={() => setTextViewRow(r => Math.min(rowCount - 1, r + 1))} disabled={textViewRow >= rowCount - 1} />

                          <span style={{ marginLeft: 12, fontSize: 12, fontWeight: 500 }}>行 #{textViewRow + 1}</span>

                          <span style={{ marginLeft: 'auto', fontSize: 10, color: '#bbb' }}>{rowCount} 行</span>

                        </div>

                        <div style={{ flex: 1, overflow: 'auto', padding: '8px 0' }}>

                          {(() => {

                            const currentRowData = rows[textViewRow];

                            if (!currentRowData) return <div style={{ padding: 16, color: '#999' }}>暂无数据</div>;

                            return cols.map((col: string, ci: number) => {

                              const val = currentRowData[col];

                              const isNull = val === null || val === undefined;

                              let typeTag = '';

                              let tagColor = '#999';

                              let tagBg = '#f0f0f0';

                              if (isNull) { typeTag = ''; tagColor = '#999'; tagBg = '#fafafa'; }

                              else if (typeof val === 'number') { typeTag = '123'; tagColor = '#1890ff'; tagBg = '#e6f4ff'; }

                              else if (typeof val === 'string') { typeTag = 'AZ'; tagColor = '#d48806'; tagBg = '#fff7e6'; }

                              else if (typeof val === 'boolean') { typeTag = 'BOL'; tagColor = '#cf1322'; tagBg = '#fff1f0'; }

                              return (

                                <div key={ci} style={{

                                  display: 'flex', alignItems: 'center', padding: '6px 12px',

                                  fontSize: 12, background: ci % 2 === 0 ? '#fff' : '#fafafa',

                                }}>

                                  <span style={{ color: tagColor, background: tagBg, fontSize: 9, padding: '1px 4px', borderRadius: 3, marginRight: 8 }}>{typeTag}</span>

                                  <span style={{ color: '#666', minWidth: 150, fontWeight: 500 }}>{col}</span>

                                  <span style={{ flex: 1, color: isNull ? '#bbb' : '#333', fontFamily: 'Consolas, monospace' }}>{isNull ? '\\N' : String(val)}</span>

                                </div>

                              );

                            });

                          })()}

                        </div>

                      </div>

                    )}

                    {currentResult?.hasMore && (

                      <div onClick={!paginationLoading ? loadMoreRows : undefined} style={{

                        textAlign: 'center', padding: '8px 0', cursor: paginationLoading ? 'wait' : 'pointer',

                        color: paginationLoading ? '#999' : '#1677ff', fontSize: 12, borderTop: '1px solid #f0f0f0', userSelect: 'none',

                      }}>

                        {paginationLoading ? '加载中...' : `已加载 ${(currentResult.data || []).length} 行，点击或滚动加载更多`}

                      </div>

                    )}

                  </div>

                );

              })()

            ) : currentResult ? (

              <div style={{ padding: 16 }}>
                {currentResult.errorMessage && (
                  <Alert type="error" showIcon message="SQL 执行错误" description={currentResult.errorMessage} style={{ marginBottom: 12 }} />
                )}
                <ExecutionStats result={currentResult} />

              </div>

            ) : (

              <div style={{ textAlign: 'center', paddingTop: 40, color: '#999', fontSize: 13 }}>无结果数据</div>

            )}

          </div>

        </div>

      </div>

      {/* ========== 执行计划 Drawer ========== */}

      <Drawer

        title={<span><ExperimentOutlined /> 执行计划 (EXPLAIN)</span>}

        placement="right" width={700} open={explainDrawerOpen}

        onClose={() => setExplainDrawerOpen(false)}

        extra={<Button size="small" onClick={() => { setExplainResult(null); setExplainDrawerOpen(false); }}>关闭</Button>}

      >

        {explainResult ? (<>

          <div style={{ marginBottom: 8, fontSize: 12, color: '#999' }}>

            耗时: {explainResult.executionTime}ms | 返回: {explainResult.data?.length || 0} 行          </div>

          {explainResult.data?.map((row: any, idx: number) => {

            const type = row.type || ''; const isFullScan = type === 'ALL';

            return (

              <div key={idx} style={{ marginBottom: 6, padding: 8, borderLeft: `3px solid ${isFullScan ? '#cf1322' : '#52c41a'}`, background: isFullScan ? '#fff2f0' : '#f6ffed', borderRadius: '0 4px 4px 0' }}>

                <Space wrap>

                  <Tag color={isFullScan ? 'red' : 'green'}>type: {type || 'N/A'}</Tag>

                  {row.possible_keys && <Tag color="blue">索引: {row.possible_keys}</Tag>}

                  {row.key && <Tag color="cyan">使用: {row.key}</Tag>}

                  <Tag>扫描行数: {row.rows || 0}</Tag>

                  {row.Extra && <Tag color={row.Extra.includes('filesort') ? 'orange' : row.Extra.includes('temporary') ? 'red' : 'default'}>Extra: {row.Extra}</Tag>}

                </Space>

                {isFullScan && <Alert message="全表扫描！建议添加索引" type="warning" showIcon style={{ marginTop: 4 }} />}

              </div>

            );

          })}

          <Table columns={explainResult.columns?.map((col: string) => ({ title: col, dataIndex: col, key: col, ellipsis: true, width: 120 })) || []}

            dataSource={explainResult.data?.map((row: any, idx: number) => ({ ...row, _key: idx }))} rowKey="_key" scroll={{ x: 600 }} size="small" pagination={false}

          />

        </>) : <div style={{ textAlign: 'center', padding: 40, color: '#bbb' }}>暂无执行计划数据</div>}

      </Drawer>



      {/* ========== SQL 审核 Drawer ========== */}

      <Drawer

        title={<span><SafetyOutlined /> SQL审核与优化建议</span>}

        placement="right" width={600} open={auditDrawerOpen} onClose={() => setAuditDrawerOpen(false)}

      >

        {auditResult ? (<>

          <div style={{ textAlign: 'center', padding: '16px 0', marginBottom: 16, background: '#fafafa', borderRadius: 8 }}>

            <div style={{ fontSize: 42, fontWeight: 'bold', color: auditResult.optimizationScore >= 80 ? '#52c41a' : auditResult.optimizationScore >= 60 ? '#faad14' : '#cf1322' }}>

              {auditResult.optimizationScore}

            </div>

            <div style={{ color: '#999', marginTop: 4 }}>优化评分</div>

            <Tag color={

              auditResult.optimizationLevel === 'excellent' ? 'green' : auditResult.optimizationLevel === 'good' ? 'blue' :

              auditResult.optimizationLevel === 'fair' ? 'orange' : 'red'

            } style={{ marginTop: 8 }}>

              {auditResult.optimizationLevel === 'excellent' ? '优秀' : auditResult.optimizationLevel === 'good' ? '良好' :

               auditResult.optimizationLevel === 'fair' ? '一般' : auditResult.optimizationLevel === 'poor' ? '较差' : '严重'}

            </Tag>

          </div>

          <Alert message={`风险等级: ${auditResult.riskLevel === 'high' ? '高' : auditResult.riskLevel === 'medium' ? '中' : auditResult.riskLevel === 'low' ? '低' : '无'}`}

            type={auditResult.riskLevel === 'high' ? 'error' : auditResult.riskLevel === 'medium' ? 'warning' : 'success'}

            showIcon style={{ marginBottom: 16 }}

          />

          {auditResult.issues?.map((issue: any, idx: number) => (

            <div key={idx} style={{ marginBottom: 8, padding: '8px 12px', borderLeft: `3px solid ${issue.level === 'error' ? '#cf1322' : issue.level === 'warning' ? '#faad14' : '#1677ff'}`, borderRadius: '0 4px 4px 0', background: '#fff' }}>

              <Space>

                {issue.level === 'error' ? <WarningOutlined style={{ color: '#cf1322' }} /> :

                 issue.level === 'warning' ? <WarningOutlined style={{ color: '#faad14' }} /> : <CheckCircleOutlined style={{ color: '#1677ff' }} />}

                <Tag color={issue.level === 'error' ? 'red' : issue.level === 'warning' ? 'orange' : 'blue'}>{issue.code}</Tag>

              </Space>

              <div style={{ marginTop: 6, fontSize: 12 }}>{issue.message}</div>

              {issue.suggestion && (

                <div style={{ marginTop: 4, color: '#666', fontSize: 11, background: '#f5f5f5', padding: '4px 8px', borderRadius: 4 }}>

                  建议: {issue.suggestion}

                </div>

              )}

            </div>

          ))}

          {(!auditResult.issues || auditResult.issues.length === 0) && <Alert type="success" message="SQL审核通过，未发现问题" showIcon />}

        </>) : <div style={{ textAlign: 'center', padding: 40, color: '#bbb' }}>暂无审核结果</div>}

      </Drawer>



      {/* ========== 表结构查看 Drawer ========== */}

      <Drawer

        title={<span><ProfileOutlined /> 表结构 - {tableStructureInfo?.tableName}</span>}

        placement="right" width={720} open={tableStructureVisible}

        onClose={() => { setTableStructureVisible(false); setTableStructureInfo(null); }}

      >

        {tableStructureInfo ? (

          <>

            <div style={{ marginBottom: 8, fontSize: 12, color: '#999' }}>

              共 {tableStructureInfo.columns.length} 个字段            </div>

            <Table

              dataSource={tableStructureInfo.columns.map((col, idx) => ({ ...col, _key: idx }))}

              rowKey="_key" size="small" bordered

              pagination={false}

              scroll={{ x: 'max-content' }}

              columns={[

                { title: '#', dataIndex: '_key', key: '_key', width: 40, render: (_: any, __: any, idx: number) => idx + 1 },

                {

                  title: '列名', dataIndex: 'name', key: 'name', width: 140,

                  render: (v: string) => (

                    <Space size={4}>

                      <DatabaseOutlined style={{ color: '#1677ff', fontSize: 10 }} />

                      <span style={{ fontFamily: 'monospace', fontWeight: v.includes('id') || v === v.toUpperCase() ? 600 : 400 }}>{v}</span>

                    </Space>

                  ),

                },

                {

                  title: '数据类型', dataIndex: 'type', key: 'type', width: 120,

                  render: (v: string) => <Tag style={{ fontFamily: 'monospace', fontSize: 11 }}>{v}</Tag>,

                },

                { title: '键', dataIndex: 'key', key: 'key', width: 50,

                  render: (v: string) => v && <Tag color={v === 'PRI' ? 'red' : 'purple'} style={{ fontSize: 10 }}>{v}</Tag>,

                },

                { title: '默认值', dataIndex: 'default', key: 'default', width: 80, ellipsis: true,

                  render: (v: string) => v ? <span style={{ color: '#999', fontSize: 11, fontFamily: 'monospace' }}>{v}</span> : '-',

                },

                { title: '注释', dataIndex: 'comment', key: 'comment',

                  ellipsis: true,

                  render: (v: string) => <span style={{ color: '#999', fontSize: 11 }}>{v}</span>,

                },

              ]}

              rowClassName={(_, idx) => idx % 2 === 0 ? undefined : 'dbeaver-row-alt'}

            />

            {tableStructureInfo.indexes && tableStructureInfo.indexes.length > 0 && (
              <>
                <Divider orientation="left" plain style={{ fontSize: 13 }}>Indexes ({tableStructureInfo.indexes.length})</Divider>
                <Table
                  dataSource={tableStructureInfo.indexes.map((ix: any, i: number) => ({ ...ix, _key: i }))}
                  rowKey="_key" size="small" bordered pagination={false}
                  columns={[
                    { title: 'Name', dataIndex: 'name', key: 'name', width: 180 },
                    { title: 'Columns', dataIndex: 'columns', key: 'columns', render: (v: any) => Array.isArray(v) ? v.join(', ') : v },
                    { title: 'Unique', dataIndex: 'unique', key: 'unique', width: 60, render: (v: string) => v === 'Y' ? <Tag color="orange" style={{ fontSize: 10 }}>UNIQUE</Tag> : null },
                    { title: 'Type', dataIndex: 'type', key: 'type', width: 80 },
                  ]}
                />
              </>
            )}
            {tableStructureInfo.ddl && (
              <>
                <Divider orientation="left" plain style={{ fontSize: 13 }}>DDL</Divider>
                <pre style={{ background: '#f5f5f5', padding: 12, borderRadius: 4, fontSize: 12, fontFamily: 'Consolas, monospace', whiteSpace: 'pre-wrap', maxHeight: 300, overflow: 'auto' }}>
                  {tableStructureInfo.ddl}
                </pre>
              </>
            )}

          </>

        ) : <Spin tip="Loading..." style={{ display: 'block', textAlign: 'center', paddingTop: 80 }} />}

      </Drawer>



      {/* ========== 快速创建数据变更工单 Modal ========== */}

      <Modal

        title={<span><SendOutlined style={{ marginRight: 8 }} />申请数据变更</span>}

        open={ticketModalVisible}

        onCancel={() => setTicketModalVisible(false)}

        onOk={handleSubmitTicket}

        okText="提交工单"

        cancelText="取消"

        confirmLoading={savingTicket}

        width={700}

        destroyOnClose

      >

        <Form form={ticketForm} layout="vertical" style={{ marginTop: 8 }}>

          <Form.Item

            name="title"

            label="工单标题"

            rules={[{ required: true, message: '请输入工单标题' }]}

          >

            <Input placeholder="例如：批量更新用户状态" />

          </Form.Item>



          {/* 展示预填信息 */}

          <Row gutter={16}>

            <Col span={8}>

              <div style={{ marginBottom: 16 }}>

                <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>实例</div>

                <Input value={currentDatabase?.name || activeTabData?.databaseId || ''} disabled style={{ fontSize: 12 }} />

              </div>

            </Col>

            <Col span={8}>

              <div style={{ marginBottom: 16 }}>

                <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>Schema</div>

                <Input value={activeTabData?.databaseName || ''} disabled style={{ fontSize: 12 }} />

              </div>

            </Col>

            <Col span={8}>

              <div style={{ marginBottom: 16 }}>

                <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>变更类型</div>

                <Tag color="orange" style={{ fontSize: 13, padding: '2px 12px', lineHeight: '22px' }}>{ticketModalChangeType}</Tag>

              </div>

            </Col>

          </Row>



          <Form.Item name="priority" label="优先级" initialValue="normal">

            <Radio.Group>

              <Radio.Button value="low">低</Radio.Button>

              <Radio.Button value="normal">普通</Radio.Button>

              <Radio.Button value="high">高</Radio.Button>

              <Radio.Button value="urgent">紧急</Radio.Button>

            </Radio.Group>

          </Form.Item>



          <div style={{ marginBottom: 4 }}>

            <div style={{ fontSize: 12, color: '#888', marginBottom: 4 }}>SQL语句</div>

            <pre style={{

              margin: 0, padding: '10px 12px', background: '#fafafa', color: '#333',

              borderRadius: 6, fontSize: 12, fontFamily: 'Consolas, Monaco, monospace',

              maxHeight: 180, overflow: 'auto', lineHeight: 1.6,

              whiteSpace: 'pre-wrap', wordBreak: 'break-all',

              border: '1px solid #333',

            }}>

              {ticketModalSql}

            </pre>

          </div>

        </Form>

      </Modal>

    </div>

  );

};



export default SqlEditor;

