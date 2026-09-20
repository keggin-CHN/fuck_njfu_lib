@echo off
chcp 936 >nul
cd /d "%~dp0"
title NJFU Direct API Service (Background)

echo ===============================================================
echo        NJFU Library Direct Service - Background Silent Mode
echo ===============================================================
echo.

:: 1. Check Python
call :find_python
if "%PY_CMD%"=="" goto :err_no_python
echo [OK] Found Python: %PY_CMD%

:: 2. Check and init venv
if exist "%~dp0venv\Scripts\python.exe" goto :start_process

echo.
echo [1/3] Initializing Python virtual environment...
%PY_CMD% -m venv "%~dp0venv"
if errorlevel 1 goto :err_venv

echo [2/3] Setting Tsinghua PyPI mirror...
"%~dp0venv\Scripts\python.exe" -m pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple >nul 2>&1
"%~dp0venv\Scripts\python.exe" -m pip config set global.trusted-host pypi.tuna.tsinghua.edu.cn >nul 2>&1

echo [3/3] Installing dependencies...
"%~dp0venv\Scripts\python.exe" -m pip install --upgrade pip -i https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn
"%~dp0venv\Scripts\python.exe" -m pip install -r "%~dp0requirements.txt" -i https://pypi.tuna.tsinghua.edu.cn/simple --trusted-host pypi.tuna.tsinghua.edu.cn

echo.
echo Dependency installation complete!
echo.

:start_process
if exist "%~dp0.stop" del /f /q "%~dp0.stop"

:: 3. Kill old processes
for /f "tokens=5" %%a in ('netstat -aon ^| findstr ":3001" ^| findstr "LISTENING"') do (
    taskkill /f /pid %%a >nul 2>&1
)
taskkill /f /im frpc.exe >nul 2>&1
taskkill /f /im wscript.exe >nul 2>&1

:: 4. Start silent VBS
if not exist "%~dp0logs" mkdir "%~dp0logs"
echo Starting background services...
wscript.exe "%~dp0run_silent.vbs"

echo.
echo ===============================================================
echo   [OK] Background services started successfully!
echo.
echo   - API log: %~dp0logs\server.log
echo   - Tunnel log: %~dp0logs\tunnel.log
echo ===============================================================
echo.
ping 127.0.0.1 -n 3 >nul
exit /b 0

:err_no_python
echo.
echo [Error] Python runtime not detected!
echo.
pause
exit /b 1

:err_venv
echo.
echo [Error] Virtual environment creation failed!
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
