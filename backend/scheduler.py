import datetime
import logging
import atexit
import functools
from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
from models import db, User, ReservationSetting, ReservationHistory, Traffic
from utils.auth_manager import AuthManager, handle_exception
from utils.reservation import SeatReservation
from utils.date_utils import get_today_date, get_tomorrow_date
from utils.traffic_monitor import LibraryTrafficMonitor
from utils.notification import NotificationService
from config import Config

logger = logging.getLogger(__name__)

scheduler = BackgroundScheduler(timezone=Config.TIMEZONE)
scheduler_initialized = False


# 装饰器：在应用上下文中执行函数
def with_app_context(func):
    @functools.wraps(func)
    def wrapper(*args, **kwargs):
        with scheduler.app.app_context():
            return func(*args, **kwargs)

    return wrapper


def get_users_with_setting(auto_reserve=None, prevent_late=None, is_admin=None):
    """获取符合条件的用户列表"""
    query = db.session.query(User).join(ReservationSetting)
    if auto_reserve is not None:
        query = query.filter(ReservationSetting.auto_reserve == auto_reserve)
    if prevent_late is not None:
        query = query.filter(ReservationSetting.prevent_late == prevent_late)
    if is_admin is not None:
        query = query.filter(User.is_admin == is_admin)
    return query.all()


def setup_scheduler(app):
    """初始化任务调度器"""
    global scheduler_initialized
    if scheduler_initialized:
        return

    scheduler.app = app
    with app.app_context():
        if not scheduler.running:
            logger.info("清除所有旧的定时任务...")
            scheduler.remove_all_jobs()
            logger.info("旧任务已清除，开始添加新任务...")
            add_scheduler_jobs()
            scheduler.start()
            atexit.register(lambda: scheduler.shutdown(wait=False))
            scheduler_initialized = True
            check_late_protection_for_all_users()
            log_scheduled_jobs()


def add_scheduler_jobs():
    """添加定时任务"""
    # 每日认证任务
    auth_time = Config.AUTH_TIME.split(':')
    scheduler.add_job(
        func=auth_all_users,
        trigger=CronTrigger(hour=auth_time[0], minute=auth_time[1]),
        id="auth_all_users",
        name="Authenticate all users daily",
        replace_existing=True,
        coalesce=True,
        max_instances=1
    )

    # 管理员预约任务
    admin_reserve_time = Config.ADMIN_RESERVE_TIME.split(':')
    scheduler.add_job(
        func=reserve_for_users,
        trigger=CronTrigger(hour=admin_reserve_time[0], minute=admin_reserve_time[1]),
        id="reserve_for_admins",
        name="Reserve seats for admin users daily",
        kwargs={"admin_only": True},
        replace_existing=True,
        coalesce=True,
        max_instances=1
    )

    # 普通用户预约任务
    user_reserve_time = Config.USER_RESERVE_TIME.split(':')
    scheduler.add_job(
        func=reserve_for_users,
        trigger=CronTrigger(hour=user_reserve_time[0], minute=user_reserve_time[1]),
        id="reserve_for_normal_users",
        name="Reserve seats for normal users daily",
        kwargs={"admin_only": False},
        replace_existing=True,
        coalesce=True,
        max_instances=1
    )

    # 迟到保护检查任务
    scheduler.add_job(
        func=check_late_protection_for_all_users,
        trigger=CronTrigger(hour="7", minute="10"),
        id="check_late_protection_morning",
        name="Check late protection for all users in the morning",
        replace_existing=True,
        coalesce=True,
        max_instances=1
    )

    # 流量监控任务 - 每5分钟采集一次（全天24小时，无休息）
    scheduler.add_job(
        func=collect_traffic_data,
        trigger="interval",
        minutes=5,
        id="collect_traffic_data",
        name="Collect library traffic data every 5 minutes",
        replace_existing=True,
        next_run_time=datetime.datetime.now(),  # 立即执行一次
        coalesce=True,
        max_instances=1
    )

    # 流量数据清理任务 - 每天凌晨2点清理7天前的数据
    scheduler.add_job(
        func=cleanup_traffic_data,
        trigger=CronTrigger(hour="2", minute="0"),
        id="cleanup_traffic_data",
        name="Cleanup old traffic data daily",
        replace_existing=True,
        coalesce=True,
        max_instances=1
    )



