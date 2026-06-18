import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import type { IncomingMessage, ServerResponse } from 'http'

const NGINX_HOST = 'wxdev.worldunion.com.cn';

export default defineConfig({
  base: '/dataops_dms/',
  plugins: [
    react(),
    {
      name: 'dual-access',
      configureServer(server) {
        server.middlewares.use((req: IncomingMessage, res: ServerResponse, next) => {
          const host = req.headers.host || '';
          const isFromNginx = host === NGINX_HOST || host.startsWith(NGINX_HOST + ':');
          const url = req.url || '';

          if (isFromNginx) {
            // Nginx 已剥离 /dataops_dms 前缀，Vite base=/dataops_dms/ 需要它
            // 把剥离的前缀加回来（但 API 路径保持原样，由 Vite proxy 处理）
            if (!url.startsWith('/api/') && !url.startsWith('/dataops_dms/')) {
              req.url = url === '/' ? '/dataops_dms/' : '/dataops_dms' + url;
            }
          } else {
            // IP 直连：未带 /dataops_dms 前缀的路径重定向到带前缀版本
            if (!url.startsWith('/dataops_dms/') && !url.startsWith('/api/')) {
              const target = url === '/' ? '/dataops_dms/' : '/dataops_dms' + url;
              res.writeHead(302, { Location: target });
              res.end();
              return;
            }
          }

          next();
        });
      },
    },
  ],
  server: {
    host: '0.0.0.0',
    port: 3000,
    allowedHosts: ['wxdev.worldunion.com.cn', 'localhost', '127.0.0.1'],
    proxy: {
      '/dataops_dms/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/dataops_dms/, '')
      },
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  },
  optimizeDeps: {
    include: ['monaco-editor'],
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          monaco: ['monaco-editor'],
        },
      },
    },
  },
})
