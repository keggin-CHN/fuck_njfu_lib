"""
任务执行器 — 执行预约、取消、迟到保护等核心操作。
不依赖任何 ORM/数据库，直接调用图书馆 API。
复用 backend/utils/ 中的 LibraryAuthenticator 进行认证。
"""
import sys
import os
import datetime
import logging
import time

# 确保 server_api 目录优先，以便 config.py 不被 backend 的覆盖
_server_api_dir = os.path.dirname(os.path.abspath(__file__))
if _server_api_dir not in sys.path:
    sys.path.insert(0, _server_api_dir)

# 将 backend 目录加入路径，以便复用 auth_manager 等工具
_backend_dir = os.path.join(_server_api_dir, "..", "backend")
_backend_dir = os.path.abspath(_backend_dir)
if _backend_dir not in sys.path:
    sys.path.append(_backend_dir)  # append 而非 insert，让 server_api 优先

from utils.auth_manager import LibraryAuthenticator, HttpClient, check_ip_blocked, handle_ip_block
from utils.date_utils import get_today_date, get_tomorrow_date, normalize_time_format, is_friday, get_end_time
from config import Config

logger = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# 轻量用户对象 — 替代 Flask-SQLAlchemy 的 User model
# ---------------------------------------------------------------------------
class LightUser:
    """用于传递给 LibraryAuthenticator 等工具的最小用户对象。"""
    def __init__(self, username: str, edu_password: str, lib_password: str):
        self.id = username  # 用学号作为 id
        self.username = username
        self.edu_password = edu_password
        self.lib_password = lib_password
        self.notification_type = "none"
        self.webhook_url = None


# ---------------------------------------------------------------------------
# 认证
# ---------------------------------------------------------------------------
def authenticate(user: LightUser) -> LibraryAuthenticator | None:
    """对用户进行 CAS + 图书馆双重认证，返回 authenticator 或 None。"""
    try:
        auth = LibraryAuthenticator(user.username, user.edu_password, user.lib_password)
        if auth.authenticate():
            logger.info(f"用户 {user.username} 认证成功")
            return auth
        else:
            logger.error(f"用户 {user.username} 认证失败")
            return None
    except Exception as e:
        logger.error(f"用户 {user.username} 认证异常: {e}")
        return None


# ---------------------------------------------------------------------------
# 预约座位
# ---------------------------------------------------------------------------
def reserve_seat(
    user: LightUser,
    area: str,
    seat_number: int,
    start_time: str = None,
    end_time: str = None,
    date_str: str = None,
    authenticator: LibraryAuthenticator = None,
) -> tuple[bool, str]:
    """
    执行座位预约。

    Returns:
        (success: bool, message: str)
    """
    # 认证
    if not authenticator:
        authenticator = authenticate(user)
    if not authenticator:
        return False, "认证失败，请检查学号和密码"

    # 计算座位 ID
    seat_id = Config.get_seat_id(area, seat_number)
    if not seat_id:
        return False, f"座位配置无效: {area} {seat_number}号"

    # 默认日期和时间
    if not date_str:
        date_str = get_tomorrow_date()
    if not start_time:
        start_time = "08:00:00"
    start_time = normalize_time_format(start_time)

    if not end_time:
        end_time = get_end_time(date_str)
    end_time = normalize_time_format(end_time)

    # 检查时长
    try:
        begin_obj = datetime.datetime.strptime(start_time, "%H:%M:%S")
        end_obj = datetime.datetime.strptime(end_time, "%H:%M:%S")
        duration = (end_obj - begin_obj).seconds / 3600
        if duration < 2:
            return False, f"预约时长不足2小时（{start_time} ~ {end_time}）"
    except ValueError as e:
        return False, f"时间格式错误: {e}"

    begin_time = f"{date_str} {start_time}"
    full_end_time = f"{date_str} {end_time}"

    return _do_reserve(user, authenticator, area, seat_number, seat_id,
                       begin_time, full_end_time, date_str, start_time, end_time)


