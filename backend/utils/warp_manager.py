import subprocess
import logging
import time
import requests
from config import Config
logger = logging.getLogger(__name__)
class WarpManager:
    """WARP代理管理器 - 负责IP检测、更换和状态管理"""
    BLOCKED_STATUS_CODES = [403, 502, 503, 504]
    BLOCKED_KEYWORDS = [
        "blocked", "forbidden", "banned", "denied",
        "访问被拒绝", "IP被封", "请求被拒绝", "无法访问"
    ]
    MAX_RECONNECT_ATTEMPTS = 3
    RECONNECT_DELAY = 5  
    _last_ip = None
    _ip_change_count = 0
    @staticmethod
    def is_warp_enabled():
        """检查WARP是否启用"""
        return Config.USE_WARP_PROXY
    @staticmethod
    def get_current_warp_ip():
        """获取当前WARP代理的出口IP"""
        if not WarpManager.is_warp_enabled():
            return None
        try:
            proxy_url = Config.get_proxy_url()
            proxies = {'http': proxy_url, 'https': proxy_url}
            response = requests.get(
                'https://ifconfig.me',
                proxies=proxies,
                timeout=10,
                verify=False
            )
            if response.status_code == 200:
                ip = response.text.strip()
                WarpManager._last_ip = ip
                logger.info(f"当前WARP出口IP: {ip}")
                return ip
        except Exception as e:
            logger.error(f"获取WARP IP失败: {str(e)}")
        return None
    @staticmethod
    def get_real_server_ip():
        """获取服务器真实IP（不经过代理）"""
        try:
            response = requests.get(
                'https://ifconfig.me',
                timeout=10,
                verify=False
            )
            if response.status_code == 200:
                return response.text.strip()
        except Exception as e:
            logger.error(f"获取服务器真实IP失败: {str(e)}")
        return None
    @staticmethod
    def check_warp_status():
        """检查WARP连接状态"""
        try:
            result = subprocess.run(
                ['warp-cli', '--accept-tos', 'status'],
                capture_output=True,
                text=True,
                timeout=10
            )
            output = result.stdout
            is_connected = 'Connected' in output
            logger.info(f"WARP状态: {'已连接' if is_connected else '未连接'}")
            return {
                'connected': is_connected,
                'output': output,
                'error': result.stderr if result.stderr else None
            }
        except subprocess.TimeoutExpired:
            logger.error("检查WARP状态超时")
            return {'connected': False, 'error': 'timeout'}
        except FileNotFoundError:
            logger.error("warp-cli未安装")
            return {'connected': False, 'error': 'warp-cli not found'}
        except Exception as e:
            logger.error(f"检查WARP状态失败: {str(e)}")
            return {'connected': False, 'error': str(e)}
    @staticmethod
    def reconnect_warp():
        """断开并重新连接WARP以尝试更换IP"""
        logger.info("正在尝试重新连接WARP...")
        old_ip = WarpManager._last_ip
        try:
            subprocess.run(
                ['warp-cli', '--accept-tos', 'disconnect'],
                capture_output=True,
                timeout=10
            )
            logger.info("WARP已断开")
            time.sleep(2)
            subprocess.run(
                ['warp-cli', '--accept-tos', 'connect'],
                capture_output=True,
                timeout=10
            )
            logger.info("WARP重新连接命令已发送")
            time.sleep(3)
            new_ip = WarpManager.get_current_warp_ip()
            if new_ip and new_ip != old_ip:
                WarpManager._ip_change_count += 1
                logger.info(f"WARP IP已更换: {old_ip} -> {new_ip}")
                return True, new_ip, old_ip
            elif new_ip:
                logger.warning(f"WARP重连后IP未变化: {new_ip}")
                return True, new_ip, old_ip
            else:
                logger.error("WARP重连后无法获取IP")
                return False, None, old_ip
        except subprocess.TimeoutExpired:
            logger.error("WARP重连操作超时")
            return False, None, old_ip
        except FileNotFoundError:
            logger.error("warp-cli未安装，无法重连")
            return False, None, old_ip
        except Exception as e:
            logger.error(f"WARP重连失败: {str(e)}")
            return False, None, old_ip
    @staticmethod
    def is_ip_blocked_response(status_code=None, response_text=None, error_message=None):
        """判断响应是否表示IP被封"""
        if status_code in WarpManager.BLOCKED_STATUS_CODES:
            return True
        if response_text:
            text_lower = response_text.lower()
            for keyword in WarpManager.BLOCKED_KEYWORDS:
                if keyword.lower() in text_lower:
                    return True
        if error_message:
            error_lower = error_message.lower()
            for keyword in WarpManager.BLOCKED_KEYWORDS:
                if keyword.lower() in error_lower:
                    return True
        return False
    @staticmethod
    def handle_blocked_ip(status_code=None, response_text=None, error_message=None, context=""):
        """处理IP被封的情况：尝试重连并通知管理员"""
        if not WarpManager.is_warp_enabled():
            logger.warning("WARP未启用，无法处理IP封禁")
            return False
        logger.warning(f"检测到可能的IP封禁 - 上下文: {context}, 状态码: {status_code}")
        error_log = f"""
========== IP封禁警告 ==========
时间: {time.strftime('%Y-%m-%d %H:%M:%S')}
上下文: {context}
状态码: {status_code}
错误信息: {error_message or 'N/A'}
响应内容: {(response_text[:500] + '...') if response_text and len(response_text) > 500 else response_text or 'N/A'}
================================
"""
        logger.warning(error_log)
        success = False
        new_ip = None
        old_ip = None
        for attempt in range(1, WarpManager.MAX_RECONNECT_ATTEMPTS + 1):
            logger.info(f"尝试重连WARP (第{attempt}次)...")
            success, new_ip, old_ip = WarpManager.reconnect_warp()
            if success:
                break
            if attempt < WarpManager.MAX_RECONNECT_ATTEMPTS:
                logger.info(f"等待{WarpManager.RECONNECT_DELAY}秒后重试...")
                time.sleep(WarpManager.RECONNECT_DELAY)
        WarpManager._notify_admin_ip_issue(
            success=success,
            old_ip=old_ip,
            new_ip=new_ip,
            error_log=error_log,
            context=context
        )
        return success
    @staticmethod
    def _notify_admin_ip_issue(success, old_ip, new_ip, error_log, context):
        """通知管理员IP问题"""
        try:
            from models import User
            from utils.notification import NotificationService
            admins = User.query.filter_by(is_admin=True).all()
            if not admins:
                logger.warning("没有管理员用户，无法发送IP封禁通知")
                return
            if success:
                title = "⚠️ WARP IP已自动更换"
                message = f"""
检测到IP可能被封禁，系统已自动更换IP。

📍 触发场景: {context}
🔄 旧IP: {old_ip or '未知'}
✅ 新IP: {new_ip or '未知'}

详细日志:
{error_log}
"""
            else:
                title = "🚨 WARP IP更换失败"
                message = f"""
检测到IP可能被封禁，但自动更换IP失败，请手动处理！

📍 触发场景: {context}
❌ 当前IP: {old_ip or '未知'}

建议操作:
1. SSH登录服务器
2. 执行: warp-cli disconnect && warp-cli connect
3. 检查: warp-cli status

详细日志:
{error_log}
"""
            for admin in admins:
                if admin.notification_type and admin.notification_type != 'none' and admin.webhook_url:
                    try:
                        NotificationService.send_custom_notification(
                            admin,
                            title=title,
                            content=message
                        )
                        logger.info(f"已向管理员 {admin.username} 发送IP封禁通知")
                    except Exception as e:
                        logger.error(f"向管理员 {admin.username} 发送通知失败: {str(e)}")
        except Exception as e:
            logger.error(f"发送管理员IP封禁通知失败: {str(e)}")
    @staticmethod
    def get_stats():
        """获取WARP使用统计"""
        return {
            'enabled': WarpManager.is_warp_enabled(),
            'last_ip': WarpManager._last_ip,
            'ip_change_count': WarpManager._ip_change_count,
            'status': WarpManager.check_warp_status()
        }