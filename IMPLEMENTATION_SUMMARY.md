# 实施总结：修复调度器并发执行问题

## 快速概览

| 项目 | 内容 |
|------|------|
| **问题** | 多 worker 环境下调度器重复执行，导致预约竞争和重复通知 |
| **原因** | gunicorn 3 workers 各自运行独立的 APScheduler 实例 |
| **解决方案** | 使用文件锁确保只有一个进程运行调度器 |
| **影响** | 仅修复代码，无需修改配置或安装依赖 |
| **测试** | 通过测试脚本验证锁机制正常工作 |

## 文件变更清单

### 核心修改（2 个文件）

1. **backend/scheduler.py** (+52 行)
   - 新增文件锁机制
   - 只有获得锁的进程运行调度器
   - 自动清理锁资源

2. **.gitignore** (+1 行)
   - 添加 `backend/locks/` 目录

### 文档和测试（5 个文件）

3. **CONCURRENCY_FIX.md** - 问题分析和技术方案（详细）
4. **CHANGELOG.md** - 版本变更记录
5. **PULL_REQUEST_SUMMARY.md** - PR/MR 说明（简明）
6. **backend/test_scheduler_lock.py** - 锁机制测试脚本
7. **backend/README_TEST.md** - 测试说明

## 关键代码变更

### scheduler.py - setup_scheduler()

**变更前：**
```python
def setup_scheduler(app):
    """初始化任务调度器"""
    global scheduler_initialized
    if scheduler_initialized:
        return
    
    scheduler.app = app
    with app.app_context():
        if not scheduler.running:
            scheduler.remove_all_jobs()
            add_scheduler_jobs()
            scheduler.start()
            # ...
```

**变更后：**
```python
def setup_scheduler(app):
    """初始化任务调度器 - 使用文件锁确保只有一个进程运行调度器"""
    global scheduler_initialized, scheduler_lock_file
    if scheduler_initialized:
        return
    
    scheduler.app = app
    lock_file_path = os.path.join(os.path.dirname(__file__), 'locks', 'scheduler.lock')
    
    try:
        # 尝试获取排他锁（非阻塞）
        scheduler_lock_file = open(lock_file_path, 'w')
        fcntl.flock(scheduler_lock_file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        
        # 成功 -> 运行调度器
        logger.info("成功获取调度器锁，此进程将运行调度器")
        # ... 初始化和启动调度器 ...
        
    except (IOError, OSError) as e:
        # 失败 -> 跳过调度器
        logger.info("另一个进程已在运行调度器，此进程跳过调度器初始化")
```

## 工作流程

```
gunicorn 启动（3 workers）
         │
         ├─── Worker 1 启动
         │    ├─ 尝试获取 scheduler.lock
         │    ├─ ✓ 成功获取锁
         │    └─ 运行 APScheduler（定时任务）
         │
         ├─── Worker 2 启动
         │    ├─ 尝试获取 scheduler.lock
         │    ├─ ✗ 无法获取锁（Worker 1 持有）
         │    └─ 跳过调度器，仅处理 HTTP 请求
         │
         └─── Worker 3 启动
              ├─ 尝试获取 scheduler.lock
              ├─ ✗ 无法获取锁（Worker 1 持有）
              └─ 跳过调度器，仅处理 HTTP 请求

结果：
- 定时任务只执行 1 次（Worker 1）
- 预约不再有竞争冲突
- 用户只收到 1 条通知
- 所有 workers 都能处理 Web 请求
```

## 验证方法

### 1. 运行单元测试
```bash
cd /home/engine/project/backend
python3 test_scheduler_lock.py
```

**预期输出：**
```
[进程 1] ✓ 成功获取调度器锁！这个进程将运行调度器
[进程 2] ✗ 无法获取锁，另一个进程已在运行调度器
[进程 3] ✗ 无法获取锁，另一个进程已在运行调度器
```

### 2. 部署后检查日志

**启动日志：**
```
成功获取调度器锁，此进程将运行调度器
另一个进程已在运行调度器，此进程跳过调度器初始化
另一个进程已在运行调度器，此进程跳过调度器初始化
```

**任务执行日志：**
```
2025-11-03 07:00:00,001 - Running job "Reserve seats for admin users daily"
2025-11-03 07:00:01,123 - 用户 2410403132 预约成功
```
*注意：只有 1 条任务执行日志，不再是 3 条*

