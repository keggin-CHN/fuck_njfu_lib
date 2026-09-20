"""
超轻量预约 API 服务器
- 无数据库，用户数据以 JSON 文件存储
- Python 后台线程做定时调度
- 极低内存占用
- API Key 鉴权
"""
import sys
import os
import logging
from contextlib import asynccontextmanager

# 确保 server_api 目录优先，以便 config.py 不被 backend 的覆盖
_server_api_dir = os.path.dirname(os.path.abspath(__file__))
if _server_api_dir not in sys.path:
    sys.path.insert(0, _server_api_dir)

# 将 backend 目录加入路径（append，让 server_api 优先）
_backend_dir = os.path.abspath(os.path.join(_server_api_dir, "..", "backend"))
if _backend_dir not in sys.path:
    sys.path.append(_backend_dir)

from fastapi import FastAPI, HTTPException, Header, Depends
from pydantic import BaseModel, Field
from typing import Optional

from config import Config
from task_scheduler import (
    save_task, load_task, delete_task, list_all_tasks,
    generate_task_id, start_scheduler, stop_scheduler,
)
from task_executor import (
    LightUser, reserve_seat, get_reservations, cancel_reservation,
    authenticate, get_punish_info, perform_seat_action,
    get_room_seats, get_library_traffic,
    get_user_info, get_user_credit,
)

# ---------------------------------------------------------------------------
# 日志
# ---------------------------------------------------------------------------
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(name)s] %(levelname)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
)
logger = logging.getLogger("server_api")


# ---------------------------------------------------------------------------
# API Key 鉴权
# ---------------------------------------------------------------------------
def verify_api_key(x_api_key: str = Header(..., alias="X-API-Key")):
    """验证请求中的 API Key。"""
    expected_key = Config.get_api_key()
    valid_keys = {expected_key} if expected_key else set()
    if not valid_keys:
        logger.warning("未配置 API Key，请运行 deploy.sh 或设置 API_KEY 环境变量")
        raise HTTPException(status_code=500, detail="服务器未配置 API Key")
    if x_api_key not in valid_keys:
        raise HTTPException(status_code=403, detail="API Key 无效")
    return x_api_key


# ---------------------------------------------------------------------------
# FastAPI 生命周期
# ---------------------------------------------------------------------------
@asynccontextmanager
async def lifespan(app: FastAPI):
    api_key = Config.get_api_key()
    if api_key:
        logger.info(f"API Key 已加载 (前4位: {api_key[:4]}...)")
    else:
        logger.warning("未配置 API Key！请设置 API_KEY 环境变量或创建 .api_key 文件")
    logger.info("启动后台调度器...")
    start_scheduler()
    yield
    logger.info("停止后台调度器...")
    stop_scheduler()


app = FastAPI(
    title="NJFU 图书馆预约 API",
    description="超轻量定时预约服务器 - 需要 API Key 鉴权",
    version="1.0.0",
    lifespan=lifespan,
)


# ---------------------------------------------------------------------------
# 请求/响应模型
# ---------------------------------------------------------------------------
class Credentials(BaseModel):
    username: str = Field(..., description="学号")
    edu_password: str = Field(..., description="统一认证密码")
    lib_password: str = Field(..., description="图书馆密码")


class SendSmsRequest(BaseModel):
    username: str = Field(..., description="学号")
    edu_password: Optional[str] = Field(None, description="统一认证密码（可选，优先从已存任务读取）")
    lib_password: Optional[str] = Field(None, description="图书馆密码（可选，优先从已存任务读取）")


class VerifySmsRequest(BaseModel):
    username: str = Field(..., description="学号")
    dynamic_code: str = Field(..., description="短信动态验证码")
    edu_password: Optional[str] = Field(None, description="统一认证密码（可选）")
    lib_password: Optional[str] = Field(None, description="图书馆密码（可选）")
    execution: Optional[str] = Field(None, description="CAS执行流状态")


class ReserveRequest(Credentials):
    edu_password: Optional[str] = Field(None, description="统一认证密码（省略时从已存任务读取）")
    lib_password: Optional[str] = Field(None, description="图书馆密码（省略时从已存任务读取）")
    area: str = Field(..., description="区域名称")
    seat_number: int = Field(..., description="座位号")
    start_time: Optional[str] = Field(None, description="开始时间")
    end_time: Optional[str] = Field(None, description="结束时间")
    date: Optional[str] = Field(None, description="日期，默认明天")


class CancelRequest(Credentials):
    uuid: str = Field(..., description="预约 UUID")


class SeatActionRequest(BaseModel):
    username: str = Field(..., description="学号")
    action: str = Field(..., description="操作类型: checkIn, leave, back, stop")
    resv_id: str = Field(..., description="预约UUID")


