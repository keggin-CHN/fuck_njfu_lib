# Changelog

## [未发布] - 2025-11-02

### 修复
- **[关键] 修复多 worker 环境下调度器并发执行问题**
  - 问题：在 gunicorn 3 workers 配置下，每个定时任务被执行 3 次
  - 影响：导致重复预约、竞争失败、重复通知
  - 解决方案：在 `scheduler.py` 中实现文件锁机制，确保只有一个进程运行调度器
  - 相关文件：
    - `backend/scheduler.py` - 新增 fcntl 文件锁逻辑
    - `.gitignore` - 添加 `backend/locks/` 目录
    - `CONCURRENCY_FIX.md` - 详细问题分析和解决方案文档
    - `backend/test_scheduler_lock.py` - 锁机制验证测试
    - `backend/README_TEST.md` - 测试说明文档

### 技术细节
- 使用 `fcntl.flock()` 实现进程间文件锁
- 锁文件位置：`backend/locks/scheduler.lock`
- 只有成功获取锁的进程会运行 APScheduler
- 其他进程跳过调度器初始化，仅作为 Web 服务器
- 进程退出时自动释放锁并清理锁文件

### 测试
```bash
# 运行锁机制测试
cd backend
python3 test_scheduler_lock.py
```

### 预期改进
1. ✅ 消除重复任务执行
2. ✅ 避免座位预约竞争冲突
3. ✅ 防止重复通知发送
4. ✅ 保持系统简单可维护，无需外部依赖

### 部署注意事项
- 无需修改 gunicorn 配置或 workers 数量
- 无需重新安装依赖
- 部署后首次启动会在日志中看到锁获取信息
- 只有一个 worker 会记录"成功获取调度器锁"
- 其他 workers 会记录"另一个进程已在运行调度器"

### 兼容性
- Linux/Unix/macOS: 完全支持 (POSIX fcntl)
- Python 3.6+: 支持
- 现有功能: 无破坏性变更

### 相关 Issue/Ticket
- 修复图书馆预约系统调度器并发通知问题
