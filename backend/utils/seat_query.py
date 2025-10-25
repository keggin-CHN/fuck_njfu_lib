"""
座位查询服务
用于获取图书馆各楼层的座位占用情况
"""
import logging
from datetime import datetime, timedelta
from utils.auth_manager import HttpClient

logger = logging.getLogger(__name__)


class SeatQueryService:
    """座位查询服务"""

    # 所有区域配置
    AREAS = {
        '二层A区': {'roomId': 100455344, 'floor': 2, 'area': 'A'},
        '二层B区': {'roomId': 100455346, 'floor': 2, 'area': 'B'},
        '三层A区': {'roomId': 100455350, 'floor': 3, 'area': 'A'},
        '三层B区': {'roomId': 100455352, 'floor': 3, 'area': 'B'},
        '三层C区': {'roomId': 100455354, 'floor': 3, 'area': 'C'},
        '三楼夹层': {'roomId': 111488386, 'floor': 3, 'area': '夹层'},
        '四层A区': {'roomId': 100455356, 'floor': 4, 'area': 'A'},
        '四层夹层': {'roomId': 111488388, 'floor': 4, 'area': '夹层'},
        '五层A区': {'roomId': 100455358, 'floor': 5, 'area': 'A'},
        '六层A区': {'roomId': 100455360, 'floor': 6, 'area': 'A'},
        '七层北侧': {'roomId': 106658017, 'floor': 7, 'area': '北'},
        '七层南侧': {'roomId': 111488396, 'floor': 7, 'area': '南'},
    }

    @staticmethod
    def get_date_string(days_offset=0):
        """获取日期字符串，格式：20251023"""
        target_date = datetime.now() + timedelta(days=days_offset)
        return target_date.strftime('%Y%m%d')

    @staticmethod
    def get_seats_data(authenticator, room_id, date_str):
        """
        获取指定房间的座位数据

        Args:
            authenticator: 认证器实例
            room_id: 房间ID
            date_str: 日期字符串，格式：20251023

        Returns:
            座位数据列表，或None（失败时）
        """
        try:
            url = HttpClient.get_lib_url("ic-web/reserve")  # 修改：使用正确的端点

            logger.info(f"正在获取房间 {room_id} 日期 {date_str} 的座位数据")
            logger.info(f"请求URL: {url}")

            params = {
                "vpn-12-libseat.njfu.edu.cn": "",
                "roomIds": room_id,  # 修改：使用roomIds而不是roomId
                "resvDates": date_str,
                "sysKind": 8
            }

            api_headers = {
                "token": authenticator.token,
                "lan": "1",
                "Referer": HttpClient.get_lib_url(""),
                "Origin": "https://webvpn.njfu.edu.cn",
            }

            logger.info(f"请求参数: {params}")
            logger.info(f"认证token存在: {bool(authenticator.token)}")

            try:
                response = HttpClient.get(
                    url,
                    headers=api_headers,
                    params=params,
                    cookies={"my_client_ticket": authenticator.my_client_ticket},
                    timeout=15
                )
            except Exception as e:
                logger.error(f"HTTP请求异常: {str(e)}", exc_info=True)
                response = None

            if response and response.status_code == 200:
                result = response.json()
                logger.info(f"API响应: code={result.get('code')}, message={result.get('message')}")
                if result.get("code") == 0:
                    data = result.get("data", [])
                    logger.info(f"成功获取房间 {room_id} 的座位数据，共 {len(data)} 个座位")
                    return data
                else:
                    error_msg = result.get('message', '未知错误')
                    logger.error(f"获取座位数据失败: {error_msg}, 完整响应: {result}")
            else:
                status = response.status_code if response else "请求失败"
                logger.error(f"获取座位数据请求失败，状态码：{status}")
                if response:
                    logger.error(f"响应内容: {response.text[:500]}")

        except Exception as e:
            logger.error(f"获取座位数据过程出错: {str(e)}", exc_info=True)

        return None

    @staticmethod
    def analyze_seats(seats_data):
        """
        分析座位数据，统计总座位数和空闲座位数

        Args:
            seats_data: 座位数据列表

        Returns:
            {
                'total': 总座位数,
                'available': 空闲座位数,
                'occupied': 已占用座位数,
                'rate': 占用率
            }
        """
        if not seats_data:
            return {
                'total': 0,
                'available': 0,
                'occupied': 0,
                'rate': 0
            }

        total = len(seats_data)
        available = 0
        occupied = 0

        for seat in seats_data:
            resv_info = seat.get('resvInfo', [])
            if not resv_info or len(resv_info) == 0:
                available += 1
            else:
                occupied += 1

        rate = round((occupied / total * 100), 1) if total > 0 else 0

        return {
            'total': total,
            'available': available,
            'occupied': occupied,
            'rate': rate
        }

    @staticmethod
    def get_all_areas_summary(authenticator, date_str):
        """
        获取所有区域的座位摘要信息

        Args:
            authenticator: 认证器实例
            date_str: 日期字符串

        Returns:
            所有区域的统计数据
        """
        summary = {}

        for area_name, config in SeatQueryService.AREAS.items():
            room_id = config['roomId']
            seats_data = SeatQueryService.get_seats_data(authenticator, room_id, date_str)
            stats = SeatQueryService.analyze_seats(seats_data)

            summary[area_name] = {
                'floor': config['floor'],
                'area': config['area'],
                'roomId': room_id,
                'stats': stats,
                'seats': seats_data or []
            }

        return summary

    @staticmethod
    def get_floor_summary(all_areas_summary):
        """
        按楼层汇总统计

        Args:
            all_areas_summary: 所有区域的统计数据

        Returns:
            按楼层汇总的数据
        """
        floor_summary = {}

        for area_name, data in all_areas_summary.items():
            floor = data['floor']
            stats = data['stats']

            if floor not in floor_summary:
                floor_summary[floor] = {
                    'floor': floor,
                    'total': 0,
                    'available': 0,
                    'occupied': 0,
                    'areas': []
                }

            floor_summary[floor]['total'] += stats['total']
            floor_summary[floor]['available'] += stats['available']
            floor_summary[floor]['occupied'] += stats['occupied']
            floor_summary[floor]['areas'].append({
                'name': area_name,
                'stats': stats
            })

        # 计算每层的占用率
        for floor, data in floor_summary.items():
            if data['total'] > 0:
                data['rate'] = round((data['occupied'] / data['total'] * 100), 1)
            else:
                data['rate'] = 0

        return floor_summary

    @staticmethod
    def convert_timestamp_to_time(timestamp):
        """将毫秒时间戳转换为时间字符串"""
        if not timestamp:
            return ""
        return datetime.fromtimestamp(timestamp / 1000).strftime('%H:%M')

    @staticmethod
    def get_seat_status_text(resv_status):
        """获取预约状态文本"""
        status_map = {
            1027: "预约中",
            1093: "使用中"
        }
        return status_map.get(resv_status, "未知")
