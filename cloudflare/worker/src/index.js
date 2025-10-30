/**
 * Cloudflare Worker 主入口
 * 图书馆实时流量和座位查询系统
 */

import { LibraryAuthenticator } from './auth.js';
import { getTrafficData } from './traffic.js';
import { getSeatsSummary, getSeatsDetail, AREAS } from './seats.js';
import { jsonResponse, errorResponse, corsHeaders } from './utils.js';

// 全局认证器缓存
let globalAuthenticator = null;
let lastAuthTime = 0;
const AUTH_CACHE_DURATION = 30 * 60 * 1000; // 30分钟

/**
 * 获取或创建认证器
 */
async function getAuthenticator(env) {
  const now = Date.now();
  
  // 如果缓存存在且未过期，检查是否仍然有效
  if (globalAuthenticator && (now - lastAuthTime < AUTH_CACHE_DURATION)) {
    const isValid = await globalAuthenticator.isValid();
    if (isValid) {
      console.log('使用缓存的认证器');
      return globalAuthenticator;
    }
  }

  // 重新认证
  console.log('创建新的认证器');
  const username = env.USERNAME;
  const eduPassword = env.EDU_PASSWORD;
  const libPassword = env.LIB_PASSWORD;

  if (!username || !eduPassword || !libPassword) {
    throw new Error('缺少必要的环境变量：USERNAME, EDU_PASSWORD, LIB_PASSWORD');
  }

  const authenticator = new LibraryAuthenticator(username, eduPassword, libPassword);
  const authResult = await authenticator.authenticate();

  if (!authResult.success) {
    throw new Error(`认证失败: ${authResult.message}`);
  }

  globalAuthenticator = authenticator;
  lastAuthTime = now;

  return authenticator;
}

/**
 * 处理API请求
 */
async function handleRequest(request, env) {
  const url = new URL(request.url);
  const path = url.pathname;

  // 处理CORS预检请求
  if (request.method === 'OPTIONS') {
    return new Response(null, {
      headers: corsHeaders()
    });
  }

  try {
    // API路由
    if (path === '/api/traffic') {
      // 获取流量数据
      const authenticator = await getAuthenticator(env);
      const data = await getTrafficData(authenticator);
      return jsonResponse(data);
    }

    if (path === '/api/seats/summary') {
      // 获取座位摘要
      const daysOffset = parseInt(url.searchParams.get('days_offset') || '0');
      if (![0, 1].includes(daysOffset)) {
        return errorResponse('日期参数错误', 400);
      }

      const authenticator = await getAuthenticator(env);
      const data = await getSeatsSummary(authenticator, daysOffset);
      return jsonResponse(data);
    }

    if (path === '/api/seats/detail') {
      // 获取座位详情
      const areaName = url.searchParams.get('area');
      const daysOffset = parseInt(url.searchParams.get('days_offset') || '0');

      if (!areaName) {
        return errorResponse('缺少区域参数', 400);
      }

      if (![0, 1].includes(daysOffset)) {
        return errorResponse('日期参数错误', 400);
      }

      const authenticator = await getAuthenticator(env);
      const data = await getSeatsDetail(authenticator, areaName, daysOffset);
      return jsonResponse(data);
    }

    if (path === '/api/areas') {
      // 获取所有区域列表
      return jsonResponse({
        success: true,
        areas: AREAS
      });
    }

    if (path === '/api/health') {
      // 健康检查
      return jsonResponse({
        success: true,
        message: '服务正常运行',
        timestamp: Date.now()
      });
    }

    // 默认响应
    return jsonResponse({
      success: true,
      message: '图书馆实时流量和座位查询 API',
      version: '1.0.0',
      endpoints: {
        traffic: '/api/traffic',
        seatsSummary: '/api/seats/summary?days_offset=0',
        seatsDetail: '/api/seats/detail?area=区域名&days_offset=0',
        areas: '/api/areas',
        health: '/api/health'
      }
    });

  } catch (error) {
    console.error('处理请求失败:', error);
    return errorResponse(error.message, 500);
  }
}

/**
 * Worker入口
 */
export default {
  async fetch(request, env, ctx) {
    return handleRequest(request, env);
  }
};
