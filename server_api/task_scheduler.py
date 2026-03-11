"""
后台定时任务调度器 — 纯 Python 线程实现，不依赖 cron 或 APScheduler。
每 30 秒扫描 tasks/ 目录下的 JSON 文件，到时间就执行预约/迟到保护。
"""
import json
import os
import time
import datetime
import threading
import hashlib
import logging
from pathlib import Path

from config import Config

logger = logging.getLogger(__name__)

# 确保任务目录存在
os.makedirs(Config.TASKS_DIR, exist_ok=True)


# ---------------------------------------------------------------------------
# 任务 JSON 管理
# ---------------------------------------------------------------------------
def generate_task_id(username: str) -> str:
    """根据用户名生成任务ID（每个用户只有一个任务文件）。"""
    return hashlib.md5(username.encode()).hexdigest()[:12]


def get_task_path(task_id: str) -> str:
    return os.path.join(Config.TASKS_DIR, f"{task_id}.json")


def save_task(task_data: dict) -> str:
    """保存任务到 JSON 文件，返回 task_id。"""
    task_id = generate_task_id(task_data["username"])
    task_data["task_id"] = task_id
    if "created_at" not in task_data:
        task_data["created_at"] = datetime.datetime.now().isoformat()
    task_path = get_task_path(task_id)
    with open(task_path, "w", encoding="utf-8") as f:
        json.dump(task_data, f, ensure_ascii=False, indent=2)
    logger.info(f"任务已保存: {task_id} ({task_data['username']})")
    return task_id


def load_task(task_id: str) -> dict | None:
    """加载任务 JSON。"""
    task_path = get_task_path(task_id)
    if not os.path.exists(task_path):
        return None
    try:
        with open(task_path, "r", encoding="utf-8") as f:
            return json.load(f)
    except (json.JSONDecodeError, IOError) as e:
        logger.error(f"读取任务 {task_id} 失败: {e}")
        return None


def delete_task(task_id: str) -> bool:
    """删除任务文件。"""
    task_path = get_task_path(task_id)
    if os.path.exists(task_path):
        os.remove(task_path)
        logger.info(f"任务已删除: {task_id}")
        return True
    return False


def list_all_tasks() -> list[dict]:
    """列出所有任务。"""
    tasks = []
    for f in Path(Config.TASKS_DIR).glob("*.json"):
        try:
            with open(f, "r", encoding="utf-8") as fp:
                tasks.append(json.load(fp))
        except (json.JSONDecodeError, IOError):
            continue
    return tasks


def update_task_result(task_id: str, result_type: str, success: bool, message: str):
    """更新任务的最近执行结果。"""
    task = load_task(task_id)
    if not task:
        return
    now_str = datetime.datetime.now().isoformat()
    if result_type == "reserve":
        task["last_reserve_date"] = datetime.datetime.now().strftime("%Y-%m-%d")
        task["last_reserve_result"] = {
            "success": success,
            "message": message,
            "time": now_str,
        }
    elif result_type == "late_check":
        task["last_late_check"] = now_str
        task["last_late_result"] = {
            "success": success,
            "message": message,
            "time": now_str,
        }
    elif result_type == "auth":
        task["last_auth_time"] = now_str
        task["last_auth_result"] = {
            "success": success,
            "message": message,
            "time": now_str,
        }
    save_task(task)


# ---------------------------------------------------------------------------
# 调度逻辑
# ---------------------------------------------------------------------------
def _should_reserve_now(task: dict, now: datetime.datetime) -> bool:
    """判断当前是否应该执行自动预约。"""
    if not task.get("auto_reserve", False):
        return False

    # 获取预约执行时间
    reserve_time_str = task.get("reserve_time", Config.DEFAULT_RESERVE_TIME)
    try:
        parts = reserve_time_str.split(":")
        target_hour, target_minute = int(parts[0]), int(parts[1])
    except (ValueError, IndexError):
        target_hour, target_minute = 7, 3

    # 当前时间是否在执行窗口内（±1分钟）
    target = now.replace(hour=target_hour, minute=target_minute, second=0, microsecond=0)
    diff = abs((now - target).total_seconds())
    if diff > 60:
        return False

    # 检查今天是否已经执行过
    last_date = task.get("last_reserve_date", "")
    today_str = now.strftime("%Y-%m-%d")
    if last_date == today_str:
        return False

    # 检查每周计划 — 预约的是明天的座位
    tomorrow = now + datetime.timedelta(days=1)
    day_key = _weekday_to_key(tomorrow.weekday())
    weekly_plan = task.get("weekly_plan", {})
    if weekly_plan:
        day_plan = weekly_plan.get(day_key, {})
        if not day_plan.get("enabled", True):
            logger.info(f"任务 {task.get('task_id')}: 明天({day_key})未启用，跳过")
            return False

    return True


