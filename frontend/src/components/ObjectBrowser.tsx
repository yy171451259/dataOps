import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Input, Tree, Spin, Tag, Tooltip } from 'antd';
import {
  SearchOutlined, DatabaseOutlined, TableOutlined,
  EyeOutlined, CodeOutlined, ThunderboltOutlined,
  ClockCircleOutlined, FieldStringOutlined,
  CloudServerOutlined, LoadingOutlined,
  PlusOutlined, ProfileOutlined,
  CommentOutlined, SafetyCertificateOutlined,
  KeyOutlined, LinkOutlined,
} from '@ant-design/icons';
import { instanceApi } from '../utils/api';
import type { DataNode, TreeDataNode } from 'antd/es/tree';

/* ---------- Types ---------- */
interface DbInstance {
  id: string;
  name: string;
  host?: string;
  port?: number | string;
  type?: string;
}

interface ObjectBrowserProps {
  databaseId?: string;
  databaseName?: string;
  onInstanceSelect?: (instanceId: string) => void;
  onDatabaseSelect?: (dbName: string) => void;
  onTableSelect?: (tableName: string) => void;
  onInsertToEditor?: (text: string) => void;
  onNewTab?: (databaseId: string, databaseName: string, tableName?: string) => void;
  onViewTableStructure?: (databaseId: string, databaseName: string, tableName: string) => void;
}

type CategoryKey = 'tables' | 'views' | 'procedures' | 'functions' | 'triggers' | 'events';

const CAT_META: Record<CategoryKey, { label: string; icon: React.ReactNode; color: string }> = {
  tables:     { label: '表',        icon: <TableOutlined />,         color: '#1677ff' },
  views:      { label: '视图',      icon: <EyeOutlined />,           color: '#52c41a' },
  procedures: { label: '存储过程',  icon: <CodeOutlined />,          color: '#faad14' },
  functions:  { label: '函数',      icon: <CodeOutlined />,          color: '#d48806' },
  triggers:   { label: '触发器',    icon: <ThunderboltOutlined />,   color: '#eb2f96' },
  events:     { label: '事件',      icon: <ClockCircleOutlined />,   color: '#13c2c2' },
};

const ALL_CATS: CategoryKey[] = ['tables', 'views', 'procedures', 'functions', 'triggers', 'events'];

type TableSchema = { name: string; type?: string; columns: Array<{ name: string; type: string; key: string; comment?: string }> };

/** 综合浏览器Schema返回结构 */
type BrowserSchema = {
  tables: TableSchema[];
  views: TableSchema[];
  procedures: string[];
  functions: string[];
  triggers: string[];
  events: string[];
};

/** 表详细信息缓存 */
type TableDetail = {
  indexes: any[];
  foreignKeys: any[];
  constraints: any[];
  triggers: string[];
  ddl: string | null;
};

// 用 React state 做缓存，确保更新触发重渲染
let dbCacheState: Record<string, string[]> = {};
let schemaCacheState: Record<string, BrowserSchema> = {};
let tableDetailCache: Record<string, TableDetail> = {};
let cacheVersion = 0;
// 防重复请求
const pendingRequests = new Set<string>();

/* ---------- Component ---------- */

/** 右键菜单 */
const MenuItem: React.FC<{
  icon: React.ReactNode;
  label: string;
  onClick: () => void;
}> = ({ icon, label, onClick }) => (
  <div
    style={{
      padding: '7px 16px', cursor: 'pointer',
      display: 'flex', alignItems: 'center', gap: 8,
      transition: 'background 0.15s',
    }}
    onMouseEnter={e => (e.currentTarget.style.background = '#f0f5ff')}
    onMouseLeave={e => (e.currentTarget.style.background = 'transparent')}
    onClick={(e) => { e.stopPropagation(); onClick(); }}
  >
    {icon}
    <span>{label}</span>
  </div>
);

