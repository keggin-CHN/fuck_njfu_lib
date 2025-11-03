import requests
import logging
from bs4 import BeautifulSoup
from models import db, Traffic, User
from datetime import datetime

logger = logging.getLogger(__name__)

class LibraryTrafficMonitor:

    TRAFFIC_URL = "https://webvpn.njfu.edu.cn/webvpn/LjIwMS4xNjkuMjE4LjE2OA==/LjE0Ny4xMDEuMTUyLjEwMi4xMDEuMTAyLjE1Ny45Ny4xNTEuOTkuMTA0LjEwMi4xNTIuMTEyLjExMS4xNTM=/book/view"
    REQUEST_TIMEOUT = 15

    @staticmethod
    def get_current_traffic():

        try:
            from utils.auth_manager import AuthManager

            user = User.query.filter_by(is_admin=True).first()
            if not user:
                user = User.query.first()

            if not user:
                logger.error("流量监控：系统中没有任何用户，无法执行流量采集")
                return None

            logger.info(f"流量监控：使用用户 {user.username} (管理员: {user.is_admin}) 进行认证")

            authenticator = AuthManager.get_authenticator(user)
            if not authenticator or not authenticator.my_client_ticket:
                logger.error(f"流量监控：无法获取用户 {user.username} 的有效认证")
                return None

            cookies = {"my_client_ticket": authenticator.my_client_ticket}
            headers = {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36"
            }

            response = requests.get(
                LibraryTrafficMonitor.TRAFFIC_URL,
                cookies=cookies,
                headers=headers,
                timeout=LibraryTrafficMonitor.REQUEST_TIMEOUT
            )

            if response.status_code != 200:
                logger.error(f"流量监控：请求失败，状态码 {response.status_code}")
                return None

            soup = BeautifulSoup(response.text, "html.parser")
            logger.debug(f"流量监控：成功获取页面内容, HTML长度: {len(response.text)}")

            all_spans = soup.find_all("span", style=lambda value: value and "font-size:20px" in value)
            logger.debug(f"流量监控：找到 {len(all_spans)} 个匹配的 <span> 标签")

            if len(all_spans) < 1:
                logger.error(f"流量监控：页面结构解析失败，未找到匹配的 span。页面内容: {response.text[:500]}")
                return None

            import re
            nums = []
            try:
                for sp in all_spans:
                    txt = (sp.text or "").strip()
                    found = re.findall(r'\d+', txt)
                    if found:
                        nums.append(''.join(found))

                if len(nums) >= 2:
                    num1 = int(nums[0])
                    num2 = int(nums[1])
                else:

                    all_nums = re.findall(r'\d+', response.text)
                    if len(all_nums) >= 2:
                        num1 = int(all_nums[0])
                        num2 = int(all_nums[1])
                    else:
                        logger.error("流量监控：未能从页面中解析到足够的数字信息")
                        return None

                total_seats = max(num1, num2)
                remaining_seats = min(num1, num2)

                count = total_seats - remaining_seats

                logger.debug(f"流量监控：解析到在馆人数: {count}, 总座位: {total_seats}, 剩余座位: {remaining_seats}")
            except Exception as e:
                logger.error(f"流量监控：解析数字失败 - {e}", exc_info=True)
                return None

            logger.info(f"流量监控：当前在馆人数 {count}/{total_seats} (剩余{remaining_seats})")
            return count

        except ValueError as e:
            logger.error(f"流量监控：数据解析失败 - {e}")
            return None
        except Exception as e:
            logger.error(f"流量监控：获取流量失败 - {e}", exc_info=True)
            return None

    @staticmethod
    def save_traffic_data(count):

        try:
            if count is None:
                return False

            timestamp = int(datetime.now().timestamp())

            existing = Traffic.query.filter_by(timestamp=timestamp).first()
            if existing:
                logger.debug(f"流量监控：时间戳 {timestamp} 的数据已存在，跳过")
                return False

            traffic = Traffic(timestamp=timestamp, count=count)
            db.session.add(traffic)
            db.session.commit()

            logger.info(f"流量监控：数据已保存 - 时间戳={timestamp}, 人数={count}")
            return True

        except Exception as e:
            logger.error(f"流量监控：数据库操作失败 - {e}")
            db.session.rollback()
            return False

    @staticmethod
    def collect_and_save():

        count = LibraryTrafficMonitor.get_current_traffic()
        if count is not None:
            return LibraryTrafficMonitor.save_traffic_data(count)
        return False

    @staticmethod
    def cleanup_old_data(days=7):

        try:
            Traffic.cleanup_old_data(days)
            logger.info(f"流量监控：已清理 {days} 天前的旧数据")
        except Exception as e:
            logger.error(f"流量监控：清理旧数据失败 - {e}")