def _do_reserve(
    user: LightUser,
    authenticator: LibraryAuthenticator,
    area: str,
    seat_number: int,
    seat_id: int,
    begin_time: str,
    full_end_time: str,
    date_str: str,
    start_time: str,
    end_time: str,
) -> tuple[bool, str]:
    """实际发送预约请求。"""
    reserve_url = HttpClient.get_lib_url("ic-web/reserve?vpn-12-libseat.njfu.edu.cn")
    api_headers = {
        "content-type": "application/json;charset=UTF-8",
        "token": authenticator.token,
        "lan": "1",
    }
    payload = {
        "sysKind": 8,
        "appAccNo": authenticator.acc_no,
        "memberKind": 1,
        "resvMember": [authenticator.acc_no],
        "resvBeginTime": begin_time,
        "resvEndTime": full_end_time,
        "resvDev": [seat_id],
        "resvProperty": 0,
        "memo": "",
        "captcha": "",
        "testName": "",
    }

    logger.info(f"用户 {user.username} 开始预约: {area} {seat_number}号, "
                f"{date_str} {start_time}-{end_time}")

    max_retries = 2
    for retry in range(max_retries):
        try:
            response = authenticator.session.post(reserve_url, headers=api_headers, json=payload)

            if check_ip_blocked(response, "座位预约"):
                if handle_ip_block(response, "座位预约"):
                    time.sleep(2)
                    continue

            if response and response.status_code == 200:
                result = response.json()
                if result.get("code") == 0:
                    msg = f"预约成功: {result.get('message', '操作成功')}"
                    logger.info(f"用户 {user.username} {msg}")
                    uuid = result.get("data", {}).get("uuid") if "data" in result else None
                    return True, msg
                else:
                    error_msg = result.get("message", "未知错误")
                    # 尝试刷新认证后重试
                    if any(kw in error_msg for kw in ("登录", "Token", "认证")) and retry == 0:
                        logger.warning(f"认证错误，尝试刷新: {error_msg}")
                        new_auth = authenticate(user)
                        if new_auth:
                            authenticator = new_auth
                            api_headers["token"] = authenticator.token
                            continue
                    msg = f"预约失败: {error_msg}"
                    logger.error(f"用户 {user.username} {msg}")
                    return False, msg
            else:
                status = response.status_code if response else "请求失败"
                msg = f"预约请求失败，状态码: {status}"
                logger.error(f"用户 {user.username} {msg}")
                return False, msg
        except Exception as e:
            logger.error(f"用户 {user.username} 预约异常: {e}")
            if retry < max_retries - 1:
                time.sleep(2)
                continue
            return False, f"预约过程出错: {e}"

    return False, "预约失败: 已达最大重试次数"


# ---------------------------------------------------------------------------
# 查询预约
# ---------------------------------------------------------------------------
def get_reservations(
    user: LightUser,
    begin_date: str = None,
    end_date: str = None,
    authenticator: LibraryAuthenticator = None,
) -> list | None:
    """查询预约列表。返回 list 或 None（认证失败）。"""
    if not authenticator:
        authenticator = authenticate(user)
    if not authenticator:
        return None

    if not begin_date:
        begin_date = get_today_date()
    if not end_date:
        end_date = begin_date

    url = HttpClient.get_lib_url("ic-web/reserve/resvInfo")
    params = {
        "vpn-12-libseat.njfu.edu.cn": "",
        "needStatus": "8454",
        "unneedStatus": "128",
        "beginDate": begin_date,
        "endDate": end_date,
    }
    api_headers = {
        "token": authenticator.token,
        "lan": "1",
    }

    max_retries = 2
    for retry in range(max_retries):
        try:
            response = authenticator.session.get(url, headers=api_headers, params=params)
            if check_ip_blocked(response, "获取预约信息"):
                if handle_ip_block(response, "获取预约信息"):
                    time.sleep(2)
                    continue
            if response and response.status_code == 200:
                result = response.json()
                if result.get("code") == 0:
                    return result.get("data", [])
                else:
                    logger.error(f"获取预约信息失败: {result.get('message')}")
            else:
                logger.error(f"获取预约请求失败: {response.status_code if response else 'N/A'}")
        except Exception as e:
            logger.error(f"获取预约信息异常: {e}")
            if retry < max_retries - 1:
                time.sleep(2)
                continue
    return []


