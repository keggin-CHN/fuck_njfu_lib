@echo off
chcp 936 >nul
cd /d "%~dp0"
title 启动 NJFU 直连服务 (后台模式)

echo ===============================================================
echo        NJFU 图书馆直连预约服务 - 后台静默启动
echo ===============================================================
echo.

:: 1. 查找 Python 环境
call :find_python
if "%PY_CMD%"=="" goto :err_no_python
echo [OK] 找到 Python: %PY_CMD%

:: 2. 检查并自动初始化虚拟环境
if exist "%~dp0venv\Scripts\python.exe" goto :start_process

echo.
echo [1/3] 首次运行，正在创建 Python 虚拟环境...
%PY_CMD% -m venv "%~dp0venv"
if errorlevel 1 goto :err_venv

echo [2/3] 配置清华大学 PyPI 镜像源...
"%~dp0venv\Scripts\python.exe" -m pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple >nul 2>&1
"%~dp0venv\Scripts\python.exe" -m pip config set global.trusted-host pypi.tuna.tsinghua.edu.cn >nul 2>&1

echo [3/3] 正在通过清华镜像源安装依赖...
"%~dp0venv\Scripts\python.exe" -m pip install --upgrade pip -i https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn
"%~dp0venv\Scripts\python.exe" -m pip install -r "%~dp0requirements.txt" -i https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn

echo.
echo 依赖安装完成！
echo.

:start_process
if exist "%~dp0.stop" del /f /q "%~dp0.stop"

:: 3. 停止可能遗留的旧进程
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3001" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a >nul 2>&1
)
taskkill /f /im frpc.exe >nul 2>&1
taskkill /f /im wscript.exe >nul 2>&1

:: 4. 调用 VBS 启动纯后台静默运行
if not exist "%~dp0logs" mkdir "%~dp0logs"
echo 正在启动后台服务...
wscript.exe "%~dp0run_silent.vbs"

echo.
echo ===============================================================
echo   [OK] 服务与穿透隧道已转入【后台静默运行】！
echo.
echo   - 屏幕上不会残留任何黑框窗口，不影响日常办公
echo   - 业务日志: %~dp0logs\server.log
echo   - 隧道日志: %~dp0logs\tunnel.log
echo.
echo   管理方式:
echo   - 查看实时日志: 双击运行 [查看实时日志.bat]
echo   - 停止后台服务: 双击运行 [一键停止服务.bat]
echo ===============================================================
echo.
echo 窗口将在 3 秒后自动关闭...
ping 127.0.0.1 -n 4 >nul
exit /b 0

:err_no_python
echo.
echo [错误] 系统未检测到 Python 运行环境！
echo.
echo 请按以下步骤安装:
echo 1. 前往官网下载 Python (推荐 3.10 ~ 3.12): https://www.python.org/downloads/
echo 2. 安装时务必勾选最下方的: [x] Add python.exe to PATH
echo 3. 安装完成后重新双击本脚本即可。
echo.
pause
exit /b 1

:err_venv
echo.
echo [错误] 虚拟环境创建失败，请检查 Python 是否安装完整。
echo.
pause
exit /b 1

:find_python
set "PY_CMD="
python --version >nul 2>&1
if not errorlevel 1 (
    set "PY_CMD=python"
    goto :eof
)
py -3 --version >nul 2>&1
if not errorlevel 1 (
    set "PY_CMD=py -3"
    goto :eof
)
py --version >nul 2>&1
if not errorlevel 1 (
    set "PY_CMD=py"
    goto :eof
)
for %%p in (
    "%LocalAppData%\Programs\Python\Python312\python.exe"
    "%LocalAppData%\Programs\Python\Python311\python.exe"
    "%LocalAppData%\Programs\Python\Python310\python.exe"
    "%ProgramFiles%\Python312\python.exe"
    "%ProgramFiles%\Python311\python.exe"
    "%ProgramFiles%\Python310\python.exe"
    "C:\Python312\python.exe"
    "C:\Python311\python.exe"
    "C:\Python310\python.exe"
) do (
    if exist %%p (
        set "PY_CMD=%%~p"
        goto :eof
    )
)
goto :eof
