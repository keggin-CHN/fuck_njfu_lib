# Server Notification Deduplication Feature

## 问题描述 (Problem Description)

在图书馆座位预约系统中，发现预约通知会重复发送给用户。这是因为在预约流程中，通知可能在多个地方被触发发送，导致用户收到重复的成功或失败通知。

In the library seat reservation system, notification messages were being sent multiple times to users. This occurred because notifications could be triggered from multiple places in the reservation workflow, resulting in duplicate success or failure notifications being delivered to users.

## 解决方案 (Solution)

添加了通知去重机制，通过在 `ReservationHistory` 模型中增加 `notification_sent` 字段来跟踪每条预约历史记录是否已发送通知。

Implemented a notification deduplication mechanism by adding a `notification_sent` field to the `ReservationHistory` model to track whether a notification has been sent for each reservation history record.

## 变更详情 (Changes)

### 1. 数据库模型变更 (Database Model Changes)

**文件**: `backend/models.py`

- 在 `ReservationHistory` 模型中添加 `notification_sent` 字段（布尔类型，默认 `False`）
- 添加 `STATUS_CANCELED` 常量用于表示取消的预约状态

Added `notification_sent` boolean field (default `False`) to the `ReservationHistory` model
Added `STATUS_CANCELED` constant for cancelled reservation status

```python
notification_sent = db.Column(db.Boolean, default=False)
STATUS_CANCELED = '已取消'
```

### 2. 通知服务变更 (Notification Service Changes)

**文件**: `backend/utils/notification.py`

在 `send_single_reservation_notification()` 方法中添加了去重逻辑：

Added deduplication logic in the `send_single_reservation_notification()` method:

1. **发送前检查** (Pre-send Check): 在发送通知前，检查 `notification_sent` 字段。如果已发送，则跳过
2. **发送后标记** (Post-send Mark): 通知成功发送后，将 `notification_sent` 设置为 `True` 并提交到数据库

Before sending: Check the `notification_sent` field. Skip if already sent.
After successful send: Set `notification_sent` to `True` and commit to database.

关键特性：
- 仅对已持久化到数据库的历史记录进行检查和标记
- 支持临时创建的 `ReservationHistory` 对象（用于构建消息但不保存到数据库）
- 使用 `hasattr()` 和 `getattr()` 安全地处理对象属性

Key features:
- Only checks and marks records that are persisted in the database
- Supports temporary `ReservationHistory` objects (for message building without database persistence)
- Uses `hasattr()` and `getattr()` for safe attribute handling

### 3. 数据库迁移 (Database Migration)

**文件**: `backend/app.py`

添加了自动数据库迁移代码，在应用启动时检查并添加 `notification_sent` 列：

Added automatic database migration code that checks and adds the `notification_sent` column on app startup:

```python
# 迁移/补充列：为 ReservationHistory 添加 notification_sent 列（SQLite）
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
```

## 工作原理 (How It Works)

### 预约流程 (Reservation Flow)

1. **创建预约记录** (Create Reservation Record)
   - 在 `reservation.py` 的 `_record_reservation_history()` 方法中创建 `ReservationHistory` 对象
   - `notification_sent` 默认为 `False`
   - Created in `_record_reservation_history()` method with `notification_sent` defaulting to `False`

2. **首次发送通知** (First Notification Send)
   - `NotificationService.send_single_reservation_notification()` 被调用
   - 检查 `notification_sent` 字段，发现为 `False`，继续发送
   - 通知发送成功后，将 `notification_sent` 设置为 `True`
   - Checks `notification_sent`, finds it's `False`, proceeds to send
   - After successful send, sets `notification_sent` to `True`

3. **重复调用保护** (Duplicate Call Protection)
   - 如果再次调用通知方法（例如在 scheduler 中手动发送）
   - 检查发现 `notification_sent` 为 `True`
   - 跳过发送，记录日志: "已发送过通知，跳过"
   - If called again (e.g., manual send in scheduler)
   - Finds `notification_sent` is `True`
   - Skips sending, logs: "通知已发送"

### 特殊情况处理 (Special Cases)

**临时对象** (Temporary Objects)

在某些场景（如迟到保护取消通知），会创建临时的 `ReservationHistory` 对象用于构建消息，但不保存到数据库：

In some scenarios (like late protection cancellation notifications), temporary `ReservationHistory` objects are created for message building but not saved to database:

```python
NotificationService.send_single_reservation_notification(
    user,
    ReservationHistory(
        user_id=user.id,
        area=area_name,
        status=ReservationHistory.STATUS_CANCELED,
        # ... 其他字段
    )
)
```

对于这类对象：
- 没有 `id` 属性（未持久化）
- 不会进行去重检查
- 不会标记 `notification_sent`
- 允许多次发送（如果需要）

For such objects:
- No `id` attribute (not persisted)
- No deduplication check performed
- `notification_sent` not marked
- Allows multiple sends if needed

## 向后兼容性 (Backward Compatibility)

- 现有数据库会在应用启动时自动添加 `notification_sent` 列
- 已存在的历史记录的 `notification_sent` 默认为 `False`（未发送）
- 不影响现有功能，只是添加了防重复机制

Existing databases automatically get the `notification_sent` column added on app startup
Existing history records default to `notification_sent=False` (not sent)
Does not affect existing functionality, only adds anti-duplication mechanism

## 测试验证 (Testing)

可以通过以下方式验证功能：

To verify the functionality:

1. 触发一次预约（成功或失败）
2. 检查数据库中对应的 `reservation_history` 记录
3. 确认 `notification_sent` 字段为 `True`
4. 尝试手动调用通知服务，应该被跳过

Trigger a reservation (success or failure)
Check the corresponding `reservation_history` record in database
Confirm `notification_sent` field is `True`
Try manually calling notification service, should be skipped

## 日志示例 (Log Examples)

**首次发送** (First Send):
```
2025-11-01 07:00:04 - utils.notification - INFO - 向用户 2410403132 发送预约通知成功
```

**重复调用被阻止** (Duplicate Prevented):
```
2025-11-01 07:00:05 - utils.notification - INFO - 用户 2410403132 的预约历史 ID=123 已发送过通知，跳过
```

## 收益 (Benefits)

1. **用户体验改善**: 避免用户收到重复的通知消息
2. **资源节约**: 减少不必要的 HTTP 请求到 webhook 端点
3. **日志清晰**: 明确记录通知发送状态
4. **可靠性提升**: 即使代码中多次调用通知方法，也能保证只发送一次

User experience improved: Avoid duplicate notification messages
Resource savings: Reduce unnecessary HTTP requests to webhook endpoints
Clear logging: Explicitly track notification send status
Improved reliability: Ensures only one send even with multiple calls

## 未来改进 (Future Improvements)

可以考虑：
- 添加通知重试机制（对于发送失败的通知）
- 添加通知发送时间戳字段
- 支持通知优先级和批量发送

Potential enhancements:
- Add notification retry mechanism (for failed sends)
- Add notification sent timestamp field
- Support notification priority and batch sending
