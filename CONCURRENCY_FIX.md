# 图书馆预约系统并发问题修复

## 问题描述

### 症状
1. **重复任务执行**：每个定时任务（认证、预约、流量监控）同时执行 3 次
2. **预约冲突**：同一用户的预约请求被发送 3 次，导致：
   - 前两次失败：`当前设备正在被预约，请稍后重试`
   - 第三次成功
3. **重复通知**：用户收到 3 条通知（2 条失败 + 1 条成功）

### 根本原因
应用使用 gunicorn 以 3 个 worker 进程运行（见 `deploy.sh` 第 155 行）：
```bash
ExecStart=${GUNICORN_EXEC} --workers 3 --worker-class gevent --bind 0.0.0.0:${APP_PORT} app:app
```

每个 worker 进程在启动时都会：
1. 导入 `scheduler.py` 模块
2. 创建自己的 `BackgroundScheduler` 实例
3. 调用 `setup_scheduler(app)` 初始化调度器
4. 独立运行定时任务

虽然 `scheduler.py` 中使用了 `scheduler_initialized` 标志来防止重复初始化，但这个标志是**进程内变量**，不能跨进程共享。因此，3 个 worker 各自运行一个独立的调度器实例。

### 日志证据
```
2025-11-02 07:00:00,001 - Running job "Reserve seats for admin users daily" (scheduled at: 2025-11-02 07:00:00+08:00)
2025-11-02 07:00:00,002 - Running job "Reserve seats for admin users daily" (scheduled at: 2025-11-02 07:00:00+08:00)
2025-11-02 07:00:00,003 - Running job "Reserve seats for admin users daily" (scheduled at: 2025-11-02 07:00:00+08:00)

2025-11-02 07:00:01,674 - 用户 2410403132 预约失败: 当前设备正在被预约，请稍后重试
2025-11-02 07:00:01,834 - 用户 2410403132 预约失败: 当前设备正在被预约，请稍后重试
2025-11-02 07:00:02,502 - 用户 2410403132 预约成功: 新增成功
```

## 解决方案

### 实现：文件锁（File Lock）机制
使用操作系统级别的文件锁来确保只有一个进程能运行调度器。

### 代码变更

#### 1. 修改 `backend/scheduler.py`

**新增导入：**
```python
import os
import fcntl
```

**新增全局变量：**
```python
scheduler_lock_file = None
```

**重写 `setup_scheduler()` 函数：**
- 使用 `fcntl.flock()` 尝试获取排他锁（`LOCK_EX | LOCK_NB`）
- 成功获取锁的进程：运行调度器
- 无法获取锁的进程：跳过调度器初始化，仅作为 Web 工作进程
- 注册清理函数：进程退出时释放锁并删除锁文件

关键逻辑：
```python
try:
    # 尝试获取文件锁（非阻塞）
    scheduler_lock_file = open(lock_file_path, 'w')
    fcntl.flock(scheduler_lock_file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
    
    # 成功 -> 运行调度器
    logger.info("成功获取调度器锁，此进程将运行调度器")
    # ... 初始化调度器 ...
    
except (IOError, OSError) as e:
    # 失败 -> 跳过调度器
    logger.info("另一个进程已在运行调度器，此进程跳过调度器初始化")
```

#### 2. 更新 `.gitignore`
添加锁文件目录到 `.gitignore`：
```
backend/locks/
```

### 工作原理

1. **进程启动**：3 个 gunicorn worker 几乎同时启动
2. **竞争锁**：每个进程尝试获取 `backend/locks/scheduler.lock` 文件锁
3. **赢家通吃**：
   - Worker 1（假设）：成功获取锁 → 运行调度器
   - Worker 2：无法获取锁 → 跳过调度器，仅处理 Web 请求
   - Worker 3：无法获取锁 → 跳过调度器，仅处理 Web 请求
4. **清理**：Worker 1 退出时自动释放锁并删除锁文件

### 优势

1. **操作系统级别**：`fcntl` 是 POSIX 标准，锁在进程间有效
2. **自动清理**：进程异常退出时，操作系统会自动释放文件锁
3. **零依赖**：无需引入 Redis 或其他外部服务
4. **简单可靠**：实现简单，易于理解和维护

## 测试验证

### 测试脚本
运行 `backend/test_scheduler_lock.py` 可验证锁机制：
```bash
cd /home/engine/project/backend
python3 test_scheduler_lock.py
```

### 预期结果
```
[进程 1] ✓ 成功获取调度器锁！这个进程将运行调度器
[进程 2] ✗ 无法获取锁，另一个进程已在运行调度器
[进程 3] ✗ 无法获取锁，另一个进程已在运行调度器
```

### 生产环境验证
部署后，检查日志应看到：
1. **启动阶段**：
   ```
   成功获取调度器锁，此进程将运行调度器
   另一个进程已在运行调度器，此进程跳过调度器初始化
   另一个进程已在运行调度器，此进程跳过调度器初始化
   ```

2. **任务执行**：每个任务只执行一次
   ```
   2025-11-03 07:00:00,001 - Running job "Reserve seats for admin users daily"
   2025-11-03 07:00:01,123 - 用户 2410403132 预约成功
   ```

3. **通知发送**：每次预约只发送一条通知

## 相关代码位置

- **调度器初始化**：`backend/scheduler.py` → `setup_scheduler()`
- **应用启动**：`backend/app.py` → 第 138 行
- **部署配置**：`deploy.sh` → 第 155 行（gunicorn workers 配置）
- **测试脚本**：`backend/test_scheduler_lock.py`

## 注意事项

1. **锁文件位置**：`backend/locks/scheduler.lock`
   - 自动创建，已加入 `.gitignore`
   - 进程正常退出时会自动删除

2. **异常情况处理**：
   - 进程崩溃：操作系统自动释放锁
   - 僵尸锁文件：新进程启动时可重新获取锁（因为锁已释放）

3. **兼容性**：
   - Linux/Unix/macOS：完全支持（POSIX 标准）
   - Windows：需要使用不同的锁机制（如 `msvcrt.locking`）

4. **性能影响**：
   - 文件锁操作开销极小（纳秒级）
   - 对 Web 请求处理性能无影响
   - 所有 worker 都能正常处理 HTTP 请求

## 未来优化建议

1. **分离调度器进程**：
   - 将调度器从 gunicorn workers 中分离
   - 使用 systemd 运行独立的调度器进程
   - 完全避免多进程竞争问题

2. **使用 Redis 锁**：
   - 如果部署环境已有 Redis
   - 可使用 Redis 分布式锁替代文件锁
   - 支持跨主机部署（高可用场景）

3. **监控告警**：
   - 监控调度器心跳
   - 检测调度器进程异常
   - 及时发现并恢复调度服务

## 结论

通过引入文件锁机制，成功解决了多 worker 进程环境下的调度器并发问题：
- ✅ 消除重复任务执行
- ✅ 避免预约竞争冲突
- ✅ 防止重复通知发送
- ✅ 保持系统简单可维护

此方案无需引入外部依赖，利用操作系统原生机制，实现简单、可靠、高效。