def log_scheduled_jobs():
    """记录已调度的任务"""
    logger.info("当前计划的任务:")
    for job in scheduler.get_jobs():
        next_run = job.next_run_time.astimezone(Config.TIMEZONE).strftime("%Y-%m-%d %H:%M:%S")
        logger.info(f"- {job.name}: 下次执行时间 {next_run}")


@with_app_context
def auth_all_users():
    """为所有自动预约用户进行认证"""
    logger.info("开始为所有自动预约用户进行认证")
    users = get_users_with_setting(auto_reserve=True)

    for user in users:
        AuthManager.clear_authenticator(user.id)
        auth = AuthManager.get_authenticator(user)
        if auth:
            logger.info(f"用户 {user.username} 认证成功")
        else:
            logger.error(f"用户 {user.username} 认证失败")

    logger.info(f"已完成所有 {len(users)} 个用户的认证")


@with_app_context
@handle_exception
def reserve_for_users(admin_only=False):
    """为指定类型的用户预约座位"""
    user_type = "管理员" if admin_only else "普通用户"
    logger.info(f"开始为{user_type}预约座位")
    users = get_users_with_setting(auto_reserve=True, is_admin=admin_only)

    for user in users:
        try:
            logger.info(f"开始为{user_type} {user.username} 预约座位")
            reserve_for_user(user)
        except Exception as e:
            logger.error(f"为{user_type} {user.username} 预约座位时发生错误: {str(e)}")

    logger.info(f"已完成所有 {len(users)} 个{user_type}的座位预约")


@handle_exception
def reserve_for_user(user):
    """为单个用户预约座位，失败时尝试重新认证"""
    reservation_setting = ReservationSetting.query.filter_by(user_id=user.id).first()
    if not reservation_setting:
        logger.warning(f"用户 {user.username} 没有预约设置，跳过")
        return False, "缺少预约设置"

    seat_id = Config.get_seat_id(reservation_setting.area, reservation_setting.seat_number)
    if not seat_id:
        logger.error(
            f"用户 {user.username} 的座位区域或座位号无效: {reservation_setting.area} - {reservation_setting.seat_number}")
        return False, "座位配置无效"

    # 是否已尝试过重新认证并重试预约（避免在短时间内重复调用预约接口）
    retried = False

    # 先获取认证器
    authenticator = AuthManager.get_authenticator(user)
    if not authenticator:
        logger.error(f"用户 {user.username} 初始认证失败，无法预约")
        record_auth_failure(user, "reserve", reservation_setting, send_notification=True)
        return False, "认证失败"

    # 创建预约对象并尝试预约
    reservation = SeatReservation(user, authenticator=authenticator)

    while True:
        # 第一次尝试时不发送通知（因为可能需要重试），只在最终结果时发送
        # 如果已经重试过，说明这是最后一次尝试，需要发送通知
        send_notification_now = retried
        
        success, message = reservation.reserve_seat(
            reservation_setting.area,
            reservation_setting.seat_number,
            seat_id,
            date_str=None,
            start_time=reservation_setting.start_time,
            send_notification=send_notification_now
        )

        if success:
            logger.info(
                f"用户 {user.username} 预约成功: {reservation_setting.area} 区域 {reservation_setting.seat_number} 号座位")
            # 如果是第一次就成功了，需要发送成功通知
            if not send_notification_now:
                from utils.notification import NotificationService
                from models import ReservationHistory
                # 获取刚刚创建的历史记录并发送通知
                latest_history = ReservationHistory.query.filter_by(
                    user_id=user.id
                ).order_by(ReservationHistory.created_at.desc()).first()
                if latest_history:
                    NotificationService.send_single_reservation_notification(user, latest_history)
            return True, message

        message = message or "预约失败"

        # 检查是否需要重试：只在可能是认证问题时重试
        # 不需要重试的情况：
        # 1. 用户已有预约
        # 2. 座位被占用/正在被预约（竞争失败）
        # 3. 已经重试过一次
        should_retry = False
        if not retried:
            message_lower = message.lower()
            # 明确不需要重试的业务逻辑错误
            no_retry_keywords = [
                "已有预约", "有预约", "已预约", "already",
                "正在被预约", "设备正在被预约",
                "时长不足", "时间段",
                "座位号无效", "配置无效"
            ]
            # 如果错误消息不包含明确的业务逻辑错误关键词，才考虑重试
            if not any(keyword in message_lower for keyword in no_retry_keywords):
                should_retry = True
                logger.warning(f"用户 {user.username} 预约失败，尝试重新认证: {message}")
            else:
                logger.info(f"用户 {user.username} 预约失败（业务逻辑错误，不重试）: {message}")
        
        if not should_retry or retried:
            if retried:
                logger.error(f"用户 {user.username} 预约失败（已尝试重新认证）: {message}")
            # 如果第一次尝试失败且不需要重试，需要发送通知
            if not send_notification_now:
                from utils.notification import NotificationService
                from models import ReservationHistory
                # 获取刚刚创建的历史记录并发送通知
                latest_history = ReservationHistory.query.filter_by(
                    user_id=user.id
                ).order_by(ReservationHistory.created_at.desc()).first()
                if latest_history:
                    NotificationService.send_single_reservation_notification(user, latest_history)
            return False, message

        retried = True
        AuthManager.clear_authenticator(user.id)
        authenticator = AuthManager.get_authenticator(user)
        if not authenticator:
            logger.error(f"用户 {user.username} 重新认证失败，无法预约")
            record_auth_failure(user, "reserve", reservation_setting, send_notification=True)
            return False, "认证失败"

        logger.info(f"用户 {user.username} 重新认证成功，再次尝试预约")
        reservation = SeatReservation(user, authenticator=authenticator)


