/**
 * 图书馆流量监控模块
 */

import { httpRequest } from './utils.js';

const TRAFFIC_URL = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OA==/LjE0Ny4xMDEuMTUyLjEwMi4xMDEuMTAyLjE1Ny45Ny4xNTEuOTkuMTA0LjEwMi4xNTIuMTEyLjExMS4xNTM=/book/view";
const TOTAL_CAPACITY = 2749;

/**
 * 获取当前在馆人数
 */
export async function getCurrentTraffic(authenticator) {
  try {
    if (!authenticator || !authenticator.myClientTicket) {
      console.error('流量监控：缺少有效认证');
      return null;
    }

    const response = await httpRequest(TRAFFIC_URL, {
      headers: {
        'Cookie': `my_client_ticket=${authenticator.myClientTicket}`,
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36'
      }
    });

    if (!response || !response.ok) {
      console.error(`流量监控：请求失败，状态码 ${response ? response.status : 'unknown'}`);
      return null;
    }

    const html = await response.text();
    
    // 解析页面获取数字
    const spanPattern = /<span[^>]*style="[^"]*font-size:20px[^"]*"[^>]*>(\d+)<\/span>/g;
    const matches = [...html.matchAll(spanPattern)];
    
    if (matches.length < 2) {
      console.error('流量监控：页面结构解析失败，未找到足够的数字');
      return null;
    }

    const num1 = parseInt(matches[0][1]);
    const num2 = parseInt(matches[1][1]);

    // 总座位数应该是较大的数字，剩余座位数是较小的数字
    const totalSeats = Math.max(num1, num2);
    const remainingSeats = Math.min(num1, num2);

    // 在馆人数 = 总座位 - 剩余座位
    const count = totalSeats - remainingSeats;

    console.log(`流量监控：当前在馆人数 ${count}/${totalSeats} (剩余${remainingSeats})`);
    
    return {
      count,
      total: totalSeats,
      remaining: remainingSeats,
      timestamp: Math.floor(Date.now() / 1000)
    };
  } catch (error) {
    console.error('流量监控：获取流量失败 -', error);
    return null;
  }
}

/**
 * 获取流量数据（API接口）
 */
export async function getTrafficData(authenticator) {
  const trafficData = await getCurrentTraffic(authenticator);
  
  if (!trafficData) {
    return {
      success: false,
      message: '获取流量数据失败',
      total_capacity: TOTAL_CAPACITY
    };
  }

  const percentage = trafficData.total > 0 
    ? Math.round((trafficData.count / trafficData.total) * 1000) / 10 
    : 0;

  const now = new Date();
  const timeStr = now.toTimeString().split(' ')[0]; // HH:MM:SS
  const updateTime = now.toISOString().replace('T', ' ').split('.')[0];

  return {
    success: true,
    current_count: trafficData.count,
    total_capacity: trafficData.total,
    remaining: trafficData.remaining,
    timestamp: trafficData.timestamp,
    count: trafficData.count,
    percentage: percentage,
    time: timeStr,
    updated_at: updateTime
  };
}
