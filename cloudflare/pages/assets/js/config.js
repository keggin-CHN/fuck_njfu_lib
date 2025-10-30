/**
 * 配置文件
 * 请在部署后修改API_BASE_URL为你的Worker地址
 */

// Worker API地址
// 本地开发: http://localhost:8787
// 生产环境: https://your-worker.your-subdomain.workers.dev
const API_BASE_URL = 'https://your-worker.your-subdomain.workers.dev';

// 导出配置
window.CONFIG = {
    API_BASE_URL: API_BASE_URL
};