def record_auth_failure(user, action_type, setting=None, send_notification=True):
    """记录认证失败到历史记录"""
    try:
        action_desc = "预约座位" if action_type == "reserve" else "迟到保护"
        tomorrow = get_tomorrow_date() if action_type == "reserve" else get_today_date()

        # 获取座位相关信息
        area = setting.area if setting else None
        seat_number = setting.seat_number if setting else None
        seat_id = Config.get_seat_id(area, seat_number) if area and seat_number else None
        start_time = setting.start_time if setting else None

        # 创建历史记录
        history = ReservationHistory(
            user_id=user.id,
            area=area,
            seat_number=seat_number,
            seat_id=seat_id,
            reserve_date=datetime.datetime.strptime(tomorrow, "%Y-%m-%d").date(),
            start_time=start_time,
            end_time=None,
            status=ReservationHistory.STATUS_AUTH_FAILED,
            message=f"{action_desc}失败: 认证失败，请更新您的统一认证或图书馆密码",
            is_late_protection=(action_type == "protect")
        )

        db.session.add(history)
        db.session.commit()
        logger.info(f"已记录用户 {user.username} 的认证失败: {action_desc}")
        
        # 发送认证失败通知（如果启用）
        if send_notification:
            NotificationService.send_single_reservation_notification(user, history)
    except Exception as e:
        logger.error(f"记录认证失败时出错: {str(e)}")
        db.session.rollback()