**通知日志：**
```
向用户 2410403132 发送预约通知成功
```
*注意：只有 1 条通知日志，不再是 3 条*

## 技术细节

### 文件锁 (fcntl.flock)

| 特性 | 说明 |
|------|------|
| **类型** | POSIX 标准，操作系统级别 |
| **锁模式** | 排他锁（LOCK_EX）+ 非阻塞（LOCK_NB） |
| **作用域** | 进程间有效（跨 worker） |
| **自动释放** | 进程退出时操作系统自动释放 |
| **锁文件** | backend/locks/scheduler.lock |

### 清理机制

```python
def cleanup_scheduler():
    scheduler.shutdown(wait=False)         # 停止调度器
    fcntl.flock(lock_file.fileno(), LOCK_UN)  # 释放锁
    lock_file.close()                      # 关闭文件
    os.remove(lock_file_path)              # 删除锁文件

atexit.register(cleanup_scheduler)  # 注册退出处理
```

## 常见问题 (FAQ)

### Q: 如果持有锁的进程崩溃怎么办？
A: 操作系统会自动释放文件锁。下次启动时，新进程可以正常获取锁。

### Q: 锁文件会一直存在吗？
A: 不会。进程正常退出时会删除锁文件。即使进程崩溃未删除，文件锁已被释放，新进程可以覆盖。

### Q: 会影响 Web 请求性能吗？
A: 不会。所有 workers 都能处理 HTTP 请求，只是调度器仅在一个进程中运行。

### Q: 需要修改 gunicorn 配置吗？
A: 不需要。workers 数量和其他配置保持不变。

### Q: 兼容 Windows 吗？
A: 当前实现使用 POSIX fcntl，主要支持 Linux/Unix/macOS。如需 Windows 支持，需使用 `msvcrt.locking`。

### Q: 如果想所有进程都不运行调度器怎么办？
A: 可以单独运行一个调度器进程，不通过 gunicorn 启动。这是更高级的架构优化。

## 部署步骤

1. **拉取代码**：
   ```bash
   cd /opt/fuck_njfu_lib
   git pull origin fix/library-reservation-notifications-concurrency
   ```

2. **无需安装依赖**（使用标准库）

3. **重启服务**：
   ```bash
   sudo systemctl restart fuck_njfu_lib
   ```

4. **检查日志**：
   ```bash
   sudo journalctl -u fuck_njfu_lib -f
   ```
   应该看到 1 个"成功获取"，2 个"跳过调度器"

5. **监控运行**：
   等待下一个定时任务执行（如 07:00 预约），确认：
   - 任务只执行 1 次
   - 预约成功（无竞争失败）
   - 通知只发送 1 条

## 回滚方案

如果出现问题，可快速回滚：

```bash
cd /opt/fuck_njfu_lib
git checkout main  # 或上一个稳定分支
sudo systemctl restart fuck_njfu_lib
```

**注意**：回滚后会恢复到多次执行的状态，但功能仍然可用（只是有重复）。

## 后续优化建议

1. **独立调度器进程**：
   - 将调度器从 gunicorn workers 中完全分离
   - 使用 systemd 单独管理调度器服务
   - 好处：架构更清晰，重启 Web 服务不影响调度器

2. **监控和告警**：
   - 监控调度器进程健康状态
   - 任务执行失败时发送告警
   - 跟踪锁获取失败次数

3. **分布式部署**：
   - 如果需要多主机部署，使用 Redis 分布式锁
   - 当前文件锁方案适用于单主机多进程场景

## 相关资源

- **详细技术文档**：[CONCURRENCY_FIX.md](./CONCURRENCY_FIX.md)
- **变更日志**：[CHANGELOG.md](./CHANGELOG.md)
- **PR 说明**：[PULL_REQUEST_SUMMARY.md](./PULL_REQUEST_SUMMARY.md)
- **测试说明**：[backend/README_TEST.md](./backend/README_TEST.md)
- **测试脚本**：[backend/test_scheduler_lock.py](./backend/test_scheduler_lock.py)

## 联系方式

如有问题或建议，请通过以下方式联系：
- 创建 Issue
- 提交 Pull Request
- 代码审查留言

---

**最后更新**：2025-11-02
**作者**：AI Assistant
**状态**：✅ 完成并测试通过