class QueryRequest(Credentials):
    begin_date: Optional[str] = Field(None, description="开始日期")
    end_date: Optional[str] = Field(None, description="结束日期")


class TaskRegisterRequest(Credentials):
    area: str = Field(..., description="默认区域")
    seat_number: int = Field(..., description="默认座位号")
    start_time: Optional[str] = Field("08:00", description="默认开始时间")
    end_time: Optional[str] = Field("22:00", description="默认结束时间")
    auto_reserve: bool = Field(True, description="是否启用自动预约")
    prevent_late: bool = Field(False, description="是否启用迟到保护")
    reserve_time: Optional[str] = Field(None, description="预约执行时间")
    auth_time: Optional[str] = Field(None, description="认证时间")
    weekly_plan: Optional[dict] = Field(None, description="每周计划 JSON")


class TaskUpdateRequest(BaseModel):
    area: Optional[str] = None
    seat_number: Optional[int] = None
    start_time: Optional[str] = None
    end_time: Optional[str] = None
    auto_reserve: Optional[bool] = None
    prevent_late: Optional[bool] = None
    reserve_time: Optional[str] = None
    auth_time: Optional[str] = None
    weekly_plan: Optional[dict] = None


# ---------------------------------------------------------------------------
# 公开端点（不需要 API Key）
# ---------------------------------------------------------------------------
@app.get("/api/health")
async def health():
    """健康检查（无需 API Key）。"""
    tasks = list_all_tasks()
    return {
        "status": "ok",
        "mode": "direct_campus" if getattr(Config, "DIRECT_CAMPUS_MODE", False) else "webvpn_proxy",
        "active_tasks": len(tasks),
        "auto_reserve_count": sum(1 for t in tasks if t.get("auto_reserve")),
        "late_protection_count": sum(1 for t in tasks if t.get("prevent_late")),
    }


@app.get("/api/verify")
async def verify_connection(x_api_key: str = Header(..., alias="X-API-Key")):
    """验证 API Key 是否正确（Android 端验证服务器按钮调用此接口）。"""
    expected_key = Config.get_api_key()
    valid_keys = {expected_key} if expected_key else set()
    if not valid_keys:
        raise HTTPException(status_code=500, detail="服务器未配置 API Key")
    if x_api_key not in valid_keys:
        return {"success": False, "message": "API Key 无效"}
    return {"success": True, "message": "连接成功"}


# ---------------------------------------------------------------------------
# 受保护端点（需要 API Key）
# ---------------------------------------------------------------------------
@app.post("/api/reserve/now", dependencies=[Depends(verify_api_key)])
async def reserve_now(req: ReserveRequest):
    """立即预约座位。"""
    edu_pwd = req.edu_password
    lib_pwd = req.lib_password
    if not edu_pwd or not lib_pwd:
        task = load_task(generate_task_id(req.username))
        if task:
            edu_pwd = edu_pwd or task.get("edu_password", "")
            lib_pwd = lib_pwd or task.get("lib_password", "")

    user = LightUser(req.username, edu_pwd or "", lib_pwd or "")
    success, message = reserve_seat(
        user,
        area=req.area,
        seat_number=req.seat_number,
        start_time=req.start_time,
        end_time=req.end_time,
        date_str=req.date,
    )
    return {"success": success, "message": message}


@app.post("/api/cancel", dependencies=[Depends(verify_api_key)])
async def cancel(req: CancelRequest):
    """取消预约。"""
    user = LightUser(req.username, req.edu_password, req.lib_password)
    success, message = cancel_reservation(user, req.uuid)
    return {"success": success, "message": message}


@app.post("/api/reservations", dependencies=[Depends(verify_api_key)])
async def query_reservations(req: QueryRequest):
    """查询预约列表。"""
    user = LightUser(req.username, req.edu_password, req.lib_password)
    result = get_reservations(user, begin_date=req.begin_date, end_date=req.end_date)
    if result is None:
        raise HTTPException(status_code=401, detail="认证失败")
    return {"success": True, "data": result}


@app.post("/api/auth/test", dependencies=[Depends(verify_api_key)])
async def test_auth(req: Credentials):
    """测试认证是否成功。"""
    user = LightUser(req.username, req.edu_password, req.lib_password)
    auth = authenticate(user)
    if auth:
        return {"success": True, "message": "认证成功"}
    else:
        return {
            "success": False,
            "message": getattr(user, "last_auth_error", None) or "认证失败，请检查账号密码",
        }


