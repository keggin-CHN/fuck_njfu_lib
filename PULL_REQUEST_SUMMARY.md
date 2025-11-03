# 修复：图书馆预约系统调度器并发执行问题

## 问题描述

在使用 gunicorn 以 3 workers 部署时，调度器在每个 worker 进程中独立运行，导致：

1. **重复任务执行**：每个定时任务同时执行 3 次
2. **预约竞争失败**：同一座位被 3 个进程同时预约，前 2 次因竞争失败，只有第 3 次成功
3. **重复通知**：用户收到 3 条通知（2 条失败 + 1 条成功）

### 日志示例

```
2025-11-02 07:00:00,001 - Running job "Reserve seats for admin users daily"
2025-11-02 07:00:00,002 - Running job "Reserve seats for admin users daily"  
2025-11-02 07:00:00,003 - Running job "Reserve seats for admin users daily"

2025-11-02 07:00:01,674 - 用户 2410403132 预约失败: 当前设备正在被预约，请稍后重试
2025-11-02 07:00:01,834 - 用户 2410403132 预约失败: 当前设备正在被预约，请稍后重试
2025-11-02 07:00:02,502 - 用户 2410403132 预约成功: 新增成功
```

## 解决方案

使用 POSIX 文件锁（`fcntl.flock`）确保只有一个 worker 进程运行调度器：

- 3 个 workers 启动时竞争同一个锁文件
- 只有获得锁的进程运行 APScheduler
- 其他进程跳过调度器，仅处理 HTTP 请求
- 进程退出时自动释放锁

## 代码变更

### 修改的文件

1. **backend/scheduler.py**
   - 新增 `import os, fcntl`
   - 新增全局变量 `scheduler_lock_file`
   - 重写 `setup_scheduler()` 函数以实现文件锁机制
   - 新增 `cleanup_scheduler()` 清理函数

2. **.gitignore**
   - 添加 `backend/locks/` 目录（锁文件存放位置）

### 新增的文件

1. **CONCURRENCY_FIX.md** - 详细的问题分析和解决方案文档
2. **CHANGELOG.md** - 版本变更记录
3. **backend/test_scheduler_lock.py** - 锁机制验证测试脚本
4. **backend/README_TEST.md** - 测试说明文档

## 测试

### 运行测试

```bash
cd backend
python3 test_scheduler_lock.py
```

### 预期结果

```
[进程 1] ✓ 成功获取调度器锁！这个进程将运行调度器
[进程 2] ✗ 无法获取锁，另一个进程已在运行调度器
[进程 3] ✗ 无法获取锁，另一个进程已在运行调度器
```

## 部署验证

部署后，检查应用日志应看到：

```
成功获取调度器锁，此进程将运行调度器
另一个进程已在运行调度器，此进程跳过调度器初始化
另一个进程已在运行调度器，此进程跳过调度器初始化
```

## 优势

1. ✅ **无需外部依赖**：使用 POSIX 标准 fcntl，无需 Redis 等
2. ✅ **自动清理**：进程崩溃时操作系统自动释放锁
3. ✅ **零配置**：无需修改 gunicorn 配置或 workers 数量
4. ✅ **向后兼容**：对现有功能无破坏性变更
5. ✅ **简单可靠**：实现简单，易于理解和维护

## 影响范围

- **功能影响**：无，仅修复并发问题
- **性能影响**：无，文件锁操作开销极小
- **兼容性**：Linux/Unix/macOS 完全支持（POSIX 标准）
- **部署变更**：无需修改部署配置或安装新依赖

## Checklist

- [x] 代码变更完成
- [x] 测试脚本通过
- [x] 文档更新完成
- [x] .gitignore 更新
- [x] 无破坏性变更
- [x] 无需数据库迁移
- [x] 无需配置变更

## 相关文档

- 详细分析：[CONCURRENCY_FIX.md](./CONCURRENCY_FIX.md)
- 变更记录：[CHANGELOG.md](./CHANGELOG.md)
- 测试说明：[backend/README_TEST.md](./backend/README_TEST.md)

## Reviewer 注意事项

1. 重点审查 `backend/scheduler.py` 的 `setup_scheduler()` 函数
2. 验证文件锁的获取和释放逻辑
3. 确认清理函数正确注册到 `atexit`
4. 可选：在本地运行 `test_scheduler_lock.py` 验证锁机制
