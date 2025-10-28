import requests
import logging
from datetime import datetime, timedelta
from models import ReservationHistory
from utils.wenxin_service import HitokotoService

logger = logging.getLogger(__name__)

last_notification_time = {}  # 用于存储每个用户最后一次发送通知的时间


class NotificationService:
    """通知推送服务"""

    NOTIFICATION_INTERVAL = 20  # 20秒的推送间隔

    @staticmethod
    def send_wechat_notification(webhook_url, user, reservation_info):
        """发送企业微信通知"""
        now = datetime.now()
        last_time = last_notification_time.get(user.id)
        if last_time and now - last_time < timedelta(seconds=NotificationService.NOTIFICATION_INTERVAL):
            logger.info(f"用户 {user.username}  {NotificationService.NOTIFICATION_INTERVAL}秒内已发送过通知，本次取消发送")
            return False, "发送过于频繁，已取消"

        try:
            # 构建消息内容
            message = NotificationService._build_message(user, reservation_info)

            # 企业微信机器人消息格式
            data = {
                "msgtype": "markdown",
                "markdown": {
                    "content": message
                }
            }

            response = requests.post(webhook_url, json=data, timeout=5)
            response.raise_for_status()

            result = response.json()
            if result.get('errcode') == 0:
                logger.info(f"企业微信通知发送成功: 用户 {user.username}")
                last_notification_time[user.id] = now  # 更新发送时间
                return True, "发送成功"
            else:
                error_msg = result.get('errmsg', '未知错误')
                logger.error(f"企业微信通知发送失败: {error_msg}")
                return False, error_msg

        except requests.RequestException as e:
            logger.error(f"企业微信通知发送失败: {str(e)}")
            return False, str(e)

    @staticmethod
    def send_telegram_notification(webhook_url, user, reservation_info):
        """发送Telegram通知"""
        now = datetime.now()
        last_time = last_notification_time.get(user.id)
        if last_time and now - last_time < timedelta(seconds=NotificationService.NOTIFICATION_INTERVAL):
            logger.info(f"用户 {user.username}  {NotificationService.NOTIFICATION_INTERVAL}秒内已发送过通知，本次取消发送")
            return False, "发送过于频繁，已取消"
        try:
            # 构建消息内容
            message = NotificationService._build_message(user, reservation_info, format_type='telegram')

            # Telegram Bot消息格式
            # webhook_url 格式应该是: https://api.telegram.org/bot<TOKEN>/sendMessage?chat_id=<CHAT_ID>
            data = {
                "text": message,
                "parse_mode": "Markdown",
                "disable_web_page_preview": True
            }

            response = requests.post(webhook_url, json=data, timeout=5)
            response.raise_for_status()

            result = response.json()
            if result.get('ok'):
                logger.info(f"Telegram通知发送成功: 用户 {user.username}")
                last_notification_time[user.id] = now  # 更新发送时间
                return True, "发送成功"
            else:
                error_msg = result.get('description', '未知错误')
                logger.error(f"Telegram通知发送失败: {error_msg}")
                return False, error_msg

        except requests.RequestException as e:
            logger.error(f"Telegram通知发送失败: {str(e)}")
            return False, str(e)

    @staticmethod
    def _build_message(user, history, format_type='wechat'):
        """构建单次预约结果的通知消息内容（区分迟到保护与自动寻座）"""
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        greeting = HitokotoService.generate_greeting()
        
        # 标记类型
        is_late = getattr(history, 'is_late_protection', False)
        is_auto = getattr(history, 'is_auto_find', False)
        
        # 构造标题前缀
        prefixes = []
        if is_late:
            prefixes.append("迟到保护")
        if is_auto:
            prefixes.append("自动寻座")
        
        # 基础标题（按状态）
        if history.status == ReservationHistory.STATUS_SUCCESS:
            base_title = "✅ 预约成功"
        elif history.status == ReservationHistory.STATUS_AUTH_FAILED:
            base_title = "❌ 认证失败"
        elif hasattr(ReservationHistory, 'STATUS_CANCELED') and history.status == ReservationHistory.STATUS_CANCELED:
            base_title = "⚠️ 预约取消"
        else:
            base_title = "❌ 预约失败"
        
        title = base_title if not prefixes else f"{base_title[0:1]} {' · '.join(prefixes)} · {base_title[2:]}"
        
        # 详情内容，增加类型说明
        extra = ""
        if is_late:
            extra += "> **类型**: 迟到保护\n> **说明**: 到达开始时间前未签到，系统自动取消并顺延重新预约\n"
        if is_auto:
            extra += "> **类型**: 自动寻座\n> **说明**: 初选座位已占用，系统推荐/自动分配可用座位完成预约\n"
        
        # 处理可能为 None 的开始/结束时间
        start_time_val = getattr(history, 'start_time', '') or ''
        end_time_val = getattr(history, 'end_time', '') or ''
        # 如果是 datetime/time 对象，格式化为字符串
        try:
            if hasattr(start_time_val, 'strftime'):
                start_time_val = start_time_val.strftime('%H:%M:%S')
        except Exception:
            pass
        try:
            if hasattr(end_time_val, 'strftime'):
                end_time_val = end_time_val.strftime('%H:%M:%S')
        except Exception:
            pass

        if not start_time_val:
            start_time_val = '--:--'
        if not end_time_val:
            end_time_val = '--:--'

        base_details = (
            f"> **日期**: {history.reserve_date.strftime('%Y-%m-%d')}\n"
            f"> **座位**: {history.area} {history.seat_number}号\n"
            f"> **时间**: {start_time_val} - {end_time_val}\n"
        )
        
        if history.status == ReservationHistory.STATUS_SUCCESS:
            details = extra + base_details
        else:
            reason = history.message or "未知原因"
            details = extra + base_details + f"> **原因**: {reason}"

        if format_type == 'wechat':
            return f"""## 图书馆预约结果通知
> **用户**: {user.username}
> **时间**: {now}
### {greeting}

### {title}
{details}
"""
        else:  # Telegram
            # 将详情适配 Telegram Markdown
            tg_details = details.replace('> **', '• *').replace('**: ', '*: ').replace('\\n', '\\n')
            return f"""📚 *图书馆预约结果通知*
👤 用户: `{user.username}`
🕒 时间: {now}

{greeting}

*{title}*
{tg_details}
"""
    
    @staticmethod
    def send_single_reservation_notification(user, history):
        """根据用户配置发送单次预约结果通知"""
        now = datetime.now()
        last_time = last_notification_time.get(user.id)
        if last_time and now - last_time < timedelta(seconds=NotificationService.NOTIFICATION_INTERVAL):
            logger.info(f"用户 {user.username}  {NotificationService.NOTIFICATION_INTERVAL}秒内已发送过通知，本次取消发送")
            return False, "发送过于频繁，已取消"

        if user.notification_type == 'none' or not user.webhook_url:
            return False, "用户未配置通知"

        if user.notification_type == 'wechat':
            message = NotificationService._build_message(user, history, 'wechat')
            data = {"msgtype": "markdown", "markdown": {"content": message}}
        elif user.notification_type == 'telegram':
            message = NotificationService._build_message(user, history, 'telegram')
            data = {"text": message, "parse_mode": "Markdown", "disable_web_page_preview": True}
        else:
            logger.warning(f"未知的通知类型: {user.notification_type}")
            return False, "未知的通知类型"
            
        try:
            response = requests.post(user.webhook_url, json=data, timeout=10)
            response.raise_for_status()
            result = response.json()

            is_success = (user.notification_type == 'wechat' and result.get('errcode') == 0) or \
                         (user.notification_type == 'telegram' and result.get('ok'))
            
            if is_success:
                logger.info(f"向用户 {user.username} 发送预约通知成功")
                last_notification_time[user.id] = now  # 更新发送时间
                return True, "发送成功"
            else:
                error_msg = result.get('errmsg') or result.get('description', '未知错误')
                logger.error(f"向用户 {user.username} 发送通知失败: {error_msg}")
                return False, error_msg
        except requests.RequestException as e:
            logger.error(f"向用户 {user.username} 发送通知请求失败: {str(e)}")
            return False, str(e)

    @staticmethod
    def test_notification(user):
        """发送测试通知"""
        # 使用 ReservationHistory 模拟一个成功的预约对象
        history = ReservationHistory(
            status=ReservationHistory.STATUS_SUCCESS,
            reserve_date=datetime.now().date(),
            area='测试区域',
            seat_number=1,
            start_time='09:00:00',
            end_time='22:00:00',
            message='这是一条测试消息'
        )
        
        # 直接复用单次预约通知的逻辑
        return NotificationService.send_single_reservation_notification(user, history)

    @staticmethod
    def send_setting_update_notification(user, setting):
        """发送预约设置更新通知"""
        now = datetime.now()
        last_time = last_notification_time.get(user.id)
        if last_time and now - last_time < timedelta(seconds=NotificationService.NOTIFICATION_INTERVAL):
            logger.info(f"用户 {user.username}  {NotificationService.NOTIFICATION_INTERVAL}秒内已发送过通知，本次取消发送")
            return False, "发送过于频繁，已取消"

        if user.notification_type == 'none' or not user.webhook_url:
            return False, "用户未配置通知"

        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        greeting = HitokotoService.generate_greeting()
        
        auto_reserve_status = "✅ 开启" if setting.auto_reserve else "❌ 关闭"
        prevent_late_status = "✅ 开启" if setting.prevent_late else "❌ 关闭"
        auto_find_seat_status = "✅ 开启" if getattr(setting, 'auto_find_seat', False) else "❌ 关闭"

        title = "⚙️ 预约设置更新"
        details = (
            f"> **座位**: {setting.area} {setting.seat_number}号\n"
            f"> **时间**: {setting.start_time} - {setting.end_time}\n"
            f"> **自动预约**: {auto_reserve_status}\n"
            f"> **迟到保护**: {prevent_late_status}\n"
            f"> **自动寻座**: {auto_find_seat_status}"
        )
        
        if user.notification_type == 'wechat':
            message = f"""## 图书馆预约设置更新通知
> **用户**: {user.username}
> **时间**: {now}
### {greeting}

### {title}
{details}
"""
            data = {"msgtype": "markdown", "markdown": {"content": message}}
        elif user.notification_type == 'telegram':
            message = f"""📚 *图书馆预约设置更新通知*
👤 用户: `{user.username}`
🕒 时间: {now}

{greeting}

*{title}*
{details.replace('> **', '• *').replace('**: ', '*: ').replace('\\n', '\\n')}
"""
            data = {"text": message, "parse_mode": "Markdown", "disable_web_page_preview": True}
        else:
            return False, "未知的通知类型"

        try:
            response = requests.post(user.webhook_url, json=data, timeout=10)
            response.raise_for_status()
            logger.info(f"向用户 {user.username} 发送设置更新通知成功")
            last_notification_time[user.id] = now  # 更新发送时间
            return True, "发送成功"
        except requests.RequestException as e:
            logger.error(f"向用户 {user.username} 发送设置更新通知失败: {str(e)}")
            return False, str(e)
