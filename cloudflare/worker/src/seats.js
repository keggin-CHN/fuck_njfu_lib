/**
 * 座位查询模块
 */

import { getLibUrl, httpRequest, getDateString, convertTimestampToTime, getSeatStatusText } from './utils.js';

// 所有区域配置
export const AREAS = {
  '二层A区': { roomId: 100455344, floor: 2, area: 'A' },
  '二层B区': { roomId: 100455346, floor: 2, area: 'B' },
  '三层A区': { roomId: 100455350, floor: 3, area: 'A' },
  '三层B区': { roomId: 100455352, floor: 3, area: 'B' },
  '三层C区': { roomId: 100455354, floor: 3, area: 'C' },
  '三楼夹层': { roomId: 111488386, floor: 3, area: '夹层' },
  '四层A区': { roomId: 100455356, floor: 4, area: 'A' },
  '四层夹层': { roomId: 111488388, floor: 4, area: '夹层' },
  '五层A区': { roomId: 100455358, floor: 5, area: 'A' },
  '六层A区': { roomId: 100455360, floor: 6, area: 'A' },
  '七层北侧': { roomId: 106658017, floor: 7, area: '北' },
  '七层南侧': { roomId: 111488396, floor: 7, area: '南' }
};

/**
 * 获取指定房间的座位数据
 */
export async function getSeatsData(authenticator, roomId, dateStr) {
  try {
    const url = getLibUrl("ic-web/reserve");
    const params = new URLSearchParams({
      "vpn-12-libseat.njfu.edu.cn": "",
      "roomIds": roomId,
      "resvDates": dateStr,
      "sysKind": "8"
    });

    const response = await httpRequest(`${url}?${params.toString()}`, {
      headers: {
        'Cookie': `my_client_ticket=${authenticator.myClientTicket}`,
        'token': authenticator.token,
        'lan': '1',
        'Referer': getLibUrl(""),
        'Origin': 'https://webvpn.njfu.edu.cn',
        'Accept': 'application/json, text/plain, */*'
      }
    });

    if (response && response.ok) {
      const result = await response.json();
      if (result.code === 0) {
        console.log(`成功获取房间 ${roomId} 的座位数据，共 ${result.data.length} 个座位`);
        return result.data;
      } else {
        console.error(`获取座位数据失败: ${result.message}`);
      }
    } else {
      console.error(`获取座位数据请求失败，状态码：${response ? response.status : 'unknown'}`);
    }
  } catch (error) {
    console.error(`获取座位数据过程出错: ${error.message}`);
  }

  return null;
}

/**
 * 分析座位数据
 */
export function analyzeSeats(seatsData) {
  if (!seatsData || seatsData.length === 0) {
    return {
      total: 0,
      available: 0,
      occupied: 0,
      rate: 0
    };
  }

  const total = seatsData.length;
  let available = 0;
  let occupied = 0;

  for (const seat of seatsData) {
    const resvInfo = seat.resvInfo || [];
    if (resvInfo.length === 0) {
      available++;
    } else {
      occupied++;
    }
  }

  const rate = total > 0 ? Math.round((occupied / total) * 1000) / 10 : 0;

  return {
    total,
    available,
    occupied,
    rate
  };
}

/**
 * 获取所有区域的座位摘要
 */
export async function getAllAreasSummary(authenticator, dateStr) {
  const summary = {};
  const areaNames = Object.keys(AREAS);

  for (let i = 0; i < areaNames.length; i++) {
    const areaName = areaNames[i];
    const config = AREAS[areaName];

    try {
      const seatsData = await getSeatsData(authenticator, config.roomId, dateStr);
      const stats = analyzeSeats(seatsData);
      
      summary[areaName] = {
        floor: config.floor,
        area: config.area,
        roomId: config.roomId,
        stats: stats,
        seats: seatsData || []
      };
    } catch (error) {
      console.error(`查询区域 ${areaName} 时出错: ${error.message}`);
      summary[areaName] = {
        floor: config.floor,
        area: config.area,
        roomId: config.roomId,
        stats: { total: 0, available: 0, occupied: 0, rate: 0 },
        seats: []
      };
    }
  }

  return summary;
}

/**
 * 按楼层汇总统计
 */
export function getFloorSummary(allAreasSummary) {
  const floorSummary = {};

  for (const [areaName, data] of Object.entries(allAreasSummary)) {
    const floor = data.floor;
    const stats = data.stats;

    if (!floorSummary[floor]) {
      floorSummary[floor] = {
        floor: floor,
        total: 0,
        available: 0,
        occupied: 0,
        areas: []
      };
    }

    floorSummary[floor].total += stats.total;
    floorSummary[floor].available += stats.available;
    floorSummary[floor].occupied += stats.occupied;
    floorSummary[floor].areas.push({
      name: areaName,
      stats: stats
    });
  }

  // 计算每层的占用率
  for (const [floor, data] of Object.entries(floorSummary)) {
    if (data.total > 0) {
      data.rate = Math.round((data.occupied / data.total) * 1000) / 10;
    } else {
      data.rate = 0;
    }
  }

  return floorSummary;
}

/**
 * 获取座位摘要（API接口）
 */
export async function getSeatsSummary(authenticator, daysOffset = 0) {
  try {
    const dateStr = getDateString(daysOffset);

    // 获取所有区域的座位数据
    const allAreasSummary = await getAllAreasSummary(authenticator, dateStr);

    // 按楼层汇总
    const floorSummary = getFloorSummary(allAreasSummary);

    // 计算总计
    const totalStats = {
      total: 0,
      available: 0,
      occupied: 0
    };

    for (const data of Object.values(allAreasSummary)) {
      const stats = data.stats;
      totalStats.total += stats.total;
      totalStats.available += stats.available;
      totalStats.occupied += stats.occupied;
    }

    if (totalStats.total > 0) {
      totalStats.rate = Math.round((totalStats.occupied / totalStats.total) * 1000) / 10;
    } else {
      totalStats.rate = 0;
    }

    return {
      success: true,
      date: dateStr,
      total: totalStats,
      floors: floorSummary,
      areas: allAreasSummary
    };
  } catch (error) {
    console.error('获取座位摘要失败:', error);
    return {
      success: false,
      message: `获取座位数据失败: ${error.message}`
    };
  }
}

/**
 * 获取指定区域的座位详情（API接口）
 */
export async function getSeatsDetail(authenticator, areaName, daysOffset = 0) {
  try {
    if (!AREAS[areaName]) {
      return {
        success: false,
        message: '区域不存在'
      };
    }

    const dateStr = getDateString(daysOffset);
    const roomId = AREAS[areaName].roomId;

    const seatsData = await getSeatsData(authenticator, roomId, dateStr);

    if (seatsData === null) {
      return {
        success: false,
        message: '获取座位数据失败'
      };
    }

    // 处理座位数据，转换时间戳
    const processedSeats = seatsData.map(seat => {
      const resvList = (seat.resvInfo || []).map(resv => ({
        startTime: convertTimestampToTime(resv.startTime),
        endTime: convertTimestampToTime(resv.endTime),
        status: getSeatStatusText(resv.resvStatus)
      }));

      return {
        devId: seat.devId,
        devName: seat.devName,
        devStatus: seat.devStatus,
        isAvailable: (seat.resvInfo || []).length === 0,
        reservations: resvList
      };
    });

    return {
      success: true,
      area: areaName,
      date: dateStr,
      seats: processedSeats
    };
  } catch (error) {
    console.error('获取座位详情失败:', error);
    return {
      success: false,
      message: `获取座位详情失败: ${error.message}`
    };
  }
}
