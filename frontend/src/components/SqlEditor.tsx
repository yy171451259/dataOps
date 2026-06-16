import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';

import { useNavigate } from 'react-router-dom';

import Editor, { OnMount } from '@monaco-editor/react';

import {

  Button, Table, message, Select, Tabs, Dropdown, Tooltip, Tag,

  Drawer, Modal, Popconfirm, Divider, Empty, Space, Input, Rate,

  Form, Alert, Radio, Row, Col,

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

}



const BottomPanel: React.FC<BottomPanelProps> = ({ visible, activeTabData, onUseQuery }) => {

  const [panelTab, setPanelTab] = useState<string>('favorites');

  const [searchText, setSearchText] = useState('');

  const [editModalOpen, setEditModalOpen] = useState(false);

  const [editingQuery, setEditingQuery] = useState<any>(null);

  const [editForm, setEditForm] = useState({ name: '', sql: '' });



  const {

    savedQueries, addSavedQuery, updateSavedQuery, deleteSavedQuery,

    toggleFavorite, executionHistory, clearExecutionHistory,

  } = useAppStore();



  const dbFilteredQueries = useMemo(() => {

    if (!activeTabData?.databaseId) return savedQueries;

    return savedQueries.filter(q =>

      (!q.databaseId || q.databaseId === activeTabData.databaseId) &&

      (!q.databaseName || q.databaseName === activeTabData.databaseName)

    );

  }, [savedQueries, activeTabData?.databaseId, activeTabData?.databaseName]);



  const dbFilteredHistory = useMemo(() => {

    if (!activeTabData?.databaseId) return executionHistory;

    return executionHistory.filter(h =>

      h.databaseId === activeTabData.databaseId &&

      h.databaseName === activeTabData.databaseName

    );

  }, [executionHistory, activeTabData?.databaseId, activeTabData?.databaseName]);



  const filteredSavedQueries = useMemo(() => {

    if (!searchText.trim()) return dbFilteredQueries;

    const kw = searchText.toLowerCase();

    return dbFilteredQueries.filter(q =>

      q.name.toLowerCase().includes(kw) || q.sql.toLowerCase().includes(kw)

    );

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



  const getAvgDuration = (totalDuration: number, count: number): number =>

    count > 0 ? Math.round(totalDuration / count) : 0;



  const handleAddFavorite = () => {

    if (!activeTabData?.sql?.trim()) { message.warning('OK'); return; }

    const existing = savedQueries.find(q => q.sql.trim() === activeTabData.sql.trim());

    if (existing) {

      if (existing.isFavorite) {

        toggleFavorite(existing.id);

        message.success('OK');

  };

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
      if (list.length > 0 && (!activeTabData?.databaseId || !currentDbExists)) {
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

      const res = await instanceApi.executeQuery(activeTabData.databaseId, { sql: sqlToRun, maxRows: 1000 });

      const executionTime = Date.now() - startTime;

      const data = res.data.data;

      const queryResult: QueryResult = {

        columns: data?.columns || [],

        data: data?.rows || [],

        success: true,

        executionTime,

        executedSql: sqlToRun,

      };

      setResults(prev => ({ ...prev, [activeTab]: [...(prev[activeTab] || []), queryResult] }));

      const newIdx = (results[activeTab]?.length || 0);

      setActiveResultIdx(newIdx);

      setActiveResultKey(String(newIdx));

      const { addExecutionRecord } = useAppStore.getState();

      addExecutionRecord({ sql: sqlToRun, databaseId: activeTabData.databaseId!, databaseName: activeTabData.databaseName, executionTime, success: true });

    } catch (err: any) {

      message.error(err.response?.data?.message || err.message || '执行失败');

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

  const [tableStructureInfo, setTableStructureInfo] = useState<{ tableName: string; columns: any[] } | null>(null);

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
      {/* SQL Tab 页签 */}
      <div style={{ display: 'flex', alignItems: 'center', background: '#e8e8e8', borderBottom: '1px solid #d9d9d9', paddingLeft: 4, minHeight: 30 }}>
        {sqlTabs.map((tab: any) => (
          <div key={tab.key} onClick={() => setActiveTab(tab.key)} style={{
            display: 'flex', alignItems: 'center', padding: '4px 12px', cursor: 'pointer',
            background: tab.key === activeTab ? '#fff' : 'transparent',
            borderLeft: '1px solid transparent', borderRight: '1px solid transparent',
            borderTop: tab.key === activeTab ? '2px solid #1677ff' : '2px solid transparent',
            fontSize: 12, marginRight: 2, marginTop: 2,
            borderBottom: tab.key === activeTab ? '1px solid #fff' : '1px solid #d9d9d9',
          }}>
            <DatabaseOutlined style={{ fontSize: 11, marginRight: 4, color: '#1677ff' }} />
            <span style={{ maxWidth: 120, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {tab.databaseName || tab.databaseId || 'SQL'}
            </span>
            {sqlTabs.length > 1 && (
              <CloseOutlined
                style={{ fontSize: 10, marginLeft: 6, color: '#999', opacity: 0.6 }}
                onClick={(e) => { e.stopPropagation(); removeTab(tab.key); }}
              />
            )}
          </div>
        ))}
        <Button type="text" size="small" icon={<PlusOutlined />} onClick={() => addTab()} style={{ marginLeft: 4, fontSize: 11, padding: '2px 8px' }}>新建</Button>
      </div>

      <div style={{ display: 'flex', flex: 1, overflow: 'hidden' }}>
        {/* 对象浏览器侧边栏 */}
        <ObjectBrowser
          databaseId={activeTabData?.databaseId}
          databaseName={activeTabData?.databaseName}
          schemas={schemaNames}
          loadingSchemas={loadingSchemaNames}
        />

        {/* 主工作区 */}
        <div style={{ display: 'flex', flexDirection: 'column', flex: 1, background: '#fff', overflow: 'hidden' }}>
          {/* 工具栏 */}
          <div style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '6px 10px', borderBottom: '1px solid #e8e8e8', background: '#fafafa' }}>
            <Select
              value={activeTabData?.databaseId}
              style={{ width: 180 }}
              size="small"
              onChange={(val: string) => {
                const db = databases.find((d: any) => d.id === val);
                updateTab(activeTab, { databaseId: val, databaseName: db?.name });
                setActiveResultKey('my-sql');
                setResults(prev => ({ ...prev, [activeTab]: [] }));
              }}
              options={databases.map((db: any) => ({ label: db.name, value: db.id }))}
              placeholder="选择数据库实例"
            />
            {schemaNames.length > 0 && activeTabData?.databaseName && (
              <Select
                value={activeTabData?.databaseName}
                style={{ width: 140 }}
                size="small"
                onChange={(val: string) => updateTab(activeTab, { databaseId: activeTabData.databaseId, databaseName: val })}
                options={[{ label: activeTabData?.databaseName, value: activeTabData?.databaseName }, ...schemaNames.filter(n => n !== activeTabData?.databaseName).map((n: string) => ({ label: n, value: n }))]}
                placeholder="选择 Schema"
              />
            )}
            <Divider type="vertical" style={{ height: 16 }} />
            <Button type="primary" size="small" icon={<PlayCircleOutlined />} loading={loading} onClick={handleExecute}>执行 (Ctrl+Enter)</Button>
            <Button size="small" icon={<ExperimentOutlined />} onClick={handleExplain}>执行计划</Button>
            <Button size="small" icon={<SafetyOutlined />} onClick={handleAudit}>SQL 审核</Button>
            <Button size="small" icon={<CopyOutlined />} onClick={handleCopySql}>复制</Button>
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
              theme="vs-dark"
              options={{
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
              <div onClick={() => { setActiveResultKey('my-sql'); setActiveResultIdx(0); }}
                style={{
                  padding: '4px 12px', fontSize: 12, cursor: 'pointer', fontWeight: 500,
                  borderBottom: activeResultKey === 'my-sql' ? '2px solid #1677ff' : '2px solid transparent',
                  color: activeResultKey === 'my-sql' ? '#1677ff' : '#595959',
                  background: activeResultKey === 'my-sql' ? '#fff' : 'transparent',
                  whiteSpace: 'nowrap', borderRight: '1px solid #e8e8e8',
                  userSelect: 'none', lineHeight: '22px',
                }}
              >
                我的SQL {savedQueries.length > 0 && <Tag color="blue" style={{ marginLeft: 4, fontSize: 10, lineHeight: '14px' }}>{savedQueries.length}</Tag>}
              </div>
              {currentResults.length > 0 && (
                <Tabs type="editable-card" hideAdd size="small"
                  activeKey={activeResultKey === 'my-sql' ? undefined : activeResultKey}
                  onChange={(key) => { setActiveResultKey(key); setActiveResultIdx(Number(key)); }}
                  onEdit={(targetKey) => {
                    const idx = Number(targetKey);
                    const newResults = currentResults.filter((_, i) => i !== idx);
                    setResults(prev => ({ ...prev, [activeTab]: newResults }));
                    if (newResults.length === 0) { setActiveResultKey('my-sql'); setActiveResultIdx(0); }
                    else if (activeResultIdx >= newResults.length) { const ni = Math.max(0, newResults.length - 1); setActiveResultIdx(ni); setActiveResultKey(String(ni)); }
                    else if (activeResultIdx === idx) { const ni = Math.min(idx, newResults.length - 1); setActiveResultIdx(ni); setActiveResultKey(String(ni)); }
                  }}
                  items={currentResults.map((r, i) => ({
                    key: String(i),
                    label: <span style={{ fontSize: 11 }}>
                      {r._dataChangeBlocked ? (
                        <span>变更 #{i + 1} <Tag color="warning" style={{ fontSize: 9, lineHeight: '14px', padding: '0 3px', borderRadius: 3, marginLeft: 2 }}>需审批</Tag></span>
                      ) : (
                        `结果 #${i + 1}${r.data?.length ? (r.hasMore ? ` (${r.data.length}+行)` : ` (${r.data.length}行)`) : r.affectRows ? ` (${r.affectRows}行)` : ''}`
                      )}
                    </span>,
                    closable: true,
                  }))}
                  tabBarStyle={{ margin: 0, minHeight: 28 }}
                  style={{ flex: 1, overflow: 'hidden' }}
                />
              )}
              {currentResults.length > 0 && (
                <Button size="small" type="link" danger style={{ marginRight: 4, flexShrink: 0 }} onClick={() => {
                  setResults(prev => ({ ...prev, [activeTab]: [] })); setActiveResultIdx(0); setActiveResultKey('my-sql');
                }}>清除全部</Button>
              )}
            </div>



            {/* 内容区：我的SQL 面板 / 结果数据 */}

            {activeResultKey === 'my-sql' ? (

              <BottomPanel visible={true} activeTabData={activeTabData} onUseQuery={handleUseFromBottom} />

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

                    margin: 0, padding: '10px 12px', background: '#1e1e1e', color: '#d4d4d4',

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

            ) : currentResult?.data?.length ? (

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

                { title: '非空', key: 'notNull', width: 55,

                  render: (_: any, rec: any) => rec.key === 'PRI' ? <Tag color="red" style={{ fontSize: 10 }}>PK</Tag> : null,

                },

                { title: '键', dataIndex: 'key', key: 'key', width: 50,

                  render: (v: string) => v && <span style={{ color: '#722ed1', fontSize: 11 }}>{v}</span>,

                },

                { title: '默认值', dataIndex: 'default', key: 'default', width: 80, ellipsis: true },

                { title: '注释', dataIndex: 'comment', key: 'comment',

                  ellipsis: true,

                  render: (v: string) => <span style={{ color: '#999', fontSize: 11 }}>{v}</span>,

                },

              ]}

              rowClassName={(_, idx) => idx % 2 === 0 ? undefined : 'dbeaver-row-alt'}

            />

          </>

        ) : null}

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

              margin: 0, padding: '10px 12px', background: '#1e1e1e', color: '#d4d4d4',

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

