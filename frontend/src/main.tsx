import React from 'react';
import ReactDOM from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import { getBasePath } from './utils/basePath';
import App from './App';
import './index.css';

// 配置 Monaco Editor 使用本地包，不从 CDN 加载
loader.config({ monaco });

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <BrowserRouter basename={getBasePath()}>
      <App />
    </BrowserRouter>
  </React.StrictMode>
);
