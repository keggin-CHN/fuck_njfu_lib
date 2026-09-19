@echo off
chcp 936 >nul
cd /d "%~dp0"
title NJFU 直连服务 (前台调试模式)

echo ===============================================================
echo        NJFU 直连服务 - 前台调试运行窗口
echo ===============================================================
echo.

call "%~dp0start_background.bat"
pause
