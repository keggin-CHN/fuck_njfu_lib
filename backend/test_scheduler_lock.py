import os
import fcntl
import time
import sys
from multiprocessing import Process

def try_acquire_lock(process_id, lock_file_path):
    try:
        print(f"[进程 {process_id}] 正在尝试获取调度器锁...")
        
        lock_file = open(lock_file_path, 'w')
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        
        print(f"[进程 {process_id}] ✓ 成功获取调度器锁！这个进程将运行调度器")
        
        for i in range(5):
            print(f"[进程 {process_id}] 调度器运行中... ({i+1}/5)")
            time.sleep(1)
        
        fcntl.flock(lock_file.fileno(), fcntl.LOCK_UN)
        lock_file.close()
        print(f"[进程 {process_id}] 已释放锁")
        
    except (IOError, OSError) as e:
        print(f"[进程 {process_id}] ✗ 无法获取锁，另一个进程已在运行调度器 (原因: {type(e).__name__})")
        
        print(f"[进程 {process_id}] 作为普通工作进程运行...")
        time.sleep(5)
        
        if lock_file:
            lock_file.close()

def main():
    lock_dir = os.path.join(os.path.dirname(__file__), 'locks')
    os.makedirs(lock_dir, exist_ok=True)
    lock_file_path = os.path.join(lock_dir, 'scheduler_test.lock')
    
    if os.path.exists(lock_file_path):
        os.remove(lock_file_path)
    
    print("=" * 60)
    print("调度器锁机制测试")
    print("=" * 60)
    print(f"模拟 3 个 gunicorn worker 进程同时启动")
    print(f"锁文件路径: {lock_file_path}")
    print("=" * 60)
    print()
    
    processes = []
    for i in range(1, 4):
        p = Process(target=try_acquire_lock, args=(i, lock_file_path))
        processes.append(p)
    
    for p in processes:
        p.start()
        time.sleep(0.1)
    
    for p in processes:
        p.join()
    
    print()
    print("=" * 60)
    print("测试完成！")
    print("预期结果: 只有一个进程成功获取锁并运行调度器")
    print("          其他进程跳过调度器初始化，仅作为工作进程运行")
    print("=" * 60)
    
    if os.path.exists(lock_file_path):
        os.remove(lock_file_path)

if __name__ == '__main__':
    main()