@app.post("/api/auth/send_code")
async def send_code(req: SendSmsRequest):
    """请求发送 CAS 动态短信验证码（无需 API Key，方便用户直接触发）。"""
    from utils.auth_manager import LibraryAuthenticator
    from task_scheduler import load_task, generate_task_id

    edu_pwd = req.edu_password
    lib_pwd = req.lib_password
    if not edu_pwd or not lib_pwd:
        task = load_task(generate_task_id(req.username))
        if task:
            edu_pwd = edu_pwd or task.get("edu_password", "")
            lib_pwd = lib_pwd or task.get("lib_password", "")

    auth = LibraryAuthenticator(req.username, edu_pwd or "", lib_pwd or "")
    ticket, status = auth.first_level_auth()
    if status == "need_dynamic_code":
        success, message, mobile = auth.send_dynamic_code()
        if success:
            auth.save_pending_session()
        return {
            "success": success,
            "message": message,
            "mobile": mobile,
            "execution": getattr(auth, "last_dynamic_execution", "e1s2")
        }
    elif ticket:
        token, acc_no = auth.second_level_auth()
        if token and acc_no:
            auth.save_session()
            return {
                "success": True,
                "message": "无需短信验证，已直接完成登录！",
                "token": token,
                "acc_no": acc_no,
                "mobile": None
            }
    return {"success": False, "message": f"触发验证码失败: status={status}", "mobile": None}


@app.post("/api/auth/verify_code")
async def verify_code(req: VerifySmsRequest):
    """提交短信验证码，完成统一认证并获取/持久化图书馆 Token。"""
    from utils.auth_manager import LibraryAuthenticator
    from task_scheduler import load_task, generate_task_id

    edu_pwd = req.edu_password
    lib_pwd = req.lib_password
    if not edu_pwd or not lib_pwd:
        task = load_task(generate_task_id(req.username))
        if task:
            edu_pwd = edu_pwd or task.get("edu_password", "")
            lib_pwd = lib_pwd or task.get("lib_password", "")

    auth = LibraryAuthenticator(req.username, edu_pwd or "", lib_pwd or "")
    auth.load_pending_session()
    exec_val = req.execution or getattr(auth, "last_dynamic_execution", "e1s2")
    success, message = auth.submit_dynamic_code(req.dynamic_code, exec_val)
    return {
        "success": success,
        "message": message,
        "token": auth.token if success else None,
        "acc_no": auth.acc_no if success else None
    }


@app.get("/api/auth/status/{username}")
async def auth_status(username: str):
    """检查指定用户的会话 Token 是否有效，并返回凭据与 Cookie。"""
    import requests
    from utils.auth_manager import LibraryAuthenticator
    auth = LibraryAuthenticator(username, "", "")
    valid = auth.is_valid()
    cookies_dict = requests.utils.dict_from_cookiejar(auth.session.cookies) if valid else {}
    return {
        "username": username,
        "is_valid": valid,
        "token": auth.token if valid else None,
        "acc_no": auth.acc_no if valid else None,
        "last_auth_time": auth.last_auth_time,
        "cookies": cookies_dict,
    }


@app.get("/api/reservations/{username}")
async def query_user_reservations(username: str):
    """查询指定用户的当前有效预约列表。"""
    user = LightUser(username, "", "")
    reservations = get_reservations(user)
    if reservations is None:
        return {"success": False, "message": "用户会话未就绪或已过期", "data": []}
    return {"success": True, "count": len(reservations), "data": reservations}


@app.get("/api/punish/{username}")
async def query_user_punish(username: str):
    """查询指定用户的违规惩罚记录。"""
    user = LightUser(username, "", "")
    info = get_punish_info(user)
    if info is None:
        return {"code": 0, "message": "查询成功", "data": []}
    return info


@app.get("/api/user/info/{username}")
async def query_user_info(username: str):
    """查询指定用户的详细个人信息（姓名、班级、学号等）。"""
    user = LightUser(username, "", "")
    info = get_user_info(user)
    if info is None:
        return {"code": 0, "message": "查询成功", "data": None}
    return info


@app.get("/api/user/credit/{username}")
async def query_user_credit(username: str):
    """查询指定用户的信用剩余积分与明细记录。"""
    user = LightUser(username, "", "")
    info = get_user_credit(user)
    if info is None:
        return {"code": 0, "message": "查询成功", "surplus": None, "records": []}
    return info


@app.post("/api/seat/action")
async def execute_seat_action(req: SeatActionRequest):
    """执行座位签到、暂离、返回或提前退座操作。"""
    user = LightUser(req.username, "", "")
    success, message = perform_seat_action(user, req.action, req.resv_id)
    return {"success": success, "message": message}


