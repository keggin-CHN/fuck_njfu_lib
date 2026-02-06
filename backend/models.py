from datetime import datetime
import string
import random
from flask_sqlalchemy import SQLAlchemy
from werkzeug.security import generate_password_hash, check_password_hash
from flask_login import UserMixin
db = SQLAlchemy()
class User(db.Model, UserMixin):
    id = db.Column(db.Integer, primary_key=True)
    username = db.Column(db.String(64), unique=True, index=True)
    password_hash = db.Column(db.String(128))
    edu_password = db.Column(db.String(256))
    lib_password = db.Column(db.String(256))
    is_admin = db.Column(db.Boolean, default=False)
    notification_type = db.Column(db.String(16), default='none')
    webhook_url = db.Column(db.String(512))
    created_at = db.Column(db.DateTime, default=datetime.now)
    def set_password(self, password):
        self.password_hash = generate_password_hash(password)
    def check_password(self, password):
        return check_password_hash(self.password_hash, password)
class ReservationSetting(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), unique=True)
    area = db.Column(db.String(64))
    seat_number = db.Column(db.Integer)
    start_time = db.Column(db.String(16))
    end_time = db.Column(db.String(8), nullable=False)
    auto_reserve = db.Column(db.Boolean, default=False)
    prevent_late = db.Column(db.Boolean, default=False)
    auto_find_seat = db.Column(db.Boolean, default=False)
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now)
class ReservationHistory(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'))
    area = db.Column(db.String(64))
    seat_number = db.Column(db.Integer)
    seat_id = db.Column(db.Integer)
    reserve_date = db.Column(db.Date)
    start_time = db.Column(db.String(16))
    end_time = db.Column(db.String(16))
    status = db.Column(db.String(16))
    message = db.Column(db.Text)
    uuid = db.Column(db.String(64))
    is_late_protection = db.Column(db.Boolean, default=False)
    is_auto_find = db.Column(db.Boolean, default=False)
    notification_sent = db.Column(db.Boolean, default=False)
    created_at = db.Column(db.DateTime, default=datetime.now)
    STATUS_SUCCESS = '成功'
    STATUS_FAILED = '失败'
    STATUS_AUTH_FAILED = 'auth_failed'
    STATUS_CANCELED = '已取消'
class InviteCode(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    code = db.Column(db.String(8), unique=True, index=True, nullable=False)
    created_by = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=False)
    used_by = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now)
    used_at = db.Column(db.DateTime, nullable=True)
    is_used = db.Column(db.Boolean, default=False)
    creator = db.relationship('User', foreign_keys=[created_by], backref='created_invite_codes')
    user = db.relationship('User', foreign_keys=[used_by], backref='used_invite_codes')
    @staticmethod
    def generate_code():
        characters = string.ascii_uppercase + string.digits
        while True:
            code = ''.join(random.choice(characters) for _ in range(8))
            if not InviteCode.query.filter_by(code=code).first():
                return code
    @staticmethod
    def create_invite_code(created_by_user_id):
        code = InviteCode.generate_code()
        invite_code = InviteCode(
            code=code,
            created_by=created_by_user_id
        )
        db.session.add(invite_code)
        db.session.commit()
        return invite_code
    def use_code(self, user_id):
        if self.is_used:
            return False, "邀请码已被使用"
        self.used_by = user_id
        self.used_at = datetime.now()
        self.is_used = True
        db.session.commit()
        return True, "邀请码使用成功"
    @staticmethod
    def validate_code(code):
        invite_code = InviteCode.query.filter_by(code=code).first()
        if not invite_code:
            return False, "邀请码不存在"
        if invite_code.is_used:
            return False, "邀请码已被使用"
        return True, invite_code
class SystemSetting(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    key = db.Column(db.String(64), unique=True, nullable=False, index=True)
    value = db.Column(db.Text, nullable=False)
    description = db.Column(db.String(256))
    updated_at = db.Column(db.DateTime, default=datetime.now, onupdate=datetime.now)
    updated_by = db.Column(db.Integer, db.ForeignKey('user.id'))
    updater = db.relationship('User', backref='updated_settings')
    @staticmethod
    def get_setting(key, default_value=None):
        setting = SystemSetting.query.filter_by(key=key).first()
        if setting:
            if setting.value.lower() in ['true', 'false']:
                return setting.value.lower() == 'true'
            return setting.value
        return default_value
    @staticmethod
    def set_setting(key, value, description=None, updated_by=None):
        setting = SystemSetting.query.filter_by(key=key).first()
        if isinstance(value, bool):
            value = str(value).lower()
        if setting:
            setting.value = value
            setting.updated_at = datetime.now()
            if updated_by:
                setting.updated_by = updated_by
            if description:
                setting.description = description
        else:
            setting = SystemSetting(
                key=key,
                value=value,
                description=description,
                updated_by=updated_by
            )
            db.session.add(setting)
        db.session.commit()
        return setting
    @staticmethod
    def is_invite_code_required():
        return SystemSetting.get_setting('invite_code_required', True)
class Log(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('user.id'), nullable=True)
    ip_address = db.Column(db.String(45))
    action = db.Column(db.String(256))
    user_agent = db.Column(db.String(256))
    response_code = db.Column(db.Integer)
    response_content = db.Column(db.Text, nullable=True)
    error_message = db.Column(db.Text, nullable=True)
    created_at = db.Column(db.DateTime, default=datetime.now)
    user = db.relationship('User', backref='logs')
class Traffic(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    timestamp = db.Column(db.Integer, unique=True, nullable=False, index=True)
    count = db.Column(db.Integer, nullable=False)
    created_at = db.Column(db.DateTime, default=datetime.now)
    @staticmethod
    def get_latest():
        return Traffic.query.order_by(Traffic.timestamp.desc()).first()
    @staticmethod
    def get_recent(hours=24):
        cutoff_time = int(datetime.now().timestamp()) - (hours * 3600)
        return Traffic.query.filter(Traffic.timestamp >= cutoff_time).order_by(Traffic.timestamp.asc()).all()
    @staticmethod
    def cleanup_old_data(days=7):
        cutoff_time = int(datetime.now().timestamp()) - (days * 24 * 3600)
        Traffic.query.filter(Traffic.timestamp < cutoff_time).delete()
        db.session.commit()