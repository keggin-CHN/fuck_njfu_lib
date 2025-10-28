import datetime
import logging
from .auth_manager import HttpClient, AuthManager, handle_exception
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
            self.authenticator = AuthManager.get_authenticator(self.user)
        return self.authenticator is not None

    def is_authentication_valid(self):
        return self.authenticator and self.authenticator.is_valid()

    def record_auth_failure(self, action_type, area=None, seat_number=None, seat_id=None, date_str=None,
                            start_time=None):
        """记录认证失败导致的任务失败"""
        try:
            action_desc = "预约座位" if action_type == "reserve" else "迟到保护"

            if not date_str:
                date_str = get_tomorrow_date() if action_type == "reserve" else get_today_date()

            # 创建历史记录
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
            return True
        except Exception as e:
            logger.error(f"记录认证失败时出错: {str(e)}")
            db.session.rollback()
            return False

    @handle_exception
    def reserve_seat(self, area, seat_number, seat_id, date_str=None, start_time=None, is_late_protection=False, is_auto_find=False):
        # 确保已认证
        if not self.ensure_authenticated():
            # 记录认证失败
            self.record_auth_failure(
                "reserve",
                area=area,
                seat_number=seat_number,
                seat_id=seat_id,
                date_str=date_str,
                start_time=start_time
            )
            return False, "认证失败"

        # 默认值处理
        if not date_str:
            date_str = get_tomorrow_date()
            logger.info(f"未指定预约日期，默认预约明天: {date_str}")
        else:
            logger.info(f"指定预约日期: {date_str}")

        if not start_time:
            start_time = "09:30:00"

        start_time = normalize_time_format(start_time)

        # 动态获取结束时间
        from models import ReservationSetting
        setting = ReservationSetting.query.filter_by(user_id=self.user.id).first()
        if setting and setting.end_time:
            end_time = normalize_time_format(setting.end_time)
        else:
            # 如果没有设置，提供一个默认值以避免错误，尽管在正常流程中 setting 和 end_time 应该是存在的
            logger.warning(f"用户 {self.user.username} 的预约设置或结束时间未找到，使用默认结束时间")
            end_time = get_end_time(date_str)

        # 验证开始时间和结束时间之间至少相差2小时
        try:
            begin_time_obj = datetime.datetime.strptime(start_time, "%H:%M:%S")
            end_time_obj = datetime.datetime.strptime(end_time, "%H:%M:%S")
            duration = (end_time_obj - begin_time_obj).seconds / 3600

            if duration < 2:
                message = f"预约时长不足2小时（从 {start_time} 到 {end_time}），图书馆规定预约时长至少2小时"
                logger.error(message)
                self._record_reservation_history(
                    area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
                    is_late_protection=is_late_protection
                )
                return False, message
        except ValueError as e:
            message = f"时间格式错误: {str(e)}"
            logger.error(message)
            self._record_reservation_history(
                area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
                is_late_protection=is_late_protection, is_auto_find=is_auto_find
            )
            return False, message

        begin_time = f"{date_str} {start_time}"
        full_end_time = f"{date_str} {end_time}"

        return self._do_reserve(area, seat_number, seat_id, begin_time, full_end_time, date_str, start_time, end_time,
                                is_late_protection, is_auto_find)

    def _do_reserve(self, area, seat_number, seat_id, begin_time, full_end_time, date_str, start_time, end_time,
                    is_late_protection, is_auto_find):
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

        try:
            response = HttpClient.post(
                reserve_url,
                headers=api_headers,
                cookies={"my_client_ticket": self.authenticator.my_client_ticket},
                json_data=payload
            )

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
                        uuid=uuid, is_late_protection=is_late_protection, is_auto_find=is_auto_find
                    )
                    return True, message
                else:
                    message = f"预约失败: {result.get('message', '未知错误')}"
                    logger.error(f"用户 {self.user.username} {message}")
                    self._record_reservation_history(
                        area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
                        is_late_protection=is_late_protection, is_auto_find=is_auto_find
                    )
                    return False, message
            else:
                status = response.status_code if response else "请求失败"
                message = f"预约请求失败，状态码：{status}"
                logger.error(f"用户 {self.user.username} {message}")
                self._record_reservation_history(
                    area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
                    is_late_protection=is_late_protection, is_auto_find=is_auto_find
                )
                return False, message

        except Exception as e:
            message = f"预约过程出错: {str(e)}"
            logger.error(f"用户 {self.user.username} {message}")
            self._record_reservation_history(
                area, seat_number, seat_id, date_str, start_time, end_time, "失败", message,
                is_late_protection=is_late_protection, is_auto_find=is_auto_find
            )
            return False, message

    def _record_reservation_history(self, area, seat_number, seat_id, date_str, start_time, end_time, status,
                                    message=None, uuid=None, is_late_protection=False, is_auto_find=False):
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
            
            # 预约后立即发送通知
            from .notification import NotificationService
            NotificationService.send_single_reservation_notification(self.user, history)

            # 若为“今天”的成功预约且用户开启了预防迟到，则统一在此调度开始前20分钟的迟到检查
            try:
                if status == ReservationHistory.STATUS_SUCCESS:
                    # 查询用户设置，判断是否开启预防迟到
                    from models import ReservationSetting
                    setting = ReservationSetting.query.filter_by(user_id=self.user.id).first()
                    # 判断预约日期是否为今天（字符串比较）
                    is_today = (date_str == get_today_date())
                    if setting and setting.prevent_late and is_today and start_time:
                        begin_dt = datetime.datetime.strptime(f"{date_str} {start_time}", "%Y-%m-%d %H:%M:%S")
                        # 延迟导入以避免循环依赖
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

        try:
            response = HttpClient.get(
                url,
                headers=api_headers,
                params=params,
                cookies={"my_client_ticket": self.authenticator.my_client_ticket}
            )

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
    
            try:
                logger.info(f"用户 {self.user.username} 尝试取消预约 UUID: {uuid}")
                from .logger_utils import add_log
                add_log(f"用户尝试取消预约 UUID: {uuid}", user=self.user)
                response = HttpClient.post(
                    url,
                    headers=api_headers,
                    params=params,
                    cookies={"my_client_ticket": self.authenticator.my_client_ticket},
                    json_data=payload
                )
    
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
                add_log(message, user=self.user, response_code=500, error_message=message)
                return False, message
    
            return False, "取消预约失败"

    def reserve_today_seat(self, area, seat_number, seat_id, start_time, is_late_protection=True, is_auto_find=False):
        today = get_today_date()
        return self.reserve_seat(area, seat_number, seat_id, date_str=today, start_time=start_time,
                                 is_late_protection=is_late_protection, is_auto_find=is_auto_find)
