import requests
import logging
from bs4 import BeautifulSoup
from models import db, Traffic, User
from datetime import datetime

logger = logging.getLogger(__name__)


class LibraryTrafficMonitor:
    """图书馆流量监控器"""

    # 图书馆流量监控页面的webvpn URL
    # 注意：这个URL是通过webvpn访问http://202.119.210.2:85/book/view的编码URL
    TRAFFIC_URL = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OA==/LjE0Ny4xMDEuMTUyLjEwMi4xMDEuMTAyLjE1Ny45Ny4xNTEuOTkuMTA0LjEwMi4xNTIuMTEyLjExMS4xNTM=/book/view"
    REQUEST_TIMEOUT = 15

    @staticmethod
    def get_current_traffic():
        """获取当前在馆人数（使用已认证的用户）"""
        try:
            from utils.auth_manager import AuthManager

            # 优先使用管理员账户进行认证
            user = User.query.filter_by(is_admin=True).first()
            if not user:
                user = User.query.first()
            
            if not user:
                logger.error("流量监控：系统中没有任何用户，无法执行流量采集")
                return None
            
            logger.info(f"流量监控：使用用户 {user.username} (管理员: {user.is_admin}) 进行认证")

            # 获取认证器
            authenticator = AuthManager.get_authenticator(user)
            if not authenticator or not authenticator.my_client_ticket:
                logger.error(f"流量监控：无法获取用户 {user.username} 的有效认证")
                return None

            # 准备请求
            cookies = {"my_client_ticket": authenticator.my_client_ticket}
            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            }

            # 发起请求
            response = requests.get(
                LibraryTrafficMonitor.TRAFFIC_URL,
                cookies=cookies,
                headers=headers,
                timeout=LibraryTrafficMonitor.REQUEST_TIMEOUT
            )

            if response.status_code != 200:
                logger.error(f"流量监控：请求失败，状态码 {response.status_code}")
                return None

            # 解析页面
            soup = BeautifulSoup(response.text, "html.parser")
            logger.debug(f"流量监控：成功获取页面内容, HTML长度: {len(response.text)}")

            # 查找包含数字的span标签
            # 页面结构: <span style="font-size:20px; color:#000;">3937</span><br/><span>剩余可用</span>
            all_spans = soup.find_all("span", style=lambda value: value and "font-size:20px" in value)
            logger.debug(f"流量监控：找到 {len(all_spans)} 个匹配的 <span> 标签")

            if len(all_spans) < 1:
                logger.error(f"流量监控：页面结构解析失败，未找到匹配的 span。页面内容: {response.text[:500]}")
                return None

            # 尝试从匹配到的 <span> 列表中提取数字（优先）
            import re
            nums = []
            try:
                for sp in all_spans:
                    txt = (sp.text or "").strip()
                    found = re.findall(r'\d+', txt)
                    if found:
                        nums.append(''.join(found))

                # 如果从 span 中找到了至少两个数字，直接使用
                if len(nums) >= 2:
                    num1 = int(nums[0])
                    num2 = int(nums[1])
                else:
                    # 回退：在整个 HTML 中查找数字（更宽容）
                    all_nums = re.findall(r'\d+', response.text)
                    if len(all_nums) >= 2:
                        num1 = int(all_nums[0])
                        num2 = int(all_nums[1])
                    else:
                        logger.error("流量监控：未能从页面中解析到足够的数字信息")
                        return None
                
                # 总座位数应该是较大的数字，剩余座位数是较小的数字
                total_seats = max(num1, num2)
                remaining_seats = min(num1, num2)

                # 在馆人数 = 总座位 - 剩余座位
                count = total_seats - remaining_seats

                logger.debug(f"流量监控：解析到在馆人数: {count}, 总座位: {total_seats}, 剩余座位: {remaining_seats}")
            except Exception as e:
                logger.error(f"流量监控：解析数字失败 - {e}", exc_info=True)
                return None

            logger.info(f"流量监控：当前在馆人数 {count}/{total_seats} (剩余{remaining_seats})")
            return {
                'count': count,
                'total': total_seats,
                'remaining': remaining_seats
            }

        except ValueError as e:
            logger.error(f"流量监控：数据解析失败 - {e}")
            return None
        except Exception as e:
            logger.error(f"流量监控：获取流量失败 - {e}", exc_info=True)
            return None

    @staticmethod
    def save_traffic_data(traffic_info):
        """保存流量数据到数据库"""
        try:
            if not traffic_info:
                return False

            count = traffic_info['count']
            timestamp = int(datetime.now().timestamp())

            # 检查是否已存在相同时间戳的记录
            existing = Traffic.query.filter_by(timestamp=timestamp).first()
            if existing:
                logger.debug(f"流量监控：时间戳 {timestamp} 的数据已存在，跳过")
                return False

            traffic = Traffic(timestamp=timestamp, count=count)
            db.session.add(traffic)
            db.session.commit()

            logger.info(f"流量监控：数据已保存 - 时间戳={timestamp}, 人数={count}/{traffic_info['total']}")
            return True

        except Exception as e:
            logger.error(f"流量监控：数据库操作失败 - {e}")
            db.session.rollback()
            return False

    @staticmethod
    def collect_and_save():
        """采集并保存流量数据"""
        traffic_info = LibraryTrafficMonitor.get_current_traffic()
        if traffic_info:
            return LibraryTrafficMonitor.save_traffic_data(traffic_info)
        return False

    @staticmethod
    def cleanup_old_data(days=7):
        """清理旧数据"""
        try:
            Traffic.cleanup_old_data(days)
            logger.info(f"流量监控：已清理 {days} 天前的旧数据")
        except Exception as e:
            logger.error(f"流量监控：清理旧数据失败 - {e}")
