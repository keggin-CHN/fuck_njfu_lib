import requests
import logging
from datetime import datetime
from models import ReservationHistory
from utils.wenxin_service import HitokotoService

logger = logging.getLogger(__name__)


class NotificationService:
    """通知推送服务"""

    @staticmethod
    def send_wechat_notification(webhook_url, user, reservation_info):
        """发送企业微信通知"""
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
        """构建单次预约结果的通知消息内容"""
        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        greeting = HitokotoService.generate_greeting()

        if history.status == ReservationHistory.STATUS_SUCCESS:
            title = "✅ 预约成功"
            details = f"> **日期**: {history.reserve_date.strftime('%Y-%m-%d')}\n" \
                      f"> **座位**: {history.area} {history.seat_number}号\n" \
                      f"> **时间**: {history.start_time} - {history.end_time}"
        else:
            title = f"❌ 预约失败"
            details = f"> **日期**: {history.reserve_date.strftime('%Y-%m-%d')}\n" \
                      f"> **座位**: {history.area} {history.seat_number}号\n" \
                      f"> **时间**: {history.start_time} - {history.end_time}\n" \
                      f"> **原因**: {history.message}"

        if format_type == 'wechat':
            return f"""## 图书馆预约结果通知
> **用户**: {user.username}
> **时间**: {now}
### {greeting}

### {title}
{details}
"""
        else:  # Telegram
            return f"""📚 *图书馆预约结果通知*
👤 用户: `{user.username}`
🕒 时间: {now}

{greeting}

*{title}*
{details.replace('> **', '• *').replace('**: ', '*: ').replace('\\n', '\\n')}
"""
    
    @staticmethod
    def send_single_reservation_notification(user, history):
        """根据用户配置发送单次预约结果通知"""
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
        if user.notification_type == 'none' or not user.webhook_url:
            return False, "用户未配置通知"

        now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')
        greeting = HitokotoService.generate_greeting()
        
        auto_reserve_status = "✅ 开启" if setting.auto_reserve else "❌ 关闭"
        prevent_late_status = "✅ 开启" if setting.prevent_late else "❌ 关闭"

        title = "⚙️ 预约设置更新"
        details = (
            f"> **座位**: {setting.area} {setting.seat_number}号\n"
            f"> **时间**: {setting.start_time} - {setting.end_time}\n"
            f"> **自动预约**: {auto_reserve_status}\n"
            f"> **迟到保护**: {prevent_late_status}"
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
            return True, "发送成功"
        except requests.RequestException as e:
            logger.error(f"向用户 {user.username} 发送设置更新通知失败: {str(e)}")
            return False, str(e)
