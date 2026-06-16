import React, { useState, useEffect } from 'react';
import { Card, Select, Row, Col, Statistic, Table, Tag, Button, Space, message, Tabs, Alert, Progress, Descriptions, Spin } from 'antd';
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

  // Data states
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
      message.error('Error');