def _should_auth_now(task: dict, now: datetime.datetime) -> bool:
    """判断当前是否应该执行认证（在预约前提前认证）。"""
    if not task.get("auto_reserve", False):
        return False

    auth_time_str = task.get("auth_time", Config.DEFAULT_AUTH_TIME)
    try:
        parts = auth_time_str.split(":")
        target_hour, target_minute = int(parts[0]), int(parts[1])
    except (ValueError, IndexError):
        target_hour, target_minute = 6, 55

    target = now.replace(hour=target_hour, minute=target_minute, second=0, microsecond=0)
    diff = abs((now - target).total_seconds())
    if diff > 60:
        return False

    # 检查今天是否已经认证过
    last_auth = task.get("last_auth_time", "")
    if last_auth:
        try:
            last_auth_dt = datetime.datetime.fromisoformat(last_auth)
            if (now - last_auth_dt).total_seconds() < 3600:
                return False
        except ValueError:
            pass

    return True


def _should_late_check(task: dict, now: datetime.datetime) -> bool:
    """判断当前是否应该执行迟到保护检查。"""
    if not task.get("prevent_late", False):
        return False

    # 迟到保护：早上 7:10 之后每 10 分钟检查一次，直到 22:00
    if now.hour < 7 or now.hour >= 22:
        return False

    # 每 10 分钟的窗口
    if now.minute % 10 != 0:
        return False

    # 检查最近一次检查时间（避免30秒内重复）
    last_check = task.get("last_late_check", "")
    if last_check:
        try:
            last_dt = datetime.datetime.fromisoformat(last_check)
            if (now - last_dt).total_seconds() < 300:
                return False
        except ValueError:
            pass

    return True


def _weekday_to_key(weekday: int) -> str:
    """Python weekday (0=Mon) -> 任务 key。"""
    mapping = {0: "mon", 1: "tue", 2: "wed", 3: "thu", 4: "fri", 5: "sat", 6: "sun"}
    return mapping.get(weekday, "mon")


def _get_plan_for_date(task: dict, date: datetime.datetime) -> dict:
    """获取指定日期的预约计划（从 weekly_plan 或默认配置）。"""
    day_key = _weekday_to_key(date.weekday())
    weekly_plan = task.get("weekly_plan", {})
    day_plan = weekly_plan.get(day_key, {})

    if day_plan and day_plan.get("enabled", False):
        return {
            "area": day_plan.get("area", task.get("area")),
            "seat_number": day_plan.get("seat", task.get("seat_number")),
            "start_time": day_plan.get("start", task.get("start_time")),
            "end_time": day_plan.get("end", task.get("end_time")),
        }
    else:
        # 使用默认配置
        return {
            "area": task.get("area"),
            "seat_number": task.get("seat_number"),
            "start_time": task.get("start_time"),
            "end_time": task.get("end_time"),
        }


# ---------------------------------------------------------------------------
# 执行任务
# ---------------------------------------------------------------------------
def _execute_reserve_task(task: dict):
    """执行一个用户的自动预约。"""
    from task_executor import LightUser, reserve_seat, authenticate

    task_id = task["task_id"]
    username = task["username"]
    logger.info(f"[{task_id}] 开始为用户 {username} 执行自动预约")

    user = LightUser(username, task["edu_password"], task["lib_password"])

    # 预约明天的座位
    tomorrow = datetime.datetime.now() + datetime.timedelta(days=1)
    plan = _get_plan_for_date(task, tomorrow)

    if not plan.get("area") or not plan.get("seat_number"):
        msg = "未配置预约区域或座位号"
        logger.warning(f"[{task_id}] {msg}")
        update_task_result(task_id, "reserve", False, msg)
        return

    # 先认证
    authenticator = authenticate(user)
    if not authenticator:
        msg = "认证失败"
        logger.error(f"[{task_id}] {msg}")
        update_task_result(task_id, "reserve", False, msg)
        return

    success, message = reserve_seat(
        user,
        area=plan["area"],
        seat_number=plan["seat_number"],
        start_time=plan.get("start_time"),
        end_time=plan.get("end_time"),
        authenticator=authenticator,
    )

    update_task_result(task_id, "reserve", success, message)
    logger.info(f"[{task_id}] 预约结果: {'成功' if success else '失败'} - {message}")

    # 失败重试一次（重新认证）
    if not success:
        no_retry_kw = ("已有预约", "已预约", "时长不足", "座位号无效", "配置无效", "正在被预约")
        if not any(kw in message for kw in no_retry_kw):
            logger.info(f"[{task_id}] 尝试重新认证后重试")
            authenticator = authenticate(user)
            if authenticator:
                success, message = reserve_seat(
                    user,
                    area=plan["area"],
                    seat_number=plan["seat_number"],
                    start_time=plan.get("start_time"),
                    end_time=plan.get("end_time"),
                    authenticator=authenticator,
                )
                update_task_result(task_id, "reserve", success, message)
                logger.info(f"[{task_id}] 重试结果: {'成功' if success else '失败'} - {message}")


