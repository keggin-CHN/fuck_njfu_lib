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
            add_scheduler_jobs()
            scheduler.start()
            atexit.register(lambda: scheduler.shutdown(wait=False))
            scheduler_initialized = True
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
        replace_existing=True
    )

    # 管理员预约任务
    admin_reserve_time = Config.ADMIN_RESERVE_TIME.split(':')
    scheduler.add_job(
        func=reserve_for_users,
        trigger=CronTrigger(hour=admin_reserve_time[0], minute=admin_reserve_time[1]),
        id="reserve_for_admins",
        name="Reserve seats for admin users daily",
        kwargs={"admin_only": True},
        replace_existing=True
    )

    # 普通用户预约任务
    user_reserve_time = Config.USER_RESERVE_TIME.split(':')
    scheduler.add_job(
        func=reserve_for_users,
        trigger=CronTrigger(hour=user_reserve_time[0], minute=user_reserve_time[1]),
        id="reserve_for_normal_users",
        name="Reserve seats for normal users daily",
        kwargs={"admin_only": False},
        replace_existing=True
    )

    # 迟到保护检查任务
    scheduler.add_job(
        func=check_late_protection_for_user,
        trigger=CronTrigger(hour="7", minute="10"),
        id="check_late_protection_morning",
        name="Check late protection for all users in the morning",
        replace_existing=True
    )

    # 流量监控任务 - 每5分钟采集一次（全天24小时，无休息）
    scheduler.add_job(
        func=LibraryTrafficMonitor.collect_and_save,
        trigger="interval",
        minutes=5,
        id="collect_traffic_data",
        name="Collect library traffic data every 5 minutes",
        replace_existing=True,
        next_run_time=datetime.datetime.now()  # 立即执行一次
    )

    # 流量数据清理任务 - 每天凌晨2点清理7天前的数据
    scheduler.add_job(
        func=LibraryTrafficMonitor.cleanup_old_data,
        trigger=CronTrigger(hour="2", minute="0"),
        id="cleanup_traffic_data",
        name="Cleanup old traffic data daily",
        replace_existing=True
    )



def log_scheduled_jobs():
    """记录已调度的任务"""
    logger.info("当前计划的任务:")
    for job in scheduler.get_jobs():
        next_run = job.next_run_time.astimezone(Config.TIMEZONE).strftime("%Y-%m-%d %H:%M:%S")
        logger.info(f"- {job.name}: 下次执行时间 {next_run}")


# @with_app_context
def auth_all_users():
    """为所有自动预约用户进行认证"""
    logger.info("开始为所有自动预约用户进行认证")
    with scheduler.app.app_context():
        users = get_users_with_setting(auto_reserve=True)

        for user in users:
            AuthManager.clear_authenticator(user.id)
            auth = AuthManager.get_authenticator(user)
            if auth:
                logger.info(f"用户 {user.username} 认证成功")
            else:
                logger.error(f"用户 {user.username} 认证失败")

    logger.info(f"已完成所有 {len(users)} 个用户的认证")


# @with_app_context
@handle_exception
def reserve_for_users(admin_only=False):
    """为指定类型的用户预约座位"""
    user_type = "管理员" if admin_only else "普通用户"
    logger.info(f"开始为{user_type}预约座位")
    with scheduler.app.app_context():
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
        return

    seat_id = Config.get_seat_id(reservation_setting.area, reservation_setting.seat_number)
    if not seat_id:
        logger.error(
            f"用户 {user.username} 的座位区域或座位号无效: {reservation_setting.area} - {reservation_setting.seat_number}")
        return

    # 是否已尝试过重新认证并重试预约（避免在短时间内重复调用预约接口）
    retried = False

    # 先获取认证器
    authenticator = AuthManager.get_authenticator(user)
    if not authenticator:
        logger.error(f"用户 {user.username} 初始认证失败，无法预约")
        record_auth_failure(user, "reserve", reservation_setting)
        return

    # 创建预约对象并尝试预约
    reservation = SeatReservation(user, authenticator=authenticator)
    result = reservation.reserve_seat(
        reservation_setting.area,
        reservation_setting.seat_number,
        seat_id,
        date_str=None,
        start_time=reservation_setting.start_time
    )

    # 如果失败，且还未重试过，则尝试重新认证后再次预约（仅重试一次）
    if not result and not retried:
        logger.info(f"用户 {user.username} 预约失败，尝试重新认证")
        # 清除旧认证
        AuthManager.clear_authenticator(user.id)
        # 重新获取认证器
        authenticator = AuthManager.get_authenticator(user)
        if authenticator:
            logger.info(f"用户 {user.username} 重新认证成功，再次尝试预约")
            reservation = SeatReservation(user, authenticator=authenticator)
            result = reservation.reserve_seat(
                reservation_setting.area,
                reservation_setting.seat_number,
                seat_id,
                date_str=None,
                start_time=reservation_setting.start_time
            )
            if result:
                logger.info(f"用户 {user.username} 重新认证后预约成功")
            else:
                logger.error(f"用户 {user.username} 重新认证后预约仍然失败")
                record_auth_failure(user, "reserve", reservation_setting)
        else:
            logger.error(f"用户 {user.username} 重新认证失败，无法预约")
            record_auth_failure(user, "reserve", reservation_setting)
        retried = True
    else:
        if result:
            logger.info(
                f"用户 {user.username} 预约成功: {reservation_setting.area} 区域 {reservation_setting.seat_number} 号座位")
        else:
            # 已经重试过且仍失败，记录失败（防止被重复触发重试）
            logger.error(f"用户 {user.username} 预约失败且已重试，放弃本次预约")
            record_auth_failure(user, "reserve", reservation_setting)


def record_auth_failure(user, action_type, setting=None):
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
                logger.error(f"未找到ID为 {user_id} 的用户")
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
