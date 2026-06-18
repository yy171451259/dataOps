/**
 * 应用基础路径，与 Vite base 配置一致
 * IP 直连会被中间件自动重定向到 /dataops_dms/...，此处按需检测兜底
 */
export function getBasePath(): string {
  return window.location.pathname.startsWith('/dataops_dms') ? '/dataops_dms' : '';
}

export function getFullPath(subPath: string): string {
  return '/dataops_dms' + subPath;
}