def _execute_auth_task(task: dict):
    """提前认证（预热）。"""
    from task_executor import LightUser, authenticate

    task_id = task["task_id"]
    username = task["username"]
    logger.info(f"[{task_id}] 提前认证用户 {username}")

    user = LightUser(username, task["edu_password"], task["lib_password"])
    authenticator = authenticate(user)
    success = authenticator is not None
    msg = "认证成功" if success else "认证失败"
    update_task_result(task_id, "auth", success, msg)


def _execute_late_check(task: dict):
    """执行迟到保护检查。"""
    from task_executor import LightUser, execute_late_protection

    task_id = task["task_id"]
    username = task["username"]
    logger.info(f"[{task_id}] 执行迟到保护检查: {username}")

    user = LightUser(username, task["edu_password"], task["lib_password"])
    area = task.get("area")
    seat_number = task.get("seat_number")

    if not area or not seat_number:
        msg = "未配置区域或座位号"
        update_task_result(task_id, "late_check", False, msg)
        return

    success, message = execute_late_protection(user, area, seat_number)
    update_task_result(task_id, "late_check", success, message)
    logger.info(f"[{task_id}] 迟到保护结果: {message}")


# ---------------------------------------------------------------------------
# 主循环
# ---------------------------------------------------------------------------
_scheduler_thread: threading.Thread | None = None
_scheduler_running = False


def _scheduler_loop():
    """后台循环，每 30 秒检查一次。"""
    global _scheduler_running
    logger.info("后台调度器已启动")

    while _scheduler_running:
        try:
            now = datetime.datetime.now()
            tasks = list_all_tasks()

            for task in tasks:
                task_id = task.get("task_id", "unknown")
                try:
                    # 认证预热
                    if _should_auth_now(task, now):
                        threading.Thread(
                            target=_execute_auth_task,
                            args=(task,),
                            name=f"auth-{task_id}",
                            daemon=True,
                        ).start()

                    # 自动预约
                    if _should_reserve_now(task, now):
                        threading.Thread(
                            target=_execute_reserve_task,
                            args=(task,),
                            name=f"reserve-{task_id}",
                            daemon=True,
                        ).start()

                    # 迟到保护
                    if _should_late_check(task, now):
                        # 先标记检查时间防止重复
                        update_task_result(task_id, "late_check", True, "检查中...")
                        threading.Thread(
                            target=_execute_late_check,
                            args=(task,),
                            name=f"late-{task_id}",
                            daemon=True,
                        ).start()

                except Exception as e:
                    logger.error(f"处理任务 {task_id} 时出错: {e}")

        except Exception as e:
            logger.error(f"调度器循环出错: {e}")

        time.sleep(30)

    logger.info("后台调度器已停止")


def start_scheduler():
    """启动后台调度线程。"""
    global _scheduler_thread, _scheduler_running
    if _scheduler_running:
        logger.warning("调度器已在运行")
        return

    _scheduler_running = True
    _scheduler_thread = threading.Thread(
        target=_scheduler_loop,
        name="task-scheduler",
        daemon=True,
    )
    _scheduler_thread.start()
    logger.info("后台调度线程已启动")


def stop_scheduler():
    """停止后台调度线程。"""
    global _scheduler_running
    _scheduler_running = False
    logger.info("正在停止调度器...")
