import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import { instanceApi } from '../utils/api';

interface SavedQuery {
  id: string;
  name: string;
  sql: string;
  databaseId?: string;
  databaseName?: string;
  isFavorite: boolean;
  executeCount: number;
  lastExecutedAt?: string;
  totalDuration: number;
  createdAt: string;
  updatedAt: string;
}

interface ExecutionRecord {
  id: string;
  savedQueryId?: string;
  sql: string;
  databaseId: string;
  databaseName?: string;
  executionTime: number;
  affectedRows?: number;
  rowCount?: number;
  success: boolean;
  executedAt: string;
}

interface TableSchemaItem {
  name: string;
  columns: Array<{ name: string; type: string; key: string; comment?: string }>;
}

interface AppState {
  // SQL Tab
  activeTab: string;
  sqlTabs: { key: string; title: string; sql: string; databaseId?: string; databaseName?: string }[];
  setActiveTab: (tab: string) => void;
  addTab: (tab: { key: string; title: string; sql: string; databaseId?: string; databaseName?: string }) => void;
  removeTab: (key: string) => void;
  updateTab: (key: string, updates: Partial<{ title: string; sql: string; databaseId: string; databaseName: string }>) => void;

  // 保存查询
  savedQueries: SavedQuery[];
  addSavedQuery: (query: Omit<SavedQuery, 'id' | 'createdAt' | 'updatedAt' | 'executeCount' | 'totalDuration'>) => string;
  updateSavedQuery: (id: string, updates: Partial<SavedQuery>) => void;
  deleteSavedQuery: (id: string) => void;
  toggleFavorite: (id: string) => void;
  recordExecution: (savedQueryId: string, executionTime: number) => void;

  // 执行历史
  executionHistory: ExecutionRecord[];
  addExecutionRecord: (record: Omit<ExecutionRecord, 'id' | 'executedAt'>) => void;
  clearExecutionHistory: () => void;

  // Schema 对象浏览器
  schemaTables: TableSchemaItem[];
  schemaLoading: boolean;
  expandedKeys: string[];
  selectedSchemaKey: string;
  schemaSearchText: string;
  setExpandedKeys: (keys: string[]) => void;
  setSelectedSchemaKey: (key: string) => void;
  setSchemaSearchText: (text: string) => void;
  loadSchemaTables: (databaseId: string, databaseName: string) => Promise<void>;

  // 全局
  sidebarCollapsed: boolean;
  toggleSidebar: () => void;
}

export const useAppStore = create<AppState>()(
  persist(
    (set, get) => ({
      activeTab: '',
      sqlTabs: [],

      setActiveTab: (tab) => set({ activeTab: tab }),

      addTab: (tab) => {
        const newTab = tab || { key: `tab-${Date.now()}`, title: `SQL ${get().sqlTabs.length + 1}`, sql: '' };
        return set((state) => ({
          sqlTabs: [...state.sqlTabs, newTab],
          activeTab: newTab.key,
        }));
      },

      removeTab: (key) => set((state) => {
        const newTabs = state.sqlTabs.filter((t) => t.key !== key);
        if (newTabs.length === 0) {
          const defaultTab = { key: `tab-${Date.now()}`, title: '查询 1', sql: '', databaseId: undefined as string | undefined };
          return { sqlTabs: [defaultTab], activeTab: defaultTab.key };
        }
        return {
          sqlTabs: newTabs,
          activeTab: state.activeTab === key ? newTabs[newTabs.length - 1].key : state.activeTab,
        };
      }),

      updateTab: (key, updates) => set((state) => ({
        sqlTabs: state.sqlTabs.map((t) => (t.key === key ? { ...t, ...updates } : t)),
      })),

      // 保存的查询
      savedQueries: [],

      addSavedQuery: (query) => {
        const id = `sq-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
        const now = new Date().toISOString();
        const newQuery: SavedQuery = {
          ...query, id,
          isFavorite: query.isFavorite ?? false,
          executeCount: 0, totalDuration: 0,
          createdAt: now, updatedAt: now,
        };
        set((state) => ({ savedQueries: [newQuery, ...state.savedQueries] }));
        return id;
      },

      updateSavedQuery: (id, updates) => set((state) => ({
        savedQueries: state.savedQueries.map((q) =>
          q.id === id ? { ...q, ...updates, updatedAt: new Date().toISOString() } : q
        ),
      })),

      deleteSavedQuery: (id) => set((state) => ({
        savedQueries: state.savedQueries.filter((q) => q.id !== id)
      })),

      toggleFavorite: (id) => set((state) => ({
        savedQueries: state.savedQueries.map((q) =>
          q.id === id ? { ...q, isFavorite: !q.isFavorite, updatedAt: new Date().toISOString() } : q
        ),
      })),

      recordExecution: (savedQueryId, executionTime) => set((state) => ({
        savedQueries: state.savedQueries.map((q) =>
          q.id === savedQueryId
            ? { ...q, executeCount: q.executeCount + 1, totalDuration: q.totalDuration + executionTime, lastExecutedAt: new Date().toISOString() }
            : q
        ),
      })),

      // 执行历史
      executionHistory: [],

      addExecutionRecord: (record) => {
        const id = `ex-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
        set((state) => ({
          executionHistory: [{ ...record, id, executedAt: new Date().toISOString() }, ...state.executionHistory].slice(0, 500),
        }));
      },

      clearExecutionHistory: () => set({ executionHistory: [] }),

      // Schema 对象浏览器
      schemaTables: [],
      schemaLoading: false,
      expandedKeys: [],
      selectedSchemaKey: '',
      schemaSearchText: '',
      setExpandedKeys: (keys) => set({ expandedKeys: keys }),
      setSelectedSchemaKey: (key) => set({ selectedSchemaKey: key }),
      setSchemaSearchText: (text) => set({ schemaSearchText: text }),

      loadSchemaTables: async (databaseId, databaseName) => {
        set({ schemaLoading: true });
        try {
          const res = await instanceApi.getSchema(databaseId, databaseName);
          const data: TableSchemaItem[] = res.data.data || [];
          set({
            schemaTables: data,
            schemaLoading: false,
            expandedKeys: data.map(t => `table-${t.name}`),
          });
        } catch {
          set({ schemaTables: [], schemaLoading: false });
        }
      },

      sidebarCollapsed: false,
      toggleSidebar: () => set((state) => ({ sidebarCollapsed: !state.sidebarCollapsed })),
    }),
    {
      name: 'dataops-sql-workbench',
      partialize: (state) => ({ savedQueries: state.savedQueries, executionHistory: state.executionHistory }),
    }
  )
);