# ---------------------------------------------------------------------------
# 取消预约
# ---------------------------------------------------------------------------
def cancel_reservation(
    user: LightUser,
    uuid: str,
    authenticator: LibraryAuthenticator = None,
) -> tuple[bool, str]:
    """取消指定预约。"""
    if not uuid:
        return False, "没有提供预约UUID"

    if not authenticator:
        authenticator = authenticate(user)
    if not authenticator:
        return False, "认证失败"

    url = HttpClient.get_lib_url("ic-web/reserve/delete")
    params = {"vpn-12-libseat.njfu.edu.cn": ""}
    api_headers = {
        "content-type": "application/json;charset=UTF-8",
        "token": authenticator.token,
        "lan": "1",
    }
    payload = {"uuid": uuid}

    max_retries = 2
    for retry in range(max_retries):
        try:
            response = authenticator.session.post(url, headers=api_headers, params=params, json=payload)
            if check_ip_blocked(response, "取消预约"):
                if handle_ip_block(response, "取消预约"):
                    time.sleep(2)
                    continue
            if response and response.status_code == 200:
                result = response.json()
                if result.get("code") == 0:
                    logger.info(f"用户 {user.username} 取消预约成功")
                    return True, "取消预约成功"
                else:
                    msg = f"取消预约失败: {result.get('message', '未知错误')}"
                    logger.error(msg)
                    return False, msg
            else:
                msg = f"取消请求失败: {response.status_code if response else 'N/A'}"
                logger.error(msg)
                return False, msg
        except Exception as e:
            logger.error(f"取消预约异常: {e}")
            if retry < max_retries - 1:
                time.sleep(2)
                continue
            return False, f"取消过程出错: {e}"
    return False, "取消失败: 已达最大重试次数"


# ---------------------------------------------------------------------------
# 迟到保护
# ---------------------------------------------------------------------------
def execute_late_protection(
    user: LightUser,
    area: str,
    seat_number: int,
    authenticator: LibraryAuthenticator = None,
) -> tuple[bool, str]:
    """
    迟到保护：检查今日预约，如果即将迟到（开始前20分钟内未签到），
    则取消当前预约并重新预约一个晚1小时的时段。
    """
    if not authenticator:
        authenticator = authenticate(user)
    if not authenticator:
        return False, "认证失败"

    today = get_today_date()
    reservations = get_reservations(user, begin_date=today, end_date=today, authenticator=authenticator)

    if reservations is None:
        # 尝试重新认证
        authenticator = authenticate(user)
        if not authenticator:
            return False, "认证失败"
        reservations = get_reservations(user, begin_date=today, end_date=today, authenticator=authenticator)

    if not reservations:
        return True, "今日没有预约，无需迟到保护"

    now = datetime.datetime.now()
    resv = reservations[0]
    resv_begin_ms = resv.get("resvBeginTime")
    if not resv_begin_ms:
        return False, "无法获取预约开始时间"

    resv_begin = datetime.datetime.fromtimestamp(resv_begin_ms / 1000)
    diff_minutes = (resv_begin - now).total_seconds() / 60

    # 只在开始前 19~21 分钟范围内执行
    if not (0 <= diff_minutes <= 25):
        return True, f"预约时间距当前 {diff_minutes:.0f} 分钟，不在检查范围"

    # 检查是否已签到
    status_name = resv.get("statusName", "")
    if status_name and ("使用" in status_name or "签到" in status_name):
        return True, f"已签到/使用中({status_name})，跳过"

    uuid = resv.get("uuid")
    if not uuid:
        return False, "无法获取预约 UUID"

    # 取消当前预约
    cancel_ok, cancel_msg = cancel_reservation(user, uuid, authenticator=authenticator)
    if not cancel_ok:
        # 重新认证后再试
        authenticator = authenticate(user)
        if authenticator:
            cancel_ok, cancel_msg = cancel_reservation(user, uuid, authenticator=authenticator)
        if not cancel_ok:
            return False, f"取消预约失败: {cancel_msg}"

    logger.info(f"迟到保护: 已取消用户 {user.username} 的预约 {uuid}")

    # 重新预约（晚1小时）
    new_start = (resv_begin + datetime.timedelta(hours=1)).strftime("%H:%M:%S")
    end_time = get_end_time(today)
    new_start_obj = datetime.datetime.strptime(new_start, "%H:%M:%S")
    end_time_obj = datetime.datetime.strptime(end_time, "%H:%M:%S")
    remaining_hours = (end_time_obj - new_start_obj).seconds / 3600

    if remaining_hours < 2:
        return True, f"剩余时间不足2小时({remaining_hours:.1f}h)，不再重新预约"

    seat_id = Config.get_seat_id(area, seat_number)
    if not seat_id:
        return False, f"座位配置无效: {area} {seat_number}号"

    reserve_ok, reserve_msg = reserve_seat(
        user, area, seat_number,
        start_time=new_start,
        end_time=end_time,
        date_str=today,
        authenticator=authenticator,
    )

    if reserve_ok:
        logger.info(f"迟到保护: 用户 {user.username} 重新预约成功 {new_start}")
        return True, f"迟到保护完成: 取消旧预约，重新预约 {new_start}"
    else:
        return False, f"迟到保护: 取消成功但重新预约失败: {reserve_msg}"