def schedule_late_protection(user_id, prevent_late):
    """调度用户的迟到保护任务"""
    try:
        with scheduler.app.app_context():
            user = User.query.get(user_id)
            if not user:
                logger.error(f"未找到ID为 {user_id} 的用户")
                return

            user_late_check_jobs = [job for job in scheduler.get_jobs()
                                    if job.id.startswith(f"late_check_{user_id}_")]

            if not prevent_late:
                for job in user_late_check_jobs:
                    scheduler.remove_job(job.id)
                logger.info(f"用户 {user.username} 已禁用迟到保护功能，已移除 {len(user_late_check_jobs)} 个相关任务")
                return

            logger.info(f"用户 {user.username} 已启用迟到保护功能")
            logger.info(f"立即执行一次迟到保护检查")
            check_late_protection_with_context(user_id)

    except Exception as e:
        logger.error(f"设置用户 {user_id} 迟到保护任务时发生错误: {str(e)}")


def check_late_protection_with_context(user_id):
    """在应用上下文中执行迟到保护检查"""
    with scheduler.app.app_context():
        try:
            user = User.query.get(user_id)
            if user:
                check_late_protection_for_user(user)
            else:
                logger.error(f"未找到ID为{user_id}的用户")
        except Exception as e:
            logger.error(f"执行迟到保护检查时出错: {str(e)}")


@handle_exception
def check_late_protection_for_user(user):
    """检查用户的迟到保护，失败时尝试重新认证"""
    logger.info(f"检查用户 {user.username} 的迟到保护")

    setting = ReservationSetting.query.filter_by(user_id=user.id).first()
    if not setting or not setting.prevent_late:
        logger.info(f"用户 {user.username} 未启用迟到保护，跳过")
        return

    # 获取认证器并尝试执行迟到保护
    authenticator = AuthManager.get_authenticator(user)
    if authenticator:
        reservation = SeatReservation(user, authenticator=authenticator)
        today = get_today_date()
        today_reservations = reservation.get_reservations(begin_date=today, end_date=today)

        # 如果获取预约失败，尝试重新认证
        if today_reservations is None:
            logger.info(f"用户 {user.username} 获取预约信息失败，尝试重新认证")
            AuthManager.clear_authenticator(user.id)
            authenticator = AuthManager.get_authenticator(user)
            if authenticator:
                logger.info(f"用户 {user.username} 重新认证成功，再次检查迟到保护")
                reservation = SeatReservation(user, authenticator=authenticator)
                today_reservations = reservation.get_reservations(begin_date=today, end_date=today)
                if today_reservations is None:
                    logger.error(f"用户 {user.username} 重新认证后获取预约信息仍然失败")
                    record_auth_failure(user, "protect", setting)
                    return
            else:
                logger.error(f"用户 {user.username} 重新认证失败，跳过迟到保护检查")
                record_auth_failure(user, "protect", setting)
                return

        if not today_reservations:
            logger.info(f"用户 {user.username} 今日没有预约，跳过")
            return

        process_today_reservations(user, reservation, today_reservations)
    else:
        logger.error(f"用户 {user.username} 认证失败，无法执行迟到保护")
        record_auth_failure(user, "protect", setting)


def process_today_reservations(user, reservation, today_reservations):
    """处理用户当天的预约"""
    now = datetime.datetime.now()

    if today_reservations:
        resv = today_reservations[0]
        resv_begin_time_ms = resv.get("resvBeginTime")
        if resv_begin_time_ms:
            resv_begin_time = datetime.datetime.fromtimestamp(resv_begin_time_ms / 1000)
            time_diff_minutes = (resv_begin_time - now).total_seconds() / 60

            # 在开始时间前约20分钟进行检测
            if 19 <= time_diff_minutes <= 21:
                logger.info(f"用户 {user.username} 预约时间 {resv_begin_time} 在约20分钟前，进行迟到保护检测")
                handle_potential_late_arrival(user, resv, reservation, get_today_date(), resv_begin_time)
            else:
                logger.info(
                    f"用户 {user.username} 预约时间 {resv_begin_time} 不在检查范围内（差距 {time_diff_minutes:.1f} 分钟）")
                if time_diff_minutes > 21:
                    schedule_late_check_task(user, resv_begin_time)


