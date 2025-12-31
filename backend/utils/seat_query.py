import logging
from datetime import datetime, timedelta
from utils.auth_manager import HttpClient, check_ip_blocked, handle_ip_block

logger = logging.getLogger(__name__)


class SeatQueryService:

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
        target_date = datetime.now() + timedelta(days=days_offset)
        return target_date.strftime('%Y%m%d')

    @staticmethod
    def get_seats_data(authenticator, room_id, date_str):
        url = HttpClient.get_lib_url("ic-web/reserve")

        logger.info(f"正在获取房间 {room_id} 日期 {date_str} 的座位数据")
        logger.info(f"请求URL: {url}")

        params = {
            "vpn-12-libseat.njfu.edu.cn": "",
            "roomIds": room_id,
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

        max_retries = 2
        for retry in range(max_retries):
            try:
                # 使用 authenticator.session 发送请求以保持会话状态
                response = authenticator.session.get(
                    url,
                    headers=api_headers,
                    params=params,
                    timeout=15
                )
                
                # 检查是否 IP 被封禁
                if check_ip_blocked(response, "座位查询"):
                    if handle_ip_block(response, "座位查询"):
                        logger.info("IP 封禁处理成功，重试座位查询...")
                        import time
                        time.sleep(2)
                        continue
                    else:
                        logger.error("IP 封禁处理失败")

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
                if retry < max_retries - 1:
                    import time
                    time.sleep(2)
                    continue

        return None

    @staticmethod
    def analyze_seats(seats_data):
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
    def get_all_areas_summary(authenticator, date_str, progress_callback=None):
        summary = {}
        total_areas = len(SeatQueryService.AREAS)
        
        for i, (area_name, config) in enumerate(SeatQueryService.AREAS.items()):
            try:
                seats_data = SeatQueryService.get_seats_data(authenticator, config['roomId'], date_str)
                stats = SeatQueryService.analyze_seats(seats_data)
                summary[area_name] = {
                    'floor': config['floor'],
                    'area': config['area'],
                    'roomId': config['roomId'],
                    'stats': stats,
                    'seats': seats_data or []
                }
            except Exception as e:
                logger.error(f"查询区域 {area_name} 时出错: {e}", exc_info=True)
            
            if progress_callback:
                progress_callback((i + 1) / total_areas)

        return summary

    @staticmethod
    def get_floor_summary(all_areas_summary):
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

        for floor, data in floor_summary.items():
            if data['total'] > 0:
                data['rate'] = round((data['occupied'] / data['total'] * 100), 1)
            else:
                data['rate'] = 0

        return floor_summary

    @staticmethod
    def convert_timestamp_to_time(timestamp):
        if not timestamp:
            return ""
        return datetime.fromtimestamp(timestamp / 1000).strftime('%H:%M')

    @staticmethod
    def get_seat_status_text(resv_status):
        status_map = {
            1027: "预约中",
            1093: "使用中"
        }
        return status_map.get(resv_status, "未知")
