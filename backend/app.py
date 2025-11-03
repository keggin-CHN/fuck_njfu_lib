import os
import logging
import csv
import io
from datetime import datetime, timedelta
import sqlite3
import pandas as pd
from multiprocessing import Pool, cpu_count, freeze_support
from functools import partial
from sqlalchemy import create_engine, text
from bs4 import BeautifulSoup
from flask import Flask, render_template, request, redirect, url_for, flash, jsonify, Response, stream_with_context
from flask_login import LoginManager, current_user, login_user, logout_user, login_required
from models import db, User, ReservationSetting, ReservationHistory, InviteCode, SystemSetting, Log, Traffic
from utils.auth_manager import encrypt_password, decrypt_password, AuthManager, LibraryAuthenticator, HttpClient
from utils.reservation import SeatReservation
from utils.date_utils import get_today_date, normalize_time_format, get_tomorrow_date
from utils.logger_utils import add_log
from utils.notification import NotificationService
from scheduler import setup_scheduler, scheduler, schedule_late_protection, schedule_late_check_task
from config import Config
from logging.handlers import TimedRotatingFileHandler
from functools import wraps

backend_dir = os.path.abspath(os.path.dirname(__file__))
root_dir = os.path.dirname(backend_dir)

template_folder = os.path.join(root_dir, 'frontend', 'templates')
static_folder = os.path.join(root_dir, 'frontend', 'static')
logs_dir = os.path.join(backend_dir, 'logs')

os.makedirs(os.path.join(static_folder, 'captcha'), exist_ok=True)
os.makedirs(logs_dir, exist_ok=True)

log_format = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
handlers = [
    TimedRotatingFileHandler(os.path.join(logs_dir, "library_reservation.log"),
                             when='midnight', interval=1, backupCount=30, encoding='utf-8'),
    logging.StreamHandler()
]
for handler in handlers:
    handler.setFormatter(logging.Formatter(log_format))

logging.basicConfig(level=logging.INFO, handlers=handlers)
logger = logging.getLogger(__name__)

app = Flask(__name__, template_folder=template_folder, static_folder=static_folder)
app.config.from_object(Config)
db.init_app(app)

login_manager = LoginManager()
login_manager.init_app(app)
login_manager.login_view = 'login'
login_manager.login_message = '请先登录'
scheduler.app = app

query_progress = {}