def handle_potential_late_arrival(user, resv, reservation, today, resv_begin_time):
    """处理可能迟到的情况（开始前20分钟检测），失败时尝试重新认证；若已签到则不进行保护"""
    uuid = resv.get("uuid")

    # 检查签到/使用状态：若已使用中或已签到则跳过保护
    status_name = resv.get("statusName", "")
    if status_name and ("使用" in status_name or "签到" in status_name):
        logger.info(f"用户 {user.username} 当前预约状态为 {status_name}，已签到或使用中，跳过迟到保护")
        return

    dev_info_list = resv.get("resvDevInfoList", [])
    if not dev_info_list:
        logger.error(f"无法获取预约 {uuid} 的座位详情")
        return

    dev_info = dev_info_list[0]
    dev_id = dev_info.get("devId")
    dev_name = dev_info.get("devName", "")
    logger.info(f"预约的座位名称: {dev_name}")

    area_name, seat_number = extract_seat_info(user, dev_name, uuid)
    if not area_name or not seat_number:
        logger.error(f"无法确定座位信息，取消迟到保护")
        return

    # 尝试取消预约
    cancel_result, cancel_message = reservation.cancel_reservation(uuid)
    if not cancel_result:
        # 如果取消失败，尝试重新认证并再次取消
        logger.info(f"用户 {user.username} 取消预约失败，尝试重新认证")
        AuthManager.clear_authenticator(user.id)
        authenticator = AuthManager.get_authenticator(user)
        if authenticator:
            logger.info(f"用户 {user.username} 重新认证成功，再次尝试取消预约")
            reservation = SeatReservation(user, authenticator=authenticator)
            cancel_result, cancel_message = reservation.cancel_reservation(uuid)
            if not cancel_result:
                logger.error(f"用户 {user.username} 重新认证后取消预约仍然失败")
                return
        else:
            logger.error(f"用户 {user.username} 重新认证失败，取消迟到保护")
            return

    # 到这里表示取消已成功
    logger.info(f"已取消用户 {user.username} 的预约 {uuid}")

    # 发送取消预约通知（迟到保护触发）
    try:
        NotificationService.send_single_reservation_notification(
            user,
            ReservationHistory(
                user_id=user.id,
                area=area_name,
                seat_number=seat_number,
                seat_id=dev_id,
                reserve_date=datetime.datetime.strptime(today, "%Y-%m-%d").date(),
                start_time=resv_begin_time.strftime("%H:%M:%S"),
                end_time=None,
                status=ReservationHistory.STATUS_CANCELED,
                message=f"迟到保护：已取消预约",
                is_late_protection=True
            )
        )
    except Exception as e:
        logger.error(f"发送取消通知时出错: {e}")

    # 重新预约
    reschedule_result = reschedule_seat_after_cancel(user, reservation, area_name, seat_number, dev_id, today, resv_begin_time)
    if not reschedule_result:
        logger.error(f"用户 {user.username} 重新预约失败")


def extract_seat_info(user, dev_name, uuid):
    """从预约信息提取座位信息"""
    # 首先从用户设置中获取
    user_setting = ReservationSetting.query.filter_by(user_id=user.id).first()
    if user_setting:
        return user_setting.area, user_setting.seat_number

    # 尝试从座位名称解析
    if " " in dev_name:
        parts = dev_name.split(" ")
        area_name = parts[0]
        try:
            seat_number = int(parts[1].rstrip("号"))
            return area_name, seat_number
        except (ValueError, IndexError):
            pass

    # 从历史记录获取
    latest_history = ReservationHistory.query.filter_by(
        user_id=user.id,
        status=ReservationHistory.STATUS_SUCCESS,
        uuid=uuid
    ).first()

    if latest_history:
        return latest_history.area, latest_history.seat_number

    return None, None


