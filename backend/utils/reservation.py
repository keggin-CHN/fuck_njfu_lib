import datetime
import logging
from .auth_manager import HttpClient, AuthManager, handle_exception, check_ip_blocked, handle_ip_block
from .date_utils import get_today_date, get_tomorrow_date, normalize_time_format, is_friday, get_end_time
from models import db, ReservationHistory

logger = logging.getLogger(__name__)


class SeatReservation:
    def __init__(self, user, authenticator=None):
        self.user = user
        self.authenticator = authenticator or AuthManager.get_authenticator(user)

    def ensure_authenticated(self):
        if not self.authenticator or not self.authenticator.is_valid():
            logger.info(f"用户 {self.user.username} 需要重新认证")
            from .logger_utils import add_log
            add_log(f"用户 {self.user.username} 的认证信息失效，正在尝试重新认证", user=self.user, response_code=100)
            # 强制刷新认证器，而不仅仅是获取（因为获取可能会返回缓存的失效认证器）
            self.authenticator = AuthManager.refresh_authenticator(self.user)
        return self.authenticator is not None

    def is_authentication_valid(self):
        return self.authenticator and self.authenticator.is_valid()

    def record_auth_failure(self, action_type, area=None, seat_number=None, seat_id=None, date_str=None,
                            start_time=None, send_notification=True):
        try:
            action_desc = "预约座位" if action_type == "reserve" else "迟到保护"

            if not date_str:
                date_str = get_tomorrow_date() if action_type == "reserve" else get_today_date()

            history = ReservationHistory(
                user_id=self.user.id,
                area=area,
                seat_number=seat_number,
                seat_id=seat_id,
                reserve_date=datetime.datetime.strptime(date_str, "%Y-%m-%d").date() if date_str else None,
                start_time=start_time,
                status=ReservationHistory.STATUS_AUTH_FAILED,
                message=f"{action_desc}失败: 认证失败，请更新您的统一认证或图书馆密码",
                is_late_protection=(action_type == "protect")
            )

            db.session.add(history)
            db.session.commit()
            logger.info(f"已记录用户 {self.user.username} 的认证失败: {action_desc}")
            
            if send_notification:
                from .notification import NotificationService
                NotificationService.send_single_reservation_notification(self.user, history)
            
            return True
        except Exception as e:
            logger.error(f"记录认证失败时出错: {str(e)}")
            db.session.rollback()
            return False

    @handle_exception
    def reserve_seat(self, area, seat_number, seat_id, date_str=None, start_time=None, is_late_protection=False, is_auto_find=False, send_notification=True):
        if not self.ensure_authenticated():
            self.record_auth_failure(
                "reserve",
                area=area,
                seat_number=seat_number,
                seat_id=seat_id,
                date_str=date_str,
                start_time=start_time,
                send_notification=send_notification
            )
            return False, "认证失败"

        if not date_str:
            date_str = get_tomorrow_date()
            logger.info(f"未指定预约日期，默认预约明天: {date_str}")
        else:
            logger.info(f"指定预约日期: {date_str}")

        if not start_time:
            start_time = "09:30:00"

        start_time = normalize_time_format(start_time)

        from models import ReservationSetting
        setting = ReservationSetting.query.filter_by(user_id=self.user.id).first()
        if setting and setting.end_time:
            end_time = normalize_time_format(setting.end_time)
        else:
            logger.warning(f"用户 {self.user.username} 的预约设置或结束时间未找到，使用默认结束时间")
            end_time = get_end_time(date_str)

        try:
            begin_time_obj = datetime.datetime.strptime(start_time, "%H:%M:%S")
            end_time_obj = datetime.datetime.strptime(end_time, "%H:%M:%S")
            duration = (end_time_obj - begin_time_obj).seconds / 3600

            if duration < 2:
                message = f"预约时长不足2小时（从 {start_time} 到 {end_time}），图书馆规定预约时长至少2小时"
                logger.error(message)
                self._record_reservation_history(
                    area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
                    is_late_protection=is_late_protection, send_notification=send_notification
                )
                return False, message
        except ValueError as e:
            message = f"时间格式错误: {str(e)}"
            logger.error(message)
            self._record_reservation_history(
                area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
                is_late_protection=is_late_protection, is_auto_find=is_auto_find, send_notification=send_notification
            )
            return False, message

        begin_time = f"{date_str} {start_time}"
        full_end_time = f"{date_str} {end_time}"

        return self._do_reserve(area, seat_number, seat_id, begin_time, full_end_time, date_str, start_time, end_time,
                                is_late_protection, is_auto_find, send_notification)

    def _do_reserve(self, area, seat_number, seat_id, begin_time, full_end_time, date_str, start_time, end_time,
                    is_late_protection, is_auto_find, send_notification=True):
        reserve_url = HttpClient.get_lib_url("ic-web/reserve?vpn-12-libseat.njfu.edu.cn")

        api_headers = {
            "content-type": "application/json;charset=UTF-8",
            "token": self.authenticator.token,
            "lan": "1"
        }

        payload = {
            "sysKind": 8,
            "appAccNo": self.authenticator.acc_no,
            "memberKind": 1,
            "resvMember": [self.authenticator.acc_no],
            "resvBeginTime": begin_time,
            "resvEndTime": full_end_time,
            "resvDev": [seat_id],
            "resvProperty": 0,
            "memo": "",
            "captcha": "",
            "testName": ""
        }

        logger.info(
            f"用户 {self.user.username} 开始预约: 区域 {area}, 座位号 {seat_number}, 日期 {date_str}, 时间 {start_time} - {end_time}")

        max_retries = 2
        for retry in range(max_retries):
            try:
                # 使用 authenticator.session 发送请求以保持会话状态
                response = self.authenticator.session.post(
                    reserve_url,
                    headers=api_headers,
                    json=payload
                )
                
                # 检查是否 IP 被封禁
                if check_ip_blocked(response, "座位预约"):
                    if handle_ip_block(response, "座位预约"):
                        logger.info("IP 封禁处理成功，重试预约请求...")
                        import time
                        time.sleep(2)
                        continue
                    else:
                        logger.error("IP 封禁处理失败")

                if response and response.status_code == 200:
                    result = response.json()
                    if result.get("code") == 0:
                        message = f"预约成功: {result.get('message', '操作成功')}"
                        logger.info(f"用户 {self.user.username} {message}")

                        uuid = None
                        if "data" in result:
                            uuid = result["data"].get("uuid")

                        self._record_reservation_history(
                            area, seat_number, seat_id, date_str, start_time, end_time, "成功", message,
                            uuid=uuid, is_late_protection=is_late_protection, is_auto_find=is_auto_find,
                            send_notification=send_notification
                        )
                        return True, message
                    else:
                        # 检查是否是 Token 失效导致的错误
                        error_msg = result.get('message', '未知错误')
                        if "登录" in error_msg or "Token" in error_msg or "认证" in error_msg:
                            logger.warning(f"用户 {self.user.username} 预约时遇到认证错误: {error_msg}，尝试刷新认证后重试")
                            # 尝试刷新认证
                            self.authenticator = AuthManager.refresh_authenticator(self.user)
                            if self.authenticator:
                                # 更新 header 中的 token
                                api_headers["token"] = self.authenticator.token
                                # 重新发送请求
                                response = self.authenticator.session.post(
                                    reserve_url,
                                    headers=api_headers,
                                    json=payload
                                )
                                if response and response.status_code == 200:
                                    result = response.json()
                                    if result.get("code") == 0:
                                        message = f"重试预约成功: {result.get('message', '操作成功')}"
                                        logger.info(f"用户 {self.user.username} {message}")
                                        uuid = None
                                        if "data" in result:
                                            uuid = result["data"].get("uuid")
                                        self._record_reservation_history(
                                            area, seat_number, seat_id, date_str, start_time, end_time, "成功", message,
                                            uuid=uuid, is_late_protection=is_late_protection, is_auto_find=is_auto_find,
                                            send_notification=send_notification
                                        )
                                        return True, message

                        message = f"预约失败: {error_msg}"
                        logger.error(f"用户 {self.user.username} {message}")
                        self._record_reservation_history(
                            area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
                            is_late_protection=is_late_protection, is_auto_find=is_auto_find,
                            send_notification=send_notification
                        )
                        return False, message
                else:
                    status = response.status_code if response else "请求失败"
                    message = f"预约请求失败，状态码：{status}"
                    logger.error(f"用户 {self.user.username} {message}")
                    self._record_reservation_history(
                        area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
                        is_late_protection=is_late_protection, is_auto_find=is_auto_find,
                        send_notification=send_notification
                    )
                    return False, message

            except Exception as e:
                message = f"预约过程出错: {str(e)}"
                logger.error(f"用户 {self.user.username} {message}")
                if retry < max_retries - 1:
                    import time
                    time.sleep(2)
                    continue
                self._record_reservation_history(
                    area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
                    is_late_protection=is_late_protection, is_auto_find=is_auto_find,
                    send_notification=send_notification
                )
                return False, message
        
        # 如果所有重试都失败
        message = "预约请求失败：已达最大重试次数"
        self._record_reservation_history(
            area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
            is_late_protection=is_late_protection, is_auto_find=is_auto_find,
            send_notification=send_notification
        )
        return False, message

    def _record_reservation_history(self, area, seat_number, seat_id, date_str, start_time, end_time, status,
                                    message=None, uuid=None, is_late_protection=False, is_auto_find=False, send_notification=True):
        try:
            reserve_date = datetime.datetime.strptime(date_str, "%Y-%m-%d").date()

            history = ReservationHistory(
                user_id=self.user.id,
                area=area,
                seat_number=seat_number,
                seat_id=seat_id,
                reserve_date=reserve_date,
                start_time=start_time,
                end_time=end_time,
                status=status,
                message=message,
                uuid=uuid,
                is_late_protection=is_late_protection,
                is_auto_find=is_auto_find
            )
            db.session.add(history)
            db.session.commit()
            logger.info(f"已记录用户 {self.user.username} 的预约历史: {status}")
            
            if send_notification:
                from .notification import NotificationService
                NotificationService.send_single_reservation_notification(self.user, history)

            try:
                if status == ReservationHistory.STATUS_SUCCESS:
                    from models import ReservationSetting
                    setting = ReservationSetting.query.filter_by(user_id=self.user.id).first()
                    is_today = (date_str == get_today_date())
                    if setting and setting.prevent_late and is_today and start_time:
                        begin_dt = datetime.datetime.strptime(f"{date_str} {start_time}", "%Y-%m-%d %H:%M:%S")
                        from scheduler import schedule_late_check_task
                        schedule_late_check_task(self.user, begin_dt)
                        logger.info(f"已为用户 {self.user.username} 调度开始前20分钟的迟到检查（开始时间 {begin_dt.strftime('%H:%M:%S')}）")
            except Exception as se:
                logger.error(f"调度迟到检查任务失败: {str(se)}")
            
        except Exception as e:
            logger.error(f"记录预约历史或发送通知时出错: {str(e)}")
            db.session.rollback()

    @handle_exception
    def get_reservations(self, begin_date, end_date):
        if not self.ensure_authenticated():
            return []

        url = HttpClient.get_lib_url("ic-web/reserve/resvInfo")

        params = {
            "vpn-12-libseat.njfu.edu.cn": "",
            "needStatus": "8454",
            "unneedStatus": "128",
            "beginDate": begin_date,
            "endDate": end_date
        }

        api_headers = {
            "token": self.authenticator.token,
            "lan": "1",
        }

        max_retries = 2
        for retry in range(max_retries):
            try:
                response = self.authenticator.session.get(
                    url,
                    headers=api_headers,
                    params=params
                )
                
                # 检查是否 IP 被封禁
                if check_ip_blocked(response, "获取预约信息"):
                    if handle_ip_block(response, "获取预约信息"):
                        logger.info("IP 封禁处理成功，重试获取预约信息...")
                        import time
                        time.sleep(2)
                        continue
                    else:
                        logger.error("IP 封禁处理失败")

                if response and response.status_code == 200:
                    result = response.json()
                    if result.get("code") == 0:
                        logger.info(f"成功获取用户 {self.user.username} 的预约信息")
                        return result.get("data", [])
                    else:
                        message = f"获取预约信息失败: {result.get('message', '未知错误')}"
                        logger.error(message)
                        from .logger_utils import add_log
                        add_log(message, user=self.user, response_code=500, error_message=message)
                else:
                    status = response.status_code if response else "请求失败"
                    message = f"获取预约信息请求失败，状态码：{status}"
                    logger.error(message)
                    from .logger_utils import add_log
                    add_log(message, user=self.user, response_code=status or 500, error_message=message)
            except Exception as e:
                message = f"获取预约信息过程出错: {str(e)}"
                logger.error(message)
                if retry < max_retries - 1:
                    import time
                    time.sleep(2)
                    continue
                from .logger_utils import add_log
                add_log(message, user=self.user, response_code=500, error_message=message)

        return []

    def get_today_reservations(self):
        today = get_today_date()
        return self.get_reservations(begin_date=today, end_date=today)

    @handle_exception
    def cancel_reservation(self, uuid):
        if not uuid:
            message = "取消预约失败：没有提供预约UUID"
            logger.error(message)
            from .logger_utils import add_log
            add_log(message, user=self.user, response_code=400, error_message=message)
            return False, message

        if not self.ensure_authenticated():
            message = "取消预约失败：认证失效"
            self.record_auth_failure("cancel")
            return False, message

        url = HttpClient.get_lib_url("ic-web/reserve/delete")

        params = {
            "vpn-12-libseat.njfu.edu.cn": ""
        }

        api_headers = {
            "content-type": "application/json;charset=UTF-8",
            "token": self.authenticator.token,
            "lan": "1",
        }

        payload = {
            "uuid": uuid
        }

        from .logger_utils import add_log
        logger.info(f"用户 {self.user.username} 尝试取消预约 UUID: {uuid}")
        add_log(f"用户尝试取消预约 UUID: {uuid}", user=self.user)
        
        max_retries = 2
        for retry in range(max_retries):
            try:
                response = self.authenticator.session.post(
                    url,
                    headers=api_headers,
                    params=params,
                    json=payload
                )
                
                # 检查是否 IP 被封禁
                if check_ip_blocked(response, "取消预约"):
                    if handle_ip_block(response, "取消预约"):
                        logger.info("IP 封禁处理成功，重试取消预约请求...")
                        import time
                        time.sleep(2)
                        continue
                    else:
                        logger.error("IP 封禁处理失败")

                if response and response.status_code == 200:
                    result = response.json()
                    if result.get("code") == 0:
                        logger.info(f"用户 {self.user.username} 成功取消预约")
                        add_log(f"成功取消预约 UUID: {uuid}", user=self.user)
                        return True, "取消预约成功"
                    else:
                        message = f"取消预约失败: {result.get('message', '未知错误')}"
                        logger.error(message)
                        add_log(message, user=self.user, response_code=500, error_message=message)
                        return False, message
                else:
                    status = response.status_code if response else "请求失败"
                    message = f"取消预约请求失败，状态码：{status}"
                    logger.error(message)
                    add_log(message, user=self.user, response_code=status or 500, error_message=message)
                    return False, message
            except Exception as e:
                message = f"取消预约过程出错: {str(e)}"
                logger.error(message)
                if retry < max_retries - 1:
                    import time
                    time.sleep(2)
                    continue
                add_log(message, user=self.user, response_code=500, error_message=message)
                return False, message

        return False, "取消预约失败：已达最大重试次数"

    def reserve_today_seat(self, area, seat_number, seat_id, start_time, is_late_protection=True, is_auto_find=False):
        today = get_today_date()
        return self.reserve_seat(area, seat_number, seat_id, date_str=today, start_time=start_time,
                                 is_late_protection=is_late_protection, is_auto_find=is_auto_find)