const ObjectBrowser: React.FC<ObjectBrowserProps> = ({
  databaseId, databaseName,
  onInstanceSelect, onDatabaseSelect, onTableSelect, onInsertToEditor,
  onNewTab, onViewTableStructure,
}) => {
  const [instances, setInstances] = useState<DbInstance[]>([]);
  const [loadingInsts, setLoadingInsts] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [loadingKeys, setLoadingKeys] = useState<React.Key[]>([]);
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  const [selectedKeys, setSelectedKeys] = useState<React.Key[]>([]);
  // 用版本号强制 useMemo 在缓存更新后重算
  const [, setCacheV] = useState(0);
  
  // 右键菜单状态
  const [contextMenu, setContextMenu] = useState<{ visible: boolean; x: number; y: number; instId: string; schemaName: string; tableName?: string } | null>(null);

  // 点击任意位置关闭右键菜单
  useEffect(() => {
    const close = () => setContextMenu(null);
    document.addEventListener('click', close);
    return () => document.removeEventListener('click', close);
  }, []);

  /* fetch instances */
  useEffect(() => {
    setLoadingInsts(true);
    instanceApi.list().then(res => {
      setInstances((res.data?.data || []) as DbInstance[]);
    }).catch(() => setInstances([])).finally(() => setLoadingInsts(false));
  }, []);

  /* auto-expand current context (skip when searching) */
  useEffect(() => {
    if (!databaseId || !instances.length || searchText.trim()) return;
    setExpandedKeys(prev => prev.includes(`inst-${databaseId}`) ? prev : [...prev, `inst-${databaseId}`]);
  }, [databaseId, searchText]);

  useEffect(() => {
    if (!databaseId || !databaseName || !instances.length) return;
    setSelectedKeys([`schema-${databaseId}-${databaseName}`]);
  }, [databaseId, databaseName, instances]);

  /** build static top-level tree nodes (instances) — NEVER filter by search (ancestors stay visible) */
  const treeData: DataNode[] = useMemo(() => {
    return instances.map(inst => ({
      key: `inst-${inst.id}`,
      title: (
        <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
          <CloudServerOutlined style={{ color: '#1677ff', fontSize: 12 }} />
          <span style={{ fontWeight: 500, fontSize: 12 }}>{inst.name}</span>
          {inst.host && <span style={{ color: '#999', fontSize: 11 }}>{inst.host}{inst.port ? `:${inst.port}` : ''}</span>}
          {inst.type && <Tag style={{ fontSize: 9, lineHeight: '16px', padding: '0 4px', borderRadius: 3, marginLeft: 2 }}>{inst.type}</Tag>}
        </span>
      ),
      isLeaf: false,
    }));
  }, [instances]);

  /** extract instId & schemaName from a schema- key */
  const parseSchemaKey = (key: string): { instId: string; schemaName: string } | null => {
    // key format: schema-{instId}-{rest is schemaName}
    const rest = key.substring(7); // remove 'schema-'
    const dashIdx = rest.indexOf('-');
    if (dashIdx < 0) return null;
    return { instId: rest.substring(0, dashIdx), schemaName: rest.substring(dashIdx + 1) };
  };

  /** extract instId & schemaName & tableName from a tbl- key */
  const parseTblKey = (key: string): { instId: string; schemaName: string; tableName: string } | null => {
    // key format: tbl-{instId}-{schemaName}-{tableName}
    const rest = key.substring(4); // remove 'tbl-'
    const dash1 = rest.indexOf('-');
    if (dash1 < 0) return null;
    const instId = rest.substring(0, dash1);
    const remainder = rest.substring(dash1 + 1);
    const dash2 = remainder.indexOf('-');
    if (dash2 < 0) return null;
    return { instId, schemaName: remainder.substring(0, dash2), tableName: remainder.substring(dash2 + 1) };
  };

  /** lazy-load children for a node */
  const onLoadData = useCallback(({ key }: TreeDataNode): Promise<void> => {
    const k = String(key);

    // 防重复：缓存已有 & 正在请求
    if (pendingRequests.has(k)) return Promise.resolve();

    // ---- Level 1 → Level 2: instance → databases ----
    if (k.startsWith('inst-')) {
      const instId = k.replace('inst-', '');
      if (dbCacheState[instId]) return Promise.resolve();
      pendingRequests.add(k);
      setLoadingKeys(prev => [...prev, k]);
      return instanceApi.getSchemas(instId)
        .then(res => {
          dbCacheState[instId] = res.data.data || [];
          cacheVersion++;
          setCacheV(cacheVersion);
        })
        .catch(() => { dbCacheState[instId] = []; cacheVersion++; setCacheV(cacheVersion); })
        .finally(() => { pendingRequests.delete(k); setLoadingKeys(prev => prev.filter(x => x !== k)); });
    }

    // ---- Level 2 → Level 3: database → categories + tables ----
    if (k.startsWith('schema-')) {
      const parsed = parseSchemaKey(k);
      if (!parsed) return Promise.resolve();
      const { instId, schemaName } = parsed;
      if (schemaCacheState[k]) return Promise.resolve();
      pendingRequests.add(k);
      setLoadingKeys(prev => [...prev, k]);
      return instanceApi.getBrowserSchema(instId, schemaName)
        .then(res => {
          schemaCacheState[k] = res.data.data || { tables: [], views: [], procedures: [], functions: [], triggers: [], events: [] };
          cacheVersion++;
          setCacheV(cacheVersion);
        })
        .catch(() => { schemaCacheState[k] = []; cacheVersion++; setCacheV(cacheVersion); })
        .finally(() => { pendingRequests.delete(k); setLoadingKeys(prev => prev.filter(x => x !== k)); });
    }

    // tbl- lazy-load 由 handleExpand 通过 onExpand 事件触发

    return Promise.resolve();
  }, []);

  /** recurse tree and inject cached children — support search filtering */
  const injectChildren = (nodes: DataNode[]): DataNode[] => {
    const s = searchText.trim().toLowerCase();
    return nodes.map(node => {
      const k = String(node.key);
      let children: DataNode[] | undefined;

      // Instance → databases (never filter by search — ancestors stay visible)
      if (k.startsWith('inst-') && dbCacheState[k.replace('inst-', '')]) {
        const instId = k.replace('inst-', '');
        const dbs = dbCacheState[instId] || [];
        children = dbs.map(schemaName => ({
          key: `schema-${instId}-${schemaName}`,
          title: (
            <span
              style={{ display: 'flex', alignItems: 'center', gap: 5 }}
              onContextMenu={(e) => { e.preventDefault(); e.stopPropagation(); setContextMenu({ visible: true, x: e.clientX, y: e.clientY, instId, schemaName }); }}
            >
              <DatabaseOutlined style={{ color: '#fa8c16', fontSize: 12 }} />
              <span style={{ fontSize: 12 }}>{schemaName}</span>
            </span>
          ),
          isLeaf: false,
        }));
      }

      // Database → categories (tables/views/procedures/functions/triggers/events)
      if (k.startsWith('schema-') && schemaCacheState[k]) {
        const parsed = parseSchemaKey(k);
        const instId = parsed?.instId || '';
        const schemaName = parsed?.schemaName || '';
        const schema = schemaCacheState[k];

        if (loadingKeys.includes(k)) {
          children = [{ key: `${k}-loading`, title: <LoadingOutlined spin />, isLeaf: true }];
        } else {
          /** build table/view leaf nodes (DBeaver-style category children) — with search filtering */
          const buildItemNodes = (items: TableSchema[], icon: React.ReactNode) => {
            let filtered = items;
            if (s) {
              filtered = items.filter(item =>
                item.name.toLowerCase().includes(s) ||
                (item.columns || []).some(col => col.name.toLowerCase().includes(s))
              );
            }
            return filtered.map(item => {
              const tblKey = `tbl-${instId}-${schemaName}-${item.name}`;
              const detail = tableDetailCache[tblKey];
              const loading = loadingKeys.includes(tblKey);

              // 索引节点列表
              const indexNodes = (detail?.indexes || []).map((idx: any, i: number) => ({
                key: `idx-${item.name}-${i}`,
                title: (
                  <span style={{ fontSize: 11.5, display: 'flex', alignItems: 'center', gap: 4 }}>
                    {idx.unique === 'Y' ? <KeyOutlined style={{ color: '#faad14', fontSize: 10 }} /> : <ThunderboltOutlined style={{ color: '#bbb', fontSize: 10 }} />}
                    <span>{idx.name}</span>
                    <span style={{ color: '#aaa', fontSize: 10 }}>{idx.column}</span>
                  </span>
                ),
                isLeaf: true,
              }));

              // 外键节点列表
              const fkNodes = (detail?.foreignKeys || []).map((fk: any, i: number) => ({
                key: `fk-${item.name}-${i}`,
                title: (
                  <span style={{ fontSize: 11.5, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <LinkOutlined style={{ color: '#13c2c2', fontSize: 10 }} />
                    <span>{fk.constraint_name || fk.name || `FK_${i + 1}`}</span>
                    <span style={{ color: '#aaa', fontSize: 10 }}>→ {fk.referenced_table || fk.referenced_table_name}</span>
                  </span>
                ),
                isLeaf: true,
              }));

              // 约束节点列表
              const constNodes = (detail?.constraints || []).map((c: any, i: number) => ({
                key: `const-${item.name}-${i}`,
                title: (
                  <span style={{ fontSize: 11.5, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <SafetyCertificateOutlined style={{ color: '#722ed1', fontSize: 10 }} />
                    <span>{c.name}</span>
                    <Tag style={{ fontSize: 9, lineHeight: '14px', padding: '0 3px', borderRadius: 3 }}>{c.type}</Tag>
                  </span>
                ),
                isLeaf: true,
              }));

              // 触发器节点列表
              const trgNodes = (detail?.triggers || []).map((t: string, i: number) => ({
                key: `trg-${item.name}-${i}`,
                title: (
                  <span style={{ fontSize: 11.5, display: 'flex', alignItems: 'center', gap: 4 }}>
                    <ThunderboltOutlined style={{ color: '#eb2f96', fontSize: 10 }} />
                    <span>{t}</span>
                  </span>
                ),
                isLeaf: true,
              }));

              return {
                key: tblKey,
                title: (
                  <span 
                    style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12 }}
                    onContextMenu={(e) => { e.preventDefault(); e.stopPropagation(); setContextMenu({ visible: true, x: e.clientX, y: e.clientY, instId, schemaName, tableName: item.name }); }}
                  >
                    {icon}
                    <span>{item.name}</span>
                  </span>
                ),
                isLeaf: false,
                children: [
                  // ── 列 ──
                  {
                    key: `cat-cols-${instId}-${schemaName}-${item.name}`,
                    title: (
                      <span style={{ fontSize: 11.5, color: '#555' }}>
                        <FieldStringOutlined style={{ marginRight: 4, color: '#1677ff', fontSize: 11 }} />列 ({item.columns?.length || 0})
                      </span>
                    ),
                    isLeaf: false,
                    children: (s
                      ? (item.columns || []).filter(col => col.name.toLowerCase().includes(s))
                      : (item.columns || [])
                    ).map(col => ({
                      key: `col-${instId}-${schemaName}-${item.name}.${col.name}`,
                      title: (
                        <span
                          style={{ display: 'flex', alignItems: 'center', gap: 3, fontSize: 11.5 }}
                          onDoubleClick={(e: React.MouseEvent) => { e.stopPropagation(); onInsertToEditor?.(col.name); }}
                        >
                          <FieldStringOutlined style={{ color: '#bbb', fontSize: 10 }} />
                          <span>{col.name}</span>
                          <span style={{ color: '#ccc', fontSize: 10 }}>{col.type}</span>
                          {col.key === 'PRI' && <Tag color="gold" style={{ fontSize: 8, lineHeight: '14px', margin: 0, padding: '0 2px' }}>PK</Tag>}
                          {!!col.comment && (
                            <Tooltip title={col.comment} mouseEnterDelay={0.6}>
                              <CommentOutlined style={{ color: '#d9d9d9', fontSize: 10 }} />
                            </Tooltip>
                          )}
                        </span>
                      ),
                      isLeaf: true,
                    })),
                  },
                  // ── 索引 ──
                  {
                    key: `cat-index-${instId}-${schemaName}-${item.name}`,
                    title: <span style={{ fontSize: 11.5, color: '#555' }}><ThunderboltOutlined style={{ marginRight: 4, color: '#fa8c16', fontSize: 11 }} />索引 ({detail?.indexes?.length || 0})</span>,
                    isLeaf: loading ? true : indexNodes.length === 0,
                    children: loading ? [{ key: `${tblKey}-idx-loading`, title: <LoadingOutlined spin />, isLeaf: true }] : indexNodes.length > 0 ? indexNodes : undefined,
                  },
                  // ── 外键 ──
                  {
                    key: `cat-fk-${instId}-${schemaName}-${item.name}`,
                    title: <span style={{ fontSize: 11.5, color: '#555' }}><LinkOutlined style={{ marginRight: 4, color: '#13c2c2', fontSize: 11 }} />外键 ({detail?.foreignKeys?.length || 0})</span>,
                    isLeaf: loading ? true : fkNodes.length === 0,
                    children: loading ? [{ key: `${tblKey}-fk-loading`, title: <LoadingOutlined spin />, isLeaf: true }] : fkNodes.length > 0 ? fkNodes : undefined,
                  },
                  // ── 触发器 ──
                  {
                    key: `cat-trg-${instId}-${schemaName}-${item.name}`,
                    title: <span style={{ fontSize: 11.5, color: '#555' }}><ThunderboltOutlined style={{ marginRight: 4, color: '#eb2f96', fontSize: 11 }} />触发器 ({detail?.triggers?.length || 0})</span>,
                    isLeaf: loading ? true : trgNodes.length === 0,
                    children: loading ? [{ key: `${tblKey}-trg-loading`, title: <LoadingOutlined spin />, isLeaf: true }] : trgNodes.length > 0 ? trgNodes : undefined,
                  },
                  // ── 约束 ──
                  {
                    key: `cat-const-${instId}-${schemaName}-${item.name}`,
                    title: <span style={{ fontSize: 11.5, color: '#555' }}><SafetyCertificateOutlined style={{ marginRight: 4, color: '#722ed1', fontSize: 11 }} />约束 ({detail?.constraints?.length || 0})</span>,
                    isLeaf: loading ? true : constNodes.length === 0,
                    children: loading ? [{ key: `${tblKey}-const-loading`, title: <LoadingOutlined spin />, isLeaf: true }] : constNodes.length > 0 ? constNodes : undefined,
                  },
                  // ── DDL ──
                  {
                    key: `cat-ddl-${instId}-${schemaName}-${item.name}`,
                    title: <span 
                      style={{ fontSize: 11.5, color: '#555' }}
                      onDoubleClick={(e) => { e.stopPropagation(); if (detail?.ddl) onInsertToEditor?.(detail.ddl); }}
                    ><CodeOutlined style={{ marginRight: 4, color: '#999', fontSize: 11 }} />DDL</span>,
                    isLeaf: true,
                  },
                ],
              };
            });
          };

          /** build simple name list nodes (for procedures/functions/triggers/events) — with search filtering */
          const buildNameNodes = (names: string[], icon: React.ReactNode) => {
            let filtered = names;
            if (s) filtered = names.filter(n => n.toLowerCase().includes(s));
            return filtered.map(name => ({
              key: `obj-${name}`,
              title: (
                <span style={{ display: 'flex', alignItems: 'center', gap: 5, fontSize: 12 }}>
                  {icon}
                  <span>{name}</span>
                </span>
              ),
              isLeaf: true,
            }));
          };

          children = ALL_CATS.map(cat => {
            const meta = CAT_META[cat];
            let catChildren: DataNode[] = [];

            switch (cat) {
              case 'tables':
                catChildren = buildItemNodes(schema.tables || [], <TableOutlined style={{ color: meta.color, fontSize: 11 }} />);
                break;
              case 'views':
                catChildren = buildItemNodes(schema.views || [], <EyeOutlined style={{ color: meta.color, fontSize: 11 }} />);
                break;
              case 'procedures':
                catChildren = buildNameNodes(schema.procedures || [], <CodeOutlined style={{ color: meta.color, fontSize: 11 }} />);
                break;
              case 'functions':
                catChildren = buildNameNodes(schema.functions || [], <CodeOutlined style={{ color: meta.color, fontSize: 11 }} />);
                break;
              case 'triggers':
                catChildren = buildNameNodes(schema.triggers || [], <ThunderboltOutlined style={{ color: meta.color, fontSize: 11 }} />);
                break;
              case 'events':
                catChildren = buildNameNodes(schema.events || [], <ClockCircleOutlined style={{ color: meta.color, fontSize: 11 }} />);
                break;
            }
            const count = catChildren.length;

            return {
              key: `${k}-${cat}`,
              title: (
                <span style={{ display: 'flex', alignItems: 'center', gap: 5 }}>
                  <span style={{ color: meta.color, fontSize: 12 }}>{meta.icon}</span>
                  <span style={{ fontSize: 12 }}>{meta.label}</span>
                  <Tag style={{ fontSize: 9, lineHeight: '14px', padding: '0 4px', borderRadius: 3 }}>
                    {count}
                  </Tag>
                </span>
              ),
              isLeaf: count === 0,
              children: count > 0 ? catChildren : undefined,
            };
          }).filter(Boolean) as DataNode[];
        }
      }

      if (children) return { ...node, children: injectChildren(children) };
      if (node.children) return { ...node, children: injectChildren(node.children) };
      return node;
    });
  };

  /** cacheVersion change forces re-evaluation */
  const finalTreeData: DataNode[] = useMemo(() => injectChildren(treeData),
    [treeData, loadingKeys, cacheVersion, searchText]);

  /** 搜索只过滤已展开节点的数据，不自动展开 */


  /** handle select */

  /** handle select */
  const handleSelect = (_keys: React.Key[], info: any) => {
    const nodeKey = info.node?.key as string;
    setSelectedKeys(_keys);
    if (!nodeKey) return;
    const k = typeof nodeKey === 'string' ? nodeKey : String(nodeKey);

    if (k.startsWith('inst-')) {
      onInstanceSelect?.(k.replace('inst-', ''));
    } else if (k.startsWith('schema-')) {
      const parsed = parseSchemaKey(k);
      if (parsed) onDatabaseSelect?.(parsed.schemaName);
    }
    // 点击表节点不再自动生成SQL，仅选中节点高亮
  };

  /** handle expand — 展开 tbl- 节点时触发 tableDetail 加载 */
  const handleExpand = useCallback((keys: React.Key[]) => {
    setExpandedKeys(keys);
    keys.forEach(key => {
      const k = String(key);
      if (!k.startsWith('tbl-')) return;
      if (tableDetailCache[k] || pendingRequests.has(k)) return;
      const parsed = parseTblKey(k);
      if (!parsed) return;
      const { instId, schemaName, tableName } = parsed;
      pendingRequests.add(k);
      setLoadingKeys(prev => [...prev, k]);
      instanceApi.getTableDetail(instId, tableName, schemaName)
        .then(res => {
          tableDetailCache[k] = res.data.data || { indexes: [], foreignKeys: [], constraints: [], triggers: [], ddl: null };
          cacheVersion++;
          setCacheV(cacheVersion);
        })
        .catch(() => { tableDetailCache[k] = { indexes: [], foreignKeys: [], constraints: [], triggers: [], ddl: null }; cacheVersion++; setCacheV(cacheVersion); })
        .finally(() => { pendingRequests.delete(k); setLoadingKeys(prev => prev.filter(x => x !== k)); });
    });
  }, []);

  return (
    <div style={{
      height: '100%', width: '100%', display: 'flex', flexDirection: 'column',
      background: '#fff',
    }}>
      {/* 标题栏 - DBeaver 风格 */}
      <div style={{
        flexShrink: 0, padding: '6px 10px',
        background: '#f5f5f5', borderBottom: '1px solid #e5e5e5',
        fontSize: 12, fontWeight: 600, color: '#444',
        display: 'flex', alignItems: 'center', gap: 6,
      }}>
        <DatabaseOutlined style={{ fontSize: 13, color: '#52c41a' }} />
        <span>数据库导航器</span>
      </div>
      {/* 搜索框，吸顶 */}
      <div style={{ flexShrink: 0, padding: '6px 10px', borderBottom: '1px solid #f0f0f0' }}>
        <Input
          size="small" allowClear value={searchText}
          onChange={e => setSearchText(e.target.value)}
          placeholder="搜索数据库、表、字段..."
          prefix={<SearchOutlined style={{ color: '#bfbfbf', fontSize: 11 }} />}
          style={{ fontSize: 12 }}
        />
      </div>

      {/* 树形导航，独立滚动 */}
      <div style={{
        flex: 1, minHeight: 0,
        overflow: 'auto',
        padding: '4px 0',
      }}>
        {loadingInsts ? (
          <div style={{ textAlign: 'center', paddingTop: 50 }}><Spin size="small" /></div>
        ) : instances.length === 0 ? (
          <div style={{ textAlign: 'center', paddingTop: 40, color: '#999', fontSize: 12 }}>暂无数据库实例</div>
        ) : (
          <Tree
            key={cacheVersion + '-' + searchText}
            treeData={finalTreeData as TreeDataNode[]}
            expandedKeys={expandedKeys}
            selectedKeys={selectedKeys}
            onSelect={handleSelect as any}
            onExpand={handleExpand}
            loadData={onLoadData}
            blockNode showIcon={false} showLine={false}
            style={{ fontSize: 12, background: 'transparent', padding: '0 2px' }}
          />
        )}
      </div>

      {/* 右键菜单 */}
      {contextMenu && (
        <div
          onClick={(e) => e.stopPropagation()}
          style={{
            position: 'fixed', left: contextMenu.x, top: contextMenu.y, zIndex: 1050,
            background: '#fff', border: '1px solid #d9d9d9', borderRadius: 6,
            boxShadow: '0 6px 16px rgba(0,0,0,0.12)', padding: '4px 0',
            minWidth: 170, fontSize: 13,
          }}
        >
          {/* 表节点才显示：查看表结构 */}
          {contextMenu.tableName && (
            <>
              <MenuItem
                icon={<ProfileOutlined style={{ color: '#722ed1', fontSize: 12 }} />}
                label="查看表结构"
                onClick={() => {
                  onViewTableStructure?.(contextMenu.instId, contextMenu.schemaName, contextMenu.tableName!);
                  setContextMenu(null);
                }}
              />
              <div style={{ height: 1, margin: '2px 8px', background: '#f0f0f0' }} />
            </>
          )}
          <MenuItem
            icon={<PlusOutlined style={{ color: '#1677ff', fontSize: 12 }} />}
            label="新建SQL查询"
            onClick={() => {
              onNewTab?.(contextMenu.instId, contextMenu.schemaName, contextMenu.tableName);
              setContextMenu(null);
            }}
          />
        </div>
      )}
    </div>
  );
};

export default ObjectBrowser;