def reschedule_seat_after_cancel(user, reservation, area_name, seat_number, dev_id, today, resv_begin_time):
    """取消预约后重新预约座位，失败时尝试重新认证, 返回 True/False"""
    # 计算新的开始时间（推迟1小时）
    new_start_time = (resv_begin_time + datetime.timedelta(hours=1)).strftime("%H:%M:%S")
    logger.info(f"迟到保护: 原预约时间 {resv_begin_time.strftime('%H:%M:%S')}, 新预约时间 {new_start_time}")

    from utils.date_utils import get_end_time
    end_time = get_end_time(today)

    new_start_time_obj = datetime.datetime.strptime(new_start_time, "%H:%M:%S")
    end_time_obj = datetime.datetime.strptime(end_time, "%H:%M:%S")
    duration = (end_time_obj - new_start_time_obj).seconds / 3600

    if duration >= 2:
        logger.info(f"为用户 {user.username} 预约 {area_name} {seat_number}号 新时间 {new_start_time}")
        # 尝试预约
        reserve_result, reserve_message = reservation.reserve_today_seat(area_name, seat_number, dev_id, new_start_time)
        if not reserve_result:
            # 如果预约失败，尝试重新认证
            logger.info(f"用户 {user.username} 重新预约失败，尝试重新认证")
            AuthManager.clear_authenticator(user.id)
            authenticator = AuthManager.get_authenticator(user)
            if authenticator:
                logger.info(f"用户 {user.username} 重新认证成功，再次尝试预约")
                reservation = SeatReservation(user, authenticator=authenticator)
                reserve_result, reserve_message = reservation.reserve_today_seat(area_name, seat_number, dev_id, new_start_time)
                if not reserve_result:
                    logger.error(f"用户 {user.username} 重新认证后预约仍然失败")
                    return
            else:
                logger.error(f"用户 {user.username} 重新认证失败，无法重新预约")
                return

        logger.info(f"迟到保护：用户 {user.username} 重新预约成功")

        # 设置下一次检查时间（新预约时间前20分钟）
        new_check_time = (
                datetime.datetime.strptime(f"{today} {new_start_time}", "%Y-%m-%d %H:%M:%S") -
                datetime.timedelta(minutes=20)
        )

        now = datetime.datetime.now()
        if new_check_time > now:
            schedule_specific_late_check(user, new_start_time, new_check_time)
    else:
        logger.info(f"剩余时间不足2小时（{duration:.1f}小时），不再重新预约")


def schedule_specific_late_check(user, start_time, check_time):
    """为特定时间点调度迟到保护检查"""
    job_id = f"late_check_{user.id}_{start_time.replace(':', '')}"
    scheduler.add_job(
        func=lambda uid=user.id: check_late_protection_with_context(uid),
        trigger="date",
        run_date=check_time,
        id=job_id,
        name=f"Late protection check for {user.username} at {start_time}",
        replace_existing=True
    )
    logger.info(f"已为用户 {user.username} 设置下一次迟到保护检查，时间: {check_time}")


def schedule_late_check_task(user, resv_begin_time):
    """调度迟到检查任务（开始前20分钟）"""
    now = datetime.datetime.now()
    check_time = resv_begin_time - datetime.timedelta(minutes=20)

    if check_time > now:
        schedule_specific_late_check(user, resv_begin_time.strftime('%H:%M:%S'), check_time)


@with_app_context
@handle_exception
def check_late_protection_for_all_users():
    """为所有启用迟到保护的用户检查迟到保护"""
    logger.info("开始为所有用户检查迟到保护")
    users = get_users_with_setting(prevent_late=True)
    logger.info(f"找到 {len(users)} 个启用迟到保护的用户")

    for user in users:
        check_late_protection_for_user(user)


@with_app_context
@handle_exception
def collect_traffic_data():
    """采集图书馆流量数据 (24/7)"""
    now = datetime.datetime.now()
    weekday_name = ['一', '二', '三', '四', '五', '六', '日'][now.weekday()]
    logger.info(f"开始采集流量数据（周{weekday_name} {now.strftime('%H:%M')}）")
    LibraryTrafficMonitor.collect_and_save()


@with_app_context
@handle_exception
def cleanup_traffic_data():
    """清理旧的流量数据"""
    logger.info("开始清理7天前的流量数据...")
    Traffic.cleanup_old_data(days=7)
    logger.info("流量数据清理完成。")