@app.post("/api/auth/login")
async def direct_login(req: Credentials):
    """通过校内直连代理直接执行 CAS + 图书馆认证，并返回凭据与会话。"""
    import requests
    user = LightUser(req.username, req.edu_password, req.lib_password)
    auth = authenticate(user)
    if auth and auth.token:
        cookies_dict = requests.utils.dict_from_cookiejar(auth.session.cookies)
        return {
            "success": True,
            "message": "校内直连登录成功",
            "token": auth.token,
            "acc_no": auth.acc_no,
            "cookies": cookies_dict
        }
    return {
        "success": False,
        "message": getattr(user, "last_auth_error", None) or "校内直连认证失败",
    }


@app.get("/api/seats/query")
async def proxy_seat_query(roomId: int, date: str, username: Optional[str] = None):
    """代理查询阅览室所有座位状态与布局信息（校内直连，避免移动端直连WebVPN问题）。"""
    user = LightUser(username, "", "") if username else None
    if not user:
        tasks = list_all_tasks()
        if tasks:
            u_info = tasks[0]
            user = LightUser(u_info.get("username", ""), u_info.get("edu_password", ""), u_info.get("lib_password", ""))
        else:
            raise HTTPException(status_code=400, detail="未指定 username 且系统中无任何已保存的任务")

    data = get_room_seats(user, roomId, date)
    if data:
        return data
    raise HTTPException(status_code=500, detail="获取座位数据失败")


@app.get("/api/traffic")
async def proxy_traffic(username: Optional[str] = None):
    """代理查询图书馆实时在馆客流情况。"""
    user = LightUser(username, "", "") if username else None
    return get_library_traffic(user)




# ---------------------------------------------------------------------------
# 定时任务管理（需要 API Key）
# ---------------------------------------------------------------------------
@app.post("/api/task/register", dependencies=[Depends(verify_api_key)])
async def register_task(req: TaskRegisterRequest):
    """注册定时任务（保存为 JSON 文件）。"""
    task_data = {
        "username": req.username,
        "edu_password": req.edu_password,
        "lib_password": req.lib_password,
        "area": req.area,
        "seat_number": req.seat_number,
        "start_time": req.start_time,
        "end_time": req.end_time,
        "auto_reserve": req.auto_reserve,
        "prevent_late": req.prevent_late,
        "reserve_time": req.reserve_time or Config.DEFAULT_RESERVE_TIME,
        "auth_time": req.auth_time or Config.DEFAULT_AUTH_TIME,
        "weekly_plan": req.weekly_plan or {},
    }
    task_id = save_task(task_data)
    return {"success": True, "task_id": task_id, "message": "定时任务已注册"}


@app.put("/api/task/{task_id}", dependencies=[Depends(verify_api_key)])
async def update_task(task_id: str, req: TaskUpdateRequest):
    """更新定时任务。"""
    task = load_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="任务不存在")
    update_fields = req.model_dump(exclude_none=True)
    task.update(update_fields)
    save_task(task)
    return {"success": True, "message": "任务已更新"}


@app.delete("/api/task/{task_id}", dependencies=[Depends(verify_api_key)])
async def remove_task(task_id: str):
    """删除定时任务。"""
    if delete_task(task_id):
        return {"success": True, "message": "任务已删除"}
    else:
        raise HTTPException(status_code=404, detail="任务不存在")


@app.get("/api/task/{task_id}", dependencies=[Depends(verify_api_key)])
async def get_task_status(task_id: str):
    """查询任务状态和最近执行结果。"""
    task = load_task(task_id)
    if not task:
        raise HTTPException(status_code=404, detail="任务不存在")
    safe_task = {k: v for k, v in task.items() if "password" not in k}
    return {"success": True, "data": safe_task}


@app.get("/api/task/lookup/{username}", dependencies=[Depends(verify_api_key)])
async def lookup_task(username: str):
    """根据用户名查找任务 ID。"""
    task_id = generate_task_id(username)
    task = load_task(task_id)
    if not task:
        return {"success": False, "message": "未找到该用户的定时任务", "task_id": task_id}
    safe_task = {k: v for k, v in task.items() if "password" not in k}
    return {"success": True, "task_id": task_id, "data": safe_task}


@app.get("/api/areas", dependencies=[Depends(verify_api_key)])
async def list_areas():
    """获取所有可用的座位区域。"""
    areas = []
    for name, info in Config.SEAT_AREAS.items():
        areas.append({
            "name": name,
            "seats_count": info["seats_count"],
        })
    return {"success": True, "data": areas}


# ---------------------------------------------------------------------------
# 启动
# ---------------------------------------------------------------------------
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host=Config.HOST,
        port=Config.PORT,
        log_level="info",
    )