with app.app_context():
    db.create_all()

    try:
        db_path = os.path.join(app.instance_path, app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', ''))
        if os.path.isfile(db_path):
            with sqlite3.connect(db_path) as conn:
                conn.row_factory = sqlite3.Row
                cursor = conn.cursor()
                cursor.execute("PRAGMA table_info(reservation_setting)")
                cols = [row['name'] for row in cursor.fetchall()]
                if 'auto_find_seat' not in cols:
                    logger.info("检测到缺少列 auto_find_seat，正在迁移数据库架构...")
                    cursor.execute("ALTER TABLE reservation_setting ADD COLUMN auto_find_seat BOOLEAN DEFAULT 0")
                    conn.commit()
                    logger.info("已添加列 auto_find_seat")
    except Exception as e:
        logger.error(f"检查/迁移 auto_find_seat 列失败: {str(e)}")
    
    try:
        db_path = os.path.join(app.instance_path, app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', ''))
        if os.path.isfile(db_path):
            with sqlite3.connect(db_path) as conn:
                conn.row_factory = sqlite3.Row
                cursor = conn.cursor()
                cursor.execute("PRAGMA table_info(reservation_history)")
                cols = [row['name'] for row in cursor.fetchall()]
                if 'is_auto_find' not in cols:
                    logger.info("检测到缺少列 is_auto_find，正在迁移数据库架构...")
                    cursor.execute("ALTER TABLE reservation_history ADD COLUMN is_auto_find BOOLEAN DEFAULT 0")
                    conn.commit()
                    logger.info("已添加列 is_auto_find")
    except Exception as e:
        logger.error(f"检查/迁移 is_auto_find 列失败: {str(e)}")
    
    try:
        db_path = os.path.join(app.instance_path, app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', ''))
        if os.path.isfile(db_path):
            with sqlite3.connect(db_path) as conn:
                conn.row_factory = sqlite3.Row
                cursor = conn.cursor()
                cursor.execute("PRAGMA table_info(reservation_history)")
                cols = [row['name'] for row in cursor.fetchall()]
                if 'notification_sent' not in cols:
                    logger.info("检测到缺少列 notification_sent，正在迁移数据库架构...")
                    cursor.execute("ALTER TABLE reservation_history ADD COLUMN notification_sent BOOLEAN DEFAULT 0")
                    conn.commit()
                    logger.info("已添加列 notification_sent")
    except Exception as e:
        logger.error(f"检查/迁移 notification_sent 列失败: {str(e)}")
    
    if User.query.count() == 0:
        logger.info("数据库为空，进行初始化设置...")
        
        if not SystemSetting.query.filter_by(key='invite_code_required').first():
            SystemSetting.set_setting(
                'invite_code_required',
                True,
                '是否启用邀请码注册模式'
            )
            logger.info("已初始化邀请码模式设置为启用")
        
        logger.info("数据库初始化完成，等待第一个用户注册成为管理员")

setup_scheduler(app)


def admin_required(f):
    @wraps(f)
    def decorated_function(*args, **kwargs):
        if not current_user.is_admin:
            flash('您没有管理员权限')
            return redirect(url_for('dashboard'))
        return f(*args, **kwargs)

    return decorated_function


def reset_user_ids():
    try:
        tables = {"user": "user", "setting": "reservation_setting", "history": "reservation_history"}
        db_path = os.path.join(app.instance_path, app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', ''))

        if not os.path.isfile(db_path):
            logger.error(f"数据库文件不存在: {db_path}")
            return

        with sqlite3.connect(db_path) as conn:
            conn.row_factory = sqlite3.Row
            cursor = conn.cursor()

            cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name=?", (tables["user"],))
            if not cursor.fetchone():
                logger.error("用户表不存在")
                return

            cursor.execute(f"SELECT id, username FROM {tables['user']} ORDER BY id")
            users = cursor.fetchall()

            if not users:
                logger.info("数据库中没有用户")
                return

            id_mapping = {user['id']: new_id for new_id, user in enumerate(users, 1) if user['id'] != new_id}

            if not id_mapping:
                logger.info("所有用户ID已连续，无需修改")
                return

            cursor.execute("BEGIN TRANSACTION")
            cursor.execute("PRAGMA foreign_keys = OFF")

            try:
                for table_key in ["setting", "history"]:
                    table = tables[table_key]
                    cursor.execute(f"SELECT name FROM sqlite_master WHERE type='table' AND name=?", (table,))
                    if cursor.fetchone():
                        for old_id, new_id in id_mapping.items():
                            cursor.execute(f"UPDATE {table} SET user_id = ? WHERE user_id = ?", (new_id, old_id))

                tmp_id_start = 10000
                for old_id, new_id in id_mapping.items():
                    cursor.execute(f"UPDATE {tables['user']} SET id = ? WHERE id = ?", (tmp_id_start + old_id, old_id))

                for old_id, new_id in id_mapping.items():
                    cursor.execute(f"UPDATE {tables['user']} SET id = ? WHERE id = ?", (new_id, tmp_id_start + old_id))

                cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='sqlite_sequence'")
                if cursor.fetchone():
                    cursor.execute(f"DELETE FROM sqlite_sequence WHERE name = ?", (tables['user'],))
                    cursor.execute(f"INSERT INTO sqlite_sequence (name, seq) VALUES (?, ?)",
                                   (tables['user'], len(users)))

                cursor.execute("PRAGMA foreign_keys = ON")
                cursor.execute("COMMIT")
                logger.info("用户ID已成功重置为连续值")
            except Exception as e:
                cursor.execute("ROLLBACK")
                raise e
    except Exception as e:
        logger.error(f"重置用户ID时发生错误: {str(e)}")


@app.context_processor
def inject_now():
    now = datetime.now(Config.TIMEZONE)
    tomorrow = now + timedelta(days=1)
    return {'now': now, 'tomorrow': tomorrow}


@login_manager.user_loader
def load_user(user_id):
    return User.query.get(int(user_id))


@app.context_processor
def inject_announcement():
    with app.app_context():
        announcement = SystemSetting.query.filter_by(key='announcement').first()
        if announcement:
            return {'announcement': announcement}
        return {'announcement': {'updated_at': ''}}


@app.route('/')
def index():
    return redirect(url_for('dashboard' if current_user.is_authenticated else 'login'))


@app.route('/login', methods=['GET', 'POST'])
def login():
    if current_user.is_authenticated:
        return redirect(url_for('dashboard'))

    if request.method == 'POST':
        username = request.form.get('username')
        password = request.form.get('password')
        user = User.query.filter_by(username=username).first()

        if user is None:
            flash('账户不存在，请先注册')
            return redirect(url_for('register'))

        if not user.check_password(password):
            flash('用户名或密码错误')
            add_log(f"用户 {username} 登录失败：密码错误", user=user, response_code=401)
            return render_template('login.html')

        login_user(user, remember=True)
        add_log(f"用户 {user.username} 登录成功", user=user)
        return redirect(url_for('dashboard'))

    return render_template('login.html')


@app.route('/register', methods=['GET', 'POST'])
def register():
    if current_user.is_authenticated:
        return redirect(url_for('dashboard'))

    if request.method == 'POST':
        username = request.form.get('username')
        password = request.form.get('password')
        edu_password = request.form.get('edu_password')
        lib_password = request.form.get('lib_password')
        captcha = request.form.get('captcha')
        invite_code = request.form.get('invite_code')

        if User.query.filter_by(username=username).first() is not None:
            flash('此学号已被注册')
            return render_template('register.html')

        is_first_user = User.query.count() == 0
        
        invite_code_required = SystemSetting.is_invite_code_required()
        
        if not is_first_user and invite_code_required:
            if not invite_code:
                flash('请输入邀请码')
                return render_template('register.html', username=username, remember_form=True)
            
            is_valid, result = InviteCode.validate_code(invite_code)
            if not is_valid:
                flash(result)
                return render_template('register.html', username=username, remember_form=True)
            
            invite_code_obj = result

        authenticator = LibraryAuthenticator(username, edu_password, lib_password)

        if captcha:
            auth_success, message = authenticator.authenticate_with_captcha(captcha)
            if not auth_success:
                if "验证码错误" in message:
                    flash('验证码错误，请重新输入')
                    return render_template('register.html', need_captcha=True,
                                           username=username, remember_form=True)
                else:
                    flash(message)
                    return render_template('register.html')
        else:
            client_ticket = authenticator.get_initial_client_ticket()
            if client_ticket:
                login_url = HttpClient.get_edu_url(
                    "authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
                )
                response = HttpClient.get(login_url, cookies={"my_client_ticket": client_ticket})
                if response and response.status_code == 200:
                    soup = BeautifulSoup(response.text, "html.parser")
                    salt_input = soup.find("input", {"id": "pwdDefaultEncryptSalt"})
                    salt = salt_input["value"] if salt_input else ""

                    if authenticator.check_need_captcha(username, salt, client_ticket):
                        flash('需要输入验证码以完成认证')
                        return render_template('register.html', need_captcha=True,
                                               username=username, remember_form=True)

            auth_result, need_captcha, error_msg = authenticator.authenticate()
            if not auth_result:
                if need_captcha:
                    flash('需要输入验证码以完成认证')
                    return render_template('register.html', need_captcha=True,
                                           username=username, remember_form=True)
                else:
                    flash(error_msg or '统一认证或图书馆密码错误，请重试')
                    return render_template('register.html')

        is_admin = is_first_user
        user = User(username=username, is_admin=is_admin)
        user.set_password(password)
        user.edu_password = encrypt_password(edu_password)
        user.lib_password = encrypt_password(lib_password)

        db.session.add(user)
        db.session.commit()

        if not is_first_user and invite_code_required:
            invite_code_obj.use_code(user.id)

        if is_admin:
            logger.info(f"用户 {username} 被设置为管理员（第一个注册的用户）")
            flash('您已被设置为管理员（第一个注册的用户）')
            add_log(f"新用户 {username} 注册成功并成为管理员", user=user)
        else:
            add_log(f"新用户 {username} 注册成功", user=user)

        flash('注册成功，请登录')
        return redirect(url_for('login'))

    return render_template('register.html')


@app.route('/logout')
@login_required
def logout():
    add_log(f"用户 {current_user.username} 退出登录", user=current_user)
    logout_user()
    return redirect(url_for('login'))


@app.route('/dashboard')
@login_required
def dashboard():
    now = datetime.now()
    tomorrow = now + timedelta(days=1)
    reservation_setting = ReservationSetting.query.filter_by(user_id=current_user.id).first()
    histories = ReservationHistory.query.filter_by(user_id=current_user.id).order_by(
        ReservationHistory.created_at.desc()).limit(20).all()
    need_captcha = request.args.get('need_captcha', None)

    reservations = []
    error_message = None
    try:
        authenticator = AuthManager.get_authenticator(current_user)
        if not authenticator:
            error_message = "认证失败，请检查图书馆账号密码"
        else:
            reservation_handler = SeatReservation(current_user, authenticator=authenticator)
            today_str = get_today_date()
            tomorrow_str = get_tomorrow_date()
            reservations_data = reservation_handler.get_reservations(begin_date=today_str, end_date=tomorrow_str)

            if reservations_data is None:
                error_message = "获取预约信息失败，请稍后重试"
                reservations_data = []
            
            for resv in reservations_data:
                day_type = "今日"
                if resv.get("resvBeginTime"):
                    resv_date = datetime.fromtimestamp(resv.get("resvBeginTime") / 1000).strftime("%Y-%m-%d")
                    if resv_date == tomorrow_str:
                        day_type = "明日"
                
                formatted = format_reservation(resv, day_type)
                if formatted:
                    reservations.append(formatted)

    except Exception as e:
        logger.error(f"在 dashboard 加载预约信息时出错: {str(e)}")
        error_message = "加载预约信息时发生内部错误"

    latest_traffic = Traffic.get_latest()
    traffic_data = {
        'count': 0,
        'total': 2749,
        'percentage': 0,
        'updated_at': 'N/A'
    }
    if latest_traffic:
        from datetime import datetime as dt
        used_capacity = latest_traffic.count
        traffic_data.update({
            'count': used_capacity,
            'percentage': round(used_capacity / traffic_data['total'] * 100, 1),
            'updated_at': dt.fromtimestamp(latest_traffic.timestamp).strftime('%H:%M:%S')
        })

    return render_template('dashboard.html', now=now, tomorrow=tomorrow,
                           reservation_setting=reservation_setting,
                           histories=histories,
                           seat_areas=Config.SEAT_AREAS,
                           captcha_needed=bool(need_captcha),
                           need_captcha_type=need_captcha,
                           reservations=reservations,
                           reservations_error=error_message,
                           traffic_data=traffic_data)


@app.route('/update_reservation_settings', methods=['POST'])
@login_required
def update_reservation_settings():
    area = request.form.get('area')
    seat_number = request.form.get('seat_number', type=int)
    start_time = request.form.get('start_time')
    end_time = request.form.get('end_time')
    auto_reserve = 'auto_reserve' in request.form
    prevent_late = 'prevent_late' in request.form
    auto_find_seat = 'auto_find_seat' in request.form

    if not validate_reservation_params(area, seat_number, start_time, end_time):
        return redirect(url_for('dashboard'))

    setting = ReservationSetting.query.filter_by(user_id=current_user.id).first()
    old_auto_reserve = False
    old_prevent_late = False

    if setting:
        old_auto_reserve = setting.auto_reserve
        old_prevent_late = setting.prevent_late
        setting.area = area
        setting.seat_number = seat_number
        setting.start_time = start_time
        setting.end_time = end_time
        setting.auto_reserve = auto_reserve
        setting.prevent_late = prevent_late
        setting.auto_find_seat = auto_find_seat
    else:
        setting = ReservationSetting(
            user_id=current_user.id,
            area=area,
            seat_number=seat_number,
            start_time=start_time,
            end_time=end_time,
            auto_reserve=auto_reserve,
            prevent_late=prevent_late,
            auto_find_seat=auto_find_seat
        )
        db.session.add(setting)

    db.session.commit()

    if old_prevent_late != prevent_late:
        schedule_late_protection(current_user.id, prevent_late)

    try:
        NotificationService.send_setting_update_notification(current_user, setting)
    except Exception as e:
        logger.error(f"发送设置更新通知失败: {str(e)}")

    add_log(f"用户 {current_user.username} 更新了预约设置", user=current_user)
    flash('预约设置已更新')
    return redirect(url_for('dashboard'))


def validate_reservation_params(area, seat_number, start_time, end_time):
    if not all([area, seat_number, start_time, end_time]):
        flash('所有字段均为必填项')
        return False

    if area not in Config.SEAT_AREAS or not (1 <= seat_number <= Config.SEAT_AREAS[area]["seats_count"]):
        flash('无效的区域或座位号')
        return False

    try:
        start_time_obj = datetime.strptime(start_time, '%H:%M')
        end_time_obj = datetime.strptime(end_time, '%H:%M')

        if start_time_obj >= end_time_obj:
            flash('结束时间必须晚于开始时间')
            return False
        
        if (end_time_obj - start_time_obj).seconds / 3600 < 2:
            flash('预约时长必须至少为2小时')
            return False
            
        return True
    except ValueError:
        flash('无效的时间格式')
        return False


@app.route('/immediate_reserve', methods=['POST'])
@login_required
def immediate_reserve():
    setting = ReservationSetting.query.filter_by(user_id=current_user.id).first()
    reserve_date = request.form.get('reserve_date', 'today')

    if not setting:
        message = '请先设置预约信息'
        flash(message)
        add_log(f"用户 {current_user.username} 尝试快速预约失败: {message}", user=current_user, response_code=400)
        history = ReservationHistory(
            user_id=current_user.id, status='失败', message=f'预约失败: {message}',
            reserve_date=datetime.now(Config.TIMEZONE).date()
        )
        db.session.add(history)
        db.session.commit()
        return redirect(url_for('dashboard'))

    seat_id = Config.get_seat_id(setting.area, setting.seat_number)
    if not seat_id:
        message = f'无效的区域或座位号 ({setting.area} - {setting.seat_number}号)'
        flash(message)
        add_log(f"用户 {current_user.username} 尝试快速预约失败: {message}", user=current_user, response_code=400)
        history = ReservationHistory(
            user_id=current_user.id, status='失败', message=f'预约失败: {message}',
            reserve_date=datetime.now(Config.TIMEZONE).date(),
            area=setting.area, seat_number=setting.seat_number
        )
        db.session.add(history)
        db.session.commit()
        return redirect(url_for('dashboard'))

    authenticator = AuthManager.get_authenticator(current_user)
    if not authenticator:
        message = '认证失败，请检查图书馆账号密码'
        flash(message)
        add_log(f"用户 {current_user.username} 尝试快速预约失败: {message}", user=current_user, response_code=401)
        history = ReservationHistory(
            user_id=current_user.id, status=ReservationHistory.STATUS_AUTH_FAILED,
            message=f'预约失败: {message}',
            reserve_date=datetime.now(Config.TIMEZONE).date(),
            area=setting.area, seat_number=setting.seat_number, seat_id=seat_id
        )
        db.session.add(history)
        db.session.commit()
        return redirect(url_for('dashboard'))

    reservation = SeatReservation(current_user, authenticator=authenticator)

    start_time = normalize_time_format(setting.start_time)
    date_str = get_today_date() if reserve_date == 'today' else None

    ok, message = reservation.reserve_seat(
        setting.area,
        setting.seat_number,
        seat_id,
        date_str=date_str,
        start_time=start_time
    )

    if not ok and setting.auto_find_seat and message and ('已被预约' in message):
        flash('目标座位在该时间段已被预约，已为您推荐同区域可用座位，请选择。')
        return redirect(url_for('dashboard', suggest='1', date=reserve_date))

    if ok and setting.prevent_late and reserve_date == 'today':
        try:
            begin_dt = datetime.strptime(f"{get_today_date()} {start_time}", "%Y-%m-%d %H:%M:%S")
            schedule_late_check_task(current_user, begin_dt)
        except Exception as e:
            logger.error(f"为用户 {current_user.username} 调度迟到检查失败: {str(e)}")
    flash(f'预约{"今天" if reserve_date == "today" else "明天"}座位{"成功！" if ok else "失败，请查看日志了解详情"}')
    return redirect(url_for('dashboard'))




def _map_config_area_to_query_area(area_name: str) -> str:
    if not area_name:
        return area_name
    name = area_name.replace('楼', '层')
    return name

@app.route('/api/suggest_alternative_seats')
@login_required
def api_suggest_alternative_seats():
    try:
        from utils.seat_query import SeatQueryService
        from utils.date_utils import get_today_date, get_tomorrow_date, normalize_time_format

        reserve_date = request.args.get('reserve_date', 'today')
        days_offset = 0 if reserve_date == 'today' else 1

        setting = ReservationSetting.query.filter_by(user_id=current_user.id).first()
        if not setting:
            return jsonify({'success': False, 'message': '未找到预约设置'}), 400

        authenticator = AuthManager.get_authenticator(current_user)
        if not authenticator or not authenticator.is_valid():
            return jsonify({'success': False, 'message': '认证失败，请重新登录'}), 401

        query_area = _map_config_area_to_query_area(setting.area)
        if query_area not in SeatQueryService.AREAS:
            return jsonify({'success': False, 'message': f'区域映射失败：{setting.area} -> {query_area}'}), 400

        room_id = SeatQueryService.AREAS[query_area]['roomId']
        date_str_query = SeatQueryService.get_date_string(days_offset)

        seats_data = SeatQueryService.get_seats_data(authenticator, room_id, date_str_query)
        if seats_data is None:
            return jsonify({'success': False, 'message': '获取座位数据失败'}), 500

        ymd = get_today_date() if days_offset == 0 else get_tomorrow_date()
        start = normalize_time_format(setting.start_time)
        end = normalize_time_format(setting.end_time)
        begin_dt = datetime.strptime(f"{ymd} {start}", "%Y-%m-%d %H:%M:%S")
        end_dt = datetime.strptime(f"{ymd} {end}", "%Y-%m-%d %H:%M:%S")
        begin_ms = int(begin_dt.timestamp() * 1000)
        end_ms = int(end_dt.timestamp() * 1000)

        def no_conflict(reservations):
            for r in reservations or []:
                s = r.get('startTime')
                e = r.get('endTime')
                if s is None or e is None:
                    return False
                if not (end_ms <= s or begin_ms >= e):
                    return False
            return True

        alternatives = []
        for seat in seats_data:
            if no_conflict(seat.get('resvInfo', [])):
                alternatives.append({
                    'devId': seat.get('devId'),
                    'devName': seat.get('devName')
                })
                if len(alternatives) >= 50:
                    break

        return jsonify({'success': True, 'alternatives': alternatives})
    except Exception as e:
        logger.error(f"获取同区域可用座位失败: {str(e)}")
        return jsonify({'success': False, 'message': '内部错误'}), 500

@app.route('/reserve_alternative', methods=['POST'])
@login_required
def reserve_alternative():
    try:
        from utils.date_utils import get_today_date, get_tomorrow_date, normalize_time_format

        dev_id = request.form.get('devId', type=int)
        reserve_date = request.form.get('reserve_date', 'today')

        if not dev_id:
            return jsonify({'success': False, 'message': '缺少参数 devId'}), 400

        area_name = None
        seat_number = None
        for area, info in Config.SEAT_AREAS.items():
            start_id = info['first_seat_id']
            end_id = start_id + info['seats_count'] - 1
            if start_id <= dev_id <= end_id:
                area_name = area
                seat_number = dev_id - start_id + 1
                break

        if not area_name or not seat_number:
            return jsonify({'success': False, 'message': '无法根据 devId 识别区域与座位号'}), 400

        setting = ReservationSetting.query.filter_by(user_id=current_user.id).first()
        if not setting:
            return jsonify({'success': False, 'message': '未找到预约设置'}), 400

        authenticator = AuthManager.get_authenticator(current_user)
        if not authenticator:
            return jsonify({'success': False, 'message': '认证失败，请检查图书馆账号密码'}), 401

        date_str = get_today_date() if reserve_date == 'today' else get_tomorrow_date()
        start_time = normalize_time_format(setting.start_time)

        reservation = SeatReservation(current_user, authenticator=authenticator)
        ok, message = reservation.reserve_seat(
            area_name, seat_number, dev_id, date_str=date_str, start_time=start_time, is_auto_find=True
        )
        
        if ok and setting.prevent_late and reserve_date == 'today':
            try:
                begin_dt = datetime.strptime(f"{date_str} {start_time}", "%Y-%m-%d %H:%M:%S")
                schedule_late_check_task(current_user, begin_dt)
            except Exception as e:
                logger.error(f"为用户 {current_user.username} 调度迟到检查失败: {str(e)}")
        
        return jsonify({'success': bool(ok), 'message': message})
    except Exception as e:
        logger.error(f"预约备选座位失败: {str(e)}")
        return jsonify({'success': False, 'message': '内部错误'}), 500


@app.route('/captcha')
def get_captcha():
    try:
        authenticator = LibraryAuthenticator("temp", "", "")
        captcha_data = authenticator.get_captcha_image()

        if captcha_data:
            return Response(captcha_data, mimetype='image/jpeg')
        return "获取验证码失败", 500
    except Exception as e:
        logger.error(f"获取验证码图片出错: {str(e)}")
        return "获取验证码失败", 500


@app.route('/logs')
@login_required
@admin_required
def view_logs():
    try:
        page = request.args.get('page', 1, type=int)
        search_query = request.args.get('search', '')
        user_id_query = request.args.get('user_id', '')
        start_date_query = request.args.get('start_date', '')

        query = Log.query.order_by(Log.created_at.desc())

        if search_query:
            search_term = f"%{search_query}%"
            query = query.filter(
                db.or_(
                    Log.ip_address.like(search_term),
                    Log.action.like(search_term),
                    Log.user_agent.like(search_term),
                    Log.response_content.like(search_term),
                    Log.error_message.like(search_term)
                )
            )

        if user_id_query:
            query = query.filter(Log.user_id == user_id_query)

        if start_date_query:
            try:
                start_date = datetime.strptime(start_date_query, '%Y-%m-%d')
                query = query.filter(Log.created_at >= start_date)
            except ValueError:
                flash('日期格式无效，请使用 YYYY-MM-DD 格式')

        logs = query.paginate(page=page, per_page=25, error_out=False)
        return render_template('logs.html', logs=logs)
    except Exception as e:
        import traceback
        error_detail = traceback.format_exc()
        logger.error(f"查看日志出错: {str(e)}\n{error_detail}")
        flash(f'加载日志失败: {str(e)}', 'error')
        return render_template('logs.html', logs=None), 500


@app.route('/api/announcement')
def get_announcement():
    try:
        announcement = SystemSetting.query.filter_by(key='announcement').first()
        if announcement:
            return jsonify({
                'success': True,
                'content': announcement.value,
                'updated_at': announcement.updated_at.strftime('%Y-%m-%d %H:%M:%S') if announcement.updated_at else ''
            })
        return jsonify({'success': True, 'content': '', 'updated_at': ''})
    except Exception as e:
        logger.error(f"获取公告失败: {str(e)}")
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/admin/announcement', methods=['POST'])
@login_required
@admin_required
def update_announcement():
    try:
        content = request.form.get('content', '').strip()

        announcement = SystemSetting.query.filter_by(key='announcement').first()
        if announcement:
            announcement.value = content
            announcement.updated_by = current_user.id
        else:
            announcement = SystemSetting(
                key='announcement',
                value=content,
                description='系统公告',
                updated_by=current_user.id
            )
            db.session.add(announcement)

        db.session.commit()
        add_log(f"管理员 {current_user.username} 更新了系统公告", user=current_user)
        flash('公告更新成功', 'success')
        return redirect(url_for('admin'))
    except Exception as e:
        db.session.rollback()
        logger.error(f"更新公告失败: {str(e)}")
        flash(f'更新公告失败: {str(e)}', 'error')
        return redirect(url_for('admin'))


@app.route('/admin')
@login_required
@admin_required
def admin():
    users = User.query.all()
    
    invite_codes = InviteCode.query.order_by(InviteCode.created_at.desc()).all()
    
    invite_stats = {
        'unused': InviteCode.query.filter_by(is_used=False).count(),
        'used': InviteCode.query.filter_by(is_used=True).count()
    }
    
    invite_code_required = SystemSetting.is_invite_code_required()
    return render_template('admin.html',
                         users=users,
                         invite_codes=invite_codes,
                         invite_stats=invite_stats,
                         invite_code_required=invite_code_required)


@app.route('/toggle_invite_code_mode', methods=['POST'])
@login_required
@admin_required
def toggle_invite_code_mode():
    try:
        current_mode = SystemSetting.is_invite_code_required()
        new_mode = not current_mode
        
        SystemSetting.set_setting(
            'invite_code_required',
            new_mode,
            '是否启用邀请码注册模式',
            current_user.id
        )
        
        mode_text = "启用" if new_mode else "关闭"
        logger.info(f"管理员 {current_user.username} {mode_text}了邀请码模式")
        
        return jsonify({
            'success': True,
            'new_mode': new_mode,
            'message': f'邀请码模式已{mode_text}'
        })
    except Exception as e:
        logger.error(f"切换邀请码模式失败: {str(e)}")
        return jsonify({
            'success': False,
            'message': '切换邀请码模式失败'
        }), 500




@app.route('/generate_invite_code', methods=['POST'])
@login_required
@admin_required
def generate_invite_code():
    try:
        invite_code = InviteCode.create_invite_code(current_user.id)
        add_log(f"管理员 {current_user.username} 生成了新的邀请码 {invite_code.code}", user=current_user)
        return jsonify({
            'success': True,
            'code': invite_code.code,
            'message': '邀请码生成成功'
        })
    except Exception as e:
        logger.error(f"生成邀请码失败: {str(e)}")
        return jsonify({
            'success': False,
            'message': '生成邀请码失败'
        }), 500


@app.route('/delete_invite_code/<int:code_id>', methods=['POST'])
@login_required
@admin_required
def delete_invite_code(code_id):
    invite_code = InviteCode.query.get_or_404(code_id)
    
    if invite_code.is_used:
        flash('不能删除已使用的邀请码')
    else:
        code_str = invite_code.code
        db.session.delete(invite_code)
        db.session.commit()
        add_log(f"管理员 {current_user.username} 删除了邀请码 {code_str}", user=current_user)
        flash('邀请码已删除')
    
    return redirect(url_for('admin'))


@app.route('/admin/clear_logs', methods=['POST'])
@login_required
@admin_required
def clear_all_logs():
    try:
        num_deleted = db.session.query(Log).delete()
        db.session.commit()
        add_log(f"管理员 {current_user.username} 清空了 {num_deleted} 条操作日志", user=current_user)
        flash(f'已成功删除 {num_deleted} 条操作日志', 'success')
    except Exception as e:
        db.session.rollback()
        logger.error(f"清空操作日志失败: {str(e)}")
        flash(f'清空操作日志失败: {str(e)}', 'error')
    return redirect(url_for('admin'))


@app.route('/update_user/<int:user_id>', methods=['POST'])
@login_required
@admin_required
def update_user(user_id):
    user = User.query.get_or_404(user_id)
    action = request.form.get('action')

    if action == 'toggle_admin':
        if user.id == current_user.id:
            flash('不能修改自己的管理员权限')
        else:
            action_text = "授予" if not user.is_admin else "取消"
            user.is_admin = not user.is_admin
            db.session.commit()
            add_log(f"管理员 {current_user.username} {action_text}了用户 {user.username} 的管理员权限", user=current_user)
            flash(f'已{action_text}{user.username}的管理员权限')

    elif action == 'reset_password':
        user.set_password('123456')
        db.session.commit()
        add_log(f"管理员 {current_user.username} 重置了用户 {user.username} 的密码", user=current_user)
        flash(f'已重置{user.username}的密码为123456')

    elif action == 'delete':
        if user.id == current_user.id:
            flash('不能删除自己的账户')
        else:
            username_to_delete = user.username
            ReservationSetting.query.filter_by(user_id=user.id).delete()
            ReservationHistory.query.filter_by(user_id=user.id).delete()
            AuthManager.clear_authenticator(user.id)
            db.session.delete(user)
            db.session.commit()
            add_log(f"管理员 {current_user.username} 删除了用户 {username_to_delete}", user=current_user)
            flash(f'已删除用户{username_to_delete}')

    return redirect(url_for('admin'))


@app.route('/change_password', methods=['POST'])
@login_required
def change_password():
    old_password = request.form.get('old_password')
    new_password = request.form.get('new_password')
    confirm_password = request.form.get('confirm_password')
    password_type = request.form.get('password_type')
    captcha = request.form.get('captcha')

    if new_password != confirm_password:
        flash('两次输入的新密码不一致')
        return redirect(url_for('dashboard'))

    if password_type == 'website':
        if not current_user.check_password(old_password):
            flash('旧密码不正确')
            return redirect(url_for('dashboard'))

        current_user.set_password(new_password)
        flash('网站密码已修改')

    elif password_type in ['edu', 'lib']:
        if captcha:
            if password_type == 'edu':
                authenticator = LibraryAuthenticator(current_user.username, new_password,
                                                     decrypt_password(current_user.lib_password))
            else:
                authenticator = LibraryAuthenticator(current_user.username,
                                                     decrypt_password(current_user.edu_password),
                                                     new_password)

            success, message = authenticator.authenticate_with_captcha(captcha)

            if not success:
                flash(message)
                logger.warning(f"验证密码失败: {message}")
                add_log(f"用户 {current_user.username} 修改{password_type}密码失败: {message}", user=current_user, response_code=401, error_message=message)
                return redirect(url_for('dashboard', need_captcha=password_type))
        else:
            if password_type == 'edu':
                authenticator = LibraryAuthenticator(current_user.username, new_password,
                                                     decrypt_password(current_user.lib_password))
            else:
                authenticator = LibraryAuthenticator(current_user.username,
                                                     decrypt_password(current_user.edu_password),
                                                     new_password)

            client_ticket = authenticator.get_initial_client_ticket()
            login_url = HttpClient.get_edu_url(
                "authserver/login?service=https%3A%2F%2Fwebvpn.njfu.edu.cn%2Frump_frontend%2FloginFromCas%2F"
            )
            response = HttpClient.get(login_url, cookies={"my_client_ticket": client_ticket})

            if response and response.status_code == 200:
                soup = BeautifulSoup(response.text, "html.parser")
                salt_input = soup.find("input", {"id": "pwdDefaultEncryptSalt"})
                salt = salt_input["value"] if salt_input else ""

                if authenticator.check_need_captcha(current_user.username, salt, client_ticket):
                    flash('需要输入验证码以完成认证')
                    return redirect(url_for('dashboard', need_captcha=password_type))

            auth_result, need_captcha, error_msg = authenticator.authenticate()

            if not auth_result:
                if need_captcha:
                    flash('需要输入验证码以完成认证')
                    return redirect(url_for('dashboard', need_captcha=password_type))
                else:
                    flash(error_msg or '认证失败，请检查密码')
                    return redirect(url_for('dashboard'))

        if password_type == 'edu':
            current_user.edu_password = encrypt_password(new_password)
            flash('统一认证密码已修改')
        else:
            current_user.lib_password = encrypt_password(new_password)
            AuthManager.clear_authenticator(current_user.id)
            flash('图书馆密码已修改')
    else:
        flash('无效的密码类型')
        return redirect(url_for('dashboard'))

    db.session.commit()
    return redirect(url_for('dashboard'))


@app.route('/update_notification_settings', methods=['POST'])
@login_required
def update_notification_settings():
    notification_type = request.form.get('notification_type', 'none')
    webhook_url = request.form.get('webhook_url', '').strip()

    if notification_type not in ['none', 'wechat', 'telegram', 'feishu', 'dingtalk']:
        flash('无效的通知类型')
        return redirect(url_for('dashboard'))

    if notification_type != 'none' and not webhook_url:
        flash('请输入Webhook URL')
        return redirect(url_for('dashboard'))

    current_user.notification_type = notification_type
    current_user.webhook_url = webhook_url if notification_type != 'none' else None

    db.session.commit()

    add_log(f"用户 {current_user.username} 更新了通知设置: {notification_type}", user=current_user)

    if notification_type == 'none':
        flash('已关闭通知功能')
    else:
        flash(f'通知设置已更新为 {"企业微信" if notification_type == "wechat" else "Telegram"}')

    return redirect(url_for('dashboard'))


@app.route('/test_notification', methods=['POST'])
@login_required
def test_notification():
    try:
        data = request.get_json()
        notification_type = data.get('notification_type')
        webhook_url = data.get('webhook_url')

        if not notification_type or not webhook_url:
            return jsonify({'success': False, 'message': '参数不完整'})

        from types import SimpleNamespace
        temp_user = SimpleNamespace(
            username=current_user.username,
            notification_type=notification_type,
            webhook_url=webhook_url
        )

        success, message = NotificationService.test_notification(temp_user)

        if success:
            add_log(f"用户 {current_user.username} 测试通知发送成功", user=current_user)
            return jsonify({'success': True, 'message': '测试通知发送成功'})
        else:
            add_log(f"用户 {current_user.username} 测试通知发送失败: {message}",
                   user=current_user, response_code=400, error_message=message)
            return jsonify({'success': False, 'message': message})

    except Exception as e:
        logger.error(f"测试通知时发生错误: {str(e)}")
        return jsonify({'success': False, 'message': str(e)}), 500


@app.route('/delete_history/<int:history_id>', methods=['POST'])
@login_required
def delete_history(history_id):
    history = ReservationHistory.query.get_or_404(history_id)

    if history.user_id != current_user.id:
        flash('您没有权限删除此记录')
        return redirect(url_for('dashboard'))

    db.session.delete(history)
    db.session.commit()
    flash('记录已删除')
    return redirect(url_for('dashboard'))


@app.route('/clear_history', methods=['POST'])
@login_required
def clear_history():
    ReservationHistory.query.filter_by(user_id=current_user.id).delete()
    db.session.commit()
    flash('所有预约记录已清空')
    return redirect(url_for('dashboard'))


@app.route('/check_first_user')
def check_first_user():
    is_first_user = User.query.count() == 0
    return jsonify({"is_first_user": is_first_user})


@app.route('/check_invite_code_required')
def check_invite_code_required():
    is_first_user = User.query.count() == 0
    invite_code_required = SystemSetting.is_invite_code_required()

    need_invite_code = not is_first_user and invite_code_required

    return jsonify({
        "is_first_user": is_first_user,
        "invite_code_required": invite_code_required,
        "need_invite_code": need_invite_code
    })


@app.route('/api/traffic/latest')
def get_latest_traffic():
    latest = Traffic.get_latest()
    total_capacity = 2749
    if latest:
        from datetime import datetime
        used_capacity = latest.count
        time_str = datetime.fromtimestamp(latest.timestamp).strftime('%H:%M:%S')
        update_time = datetime.fromtimestamp(latest.timestamp).strftime('%Y-%m-%d %H:%M:%S')
        return jsonify({
            'success': True,
            'current_count': used_capacity,
            'total_capacity': total_capacity,
            'timestamp': latest.timestamp,
            'count': used_capacity,
            'percentage': round(used_capacity / total_capacity * 100, 1) if total_capacity > 0 else 0,
            'time': time_str,
            'updated_at': update_time
        })
    return jsonify({'success': False, 'message': '暂无流量数据', 'total_capacity': total_capacity})


@app.route('/api/traffic/history')
def get_traffic_history():
    from datetime import datetime
    hours = request.args.get('hours', 24, type=int)
    traffic_list = Traffic.get_recent(hours=hours)

    total_capacity = 2749
    data = [{
        'timestamp': t.timestamp,
        'count': t.count,
        'time': datetime.fromtimestamp(t.timestamp).strftime('%H:%M')
    } for t in traffic_list]

    return jsonify({
        'success': True,
        'data': data,
        'total_capacity': total_capacity
    })


@app.route('/api/traffic/collect', methods=['POST'])
@login_required
@admin_required
def collect_traffic_now():
    try:
        from utils.traffic_monitor import LibraryTrafficMonitor
        success = LibraryTrafficMonitor.collect_and_save()

        if success:
            latest = Traffic.get_latest()
            if latest:
                add_log(f"管理员 {current_user.username} 手动采集流量数据", user=current_user)
                return jsonify({
                    'success': True,
                    'message': '流量数据采集成功',
                    'data': {
                        'timestamp': latest.timestamp,
                        'count': latest.count,
                        'total': 2749
                    }
                })

        return jsonify({
            'success': False,
            'message': '流量数据采集失败，请查看日志'
        }), 500

    except Exception as e:
        logger.error(f"手动采集流量数据失败: {str(e)}")
        return jsonify({
            'success': False,
            'message': str(e)
        }), 500


def query_traffic_data_chunk(date_range, db_uri):
    start_ts, end_ts = date_range
    engine = create_engine(db_uri)
    connection = engine.connect()
    
    query = text(
        "SELECT timestamp, count FROM traffic WHERE timestamp >= :start AND timestamp < :end ORDER BY timestamp DESC"
    )
    
    result = connection.execute(query, {'start': start_ts, 'end': end_ts})
    
    chunk_data = [{
        'timestamp': row.timestamp,
        'count': row.count
    } for row in result]
    
    connection.close()
    return chunk_data

@app.route('/export_traffic_csv')
@login_required
@admin_required
def export_traffic_csv():
    try:
        min_ts_result = db.session.query(db.func.min(Traffic.timestamp)).scalar()
        max_ts_result = db.session.query(db.func.max(Traffic.timestamp)).scalar()

        if not min_ts_result or not max_ts_result:
            flash('没有流量数据可导出', 'warning')
            return redirect(url_for('admin'))

        db_filename = app.config['SQLALCHEMY_DATABASE_URI'].replace('sqlite:///', '')
        db_path = os.path.join(app.instance_path, db_filename)
        db_uri = f'sqlite:///{db_path}'
        engine = create_engine(db_uri)
        connection = engine.connect()

        total_capacity = 2749

        @stream_with_context
        def generate():
            try:
                yield '\ufeff'
                header_buf = io.StringIO()
                header_writer = csv.writer(header_buf)
                header_writer.writerow(['时间戳', '日期时间', '在馆人数', '总容量', '占用率(%)'])
                yield header_buf.getvalue()
                header_buf.close()

                query = text("SELECT timestamp, count FROM traffic ORDER BY timestamp DESC")
                result = connection.execute(query)

                for row in result:
                    ts = row.timestamp
                    count = row.count

                    try:
                        dt_local = datetime.fromtimestamp(ts, tz=Config.TIMEZONE)
                        dt_str = dt_local.strftime('%Y-%m-%d %H:%M:%S')
                    except Exception:
                        dt_str = datetime.fromtimestamp(ts).strftime('%Y-%m-%d %H:%M:%S')

                    occ = round((count / total_capacity) * 100, 2)

                    buf = io.StringIO()
                    writer = csv.writer(buf)
                    writer.writerow([ts, dt_str, count, total_capacity, occ])
                    yield buf.getvalue()
                    buf.close()
            finally:
                connection.close()

        response = Response(
            generate(),
            mimetype='text/csv',
            headers={
                'Content-Disposition': f'attachment; filename=library_traffic_{datetime.now().strftime("%Y%m%d_%H%M%S")}.csv',
                'Cache-Control': 'no-store, no-transform'
            }
        )

        add_log(f"管理员 {current_user.username} 导出流量数据 (流式模式)", user=current_user)
        return response

    except Exception as e:
        import traceback
        logger.error(f"导出流量数据失败: {str(e)}\n{traceback.format_exc()}")
        flash(f'导出失败: {str(e)}', 'error')
        return redirect(url_for('admin'))


@app.route('/seats')
@login_required
def seats_query():
    return render_template('seats.html')


@app.route('/api/seats/summary')
@login_required
def get_seats_summary():
    try:
        from utils.seat_query import SeatQueryService
        from utils.auth_manager import AuthManager

        days_offset = request.args.get('days_offset', 0, type=int)
        if days_offset not in [0, 1]:
            return jsonify({'success': False, 'message': '日期参数错误'}), 400

        date_str = SeatQueryService.get_date_string(days_offset)

        authenticator = AuthManager.get_authenticator(current_user)
        if not authenticator or not authenticator.is_valid():
            return jsonify({'success': False, 'message': '认证失败，请重新登录'}), 401

        def progress_callback(progress):
            query_progress[current_user.id] = progress

        progress_callback(0)

        all_areas_summary = SeatQueryService.get_all_areas_summary(
            authenticator,
            date_str,
            progress_callback=progress_callback
        )

        floor_summary = SeatQueryService.get_floor_summary(all_areas_summary)

        total_stats = {
            'total': 0,
            'available': 0,
            'occupied': 0
        }
        for area_data in all_areas_summary.values():
            stats = area_data['stats']
            total_stats['total'] += stats['total']
            total_stats['available'] += stats['available']
            total_stats['occupied'] += stats['occupied']

        if total_stats['total'] > 0:
            total_stats['rate'] = round((total_stats['occupied'] / total_stats['total'] * 100), 1)
        else:
            total_stats['rate'] = 0

        return jsonify({
            'success': True,
            'date': date_str,
            'total': total_stats,
            'floors': floor_summary,
            'areas': all_areas_summary
        })

    except Exception as e:
        import traceback
        error_detail = traceback.format_exc()
        logger.error(f"获取座位摘要失败: {str(e)}\n{error_detail}")
        return jsonify({'success': False, 'message': f'获取座位数据失败: {str(e)}'}), 500


@app.route('/api/seats/progress')
@login_required
def get_seats_progress():
    progress = query_progress.get(current_user.id, 0)
    return jsonify({'progress': progress})


@app.route('/api/seats/detail')
@login_required
def get_seats_detail():
    try:
        from utils.seat_query import SeatQueryService
        from utils.auth_manager import AuthManager

        area_name = request.args.get('area')
        days_offset = request.args.get('days_offset', 0, type=int)

        if not area_name:
            return jsonify({'success': False, 'message': '缺少区域参数'}), 400

        if area_name not in SeatQueryService.AREAS:
            return jsonify({'success': False, 'message': '区域不存在'}), 404

        if days_offset not in [0, 1]:
            return jsonify({'success': False, 'message': '日期参数错误'}), 400

        date_str = SeatQueryService.get_date_string(days_offset)
        room_id = SeatQueryService.AREAS[area_name]['roomId']

        authenticator = AuthManager.get_authenticator(current_user)
        if not authenticator or not authenticator.is_valid():
            return jsonify({'success': False, 'message': '认证失败，请重新登录'}), 401

        seats_data = SeatQueryService.get_seats_data(
            authenticator,
            room_id,
            date_str
        )

        if seats_data is None:
            return jsonify({'success': False, 'message': '获取座位数据失败'}), 500

        processed_seats = []
        for seat in seats_data:
            resv_list = []
            for resv in seat.get('resvInfo', []):
                resv_list.append({
                    'startTime': SeatQueryService.convert_timestamp_to_time(resv.get('startTime')),
                    'endTime': SeatQueryService.convert_timestamp_to_time(resv.get('endTime')),
                    'status': SeatQueryService.get_seat_status_text(resv.get('resvStatus'))
                })

            processed_seats.append({
                'devId': seat.get('devId'),
                'devName': seat.get('devName'),
                'devStatus': seat.get('devStatus'),
                'isAvailable': len(seat.get('resvInfo', [])) == 0,
                'reservations': resv_list
            })

        return jsonify({
            'success': True,
            'area': area_name,
            'date': date_str,
            'seats': processed_seats
        })

    except Exception as e:
        import traceback
        error_detail = traceback.format_exc()
        logger.error(f"获取座位详情失败: {str(e)}\n{error_detail}")
        return jsonify({'success': False, 'message': f'获取座位数据失败: {str(e)}'}), 500


def format_reservation(resv, day_type):
    try:
        def ts_to_str(ts):
            return datetime.fromtimestamp(ts / 1000).strftime("%Y-%m-%d %H:%M:%S") if ts else "未知"

        seat_info = "未知座位"
        dev_info_list = resv.get("resvDevInfoList", [])
        if dev_info_list:
            dev_info = dev_info_list[0]
            seat_info = dev_info.get("devName", "未知座位")

        return {
            "uuid": resv.get("uuid"),
            "seat_info": seat_info,
            "begin_time": ts_to_str(resv.get("resvBeginTime", 0)),
            "end_time": ts_to_str(resv.get("resvEndTime", 0)),
            "status": resv.get("statusName", "未知状态"),
            "day_type": day_type
        }
    except Exception as e:
        logger.error(f"格式化预约信息出错: {str(e)}")
        return None


@app.route('/cancel_reservation/<uuid>', methods=['POST'])
@login_required
def cancel_user_reservation(uuid):
    try:
        authenticator = AuthManager.get_authenticator(current_user)
        if not authenticator:
            flash("认证失败，请检查图书馆账号密码")
            return redirect(url_for('dashboard'))

        reservation = SeatReservation(current_user, authenticator=authenticator)
        if reservation.cancel_reservation(uuid):
            flash("预约已成功取消")
        else:
            flash("取消预约失败，请稍后再试")
    except Exception as e:
        logger.error(f"取消预约出错: {str(e)}")
        flash("取消预约时发生错误")
    
    return redirect(url_for('dashboard'))


if __name__ == '__main__':
    freeze_support()
    app.run(host='0.0.0.0', port=5000, debug=False)
