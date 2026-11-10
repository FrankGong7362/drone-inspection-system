@echo off
chcp 65001 >nul
title 安装依赖并打包

REM ============================================================
REM 无人机巡检系统 - 首次部署：安装依赖 + 打包
REM 包括：后端 Maven 打包、前端 npm install、算法 pip install
REM ============================================================

set "PROJECT=%~dp0.."

echo ============================================================
echo  首次部署：安装依赖并打包
echo ============================================================
echo.

REM ---------------- 后端 ----------------
echo [1/3] 后端打包 (Maven) ...
cd /d "%PROJECT%\backend"
call mvn clean package -DskipTests
if errorlevel 1 (
    echo [错误] 后端打包失败，请检查 Maven 配置和网络连接。
    pause
    exit /b 1
)
echo.

REM ---------------- 前端 ----------------
echo [2/3] 前端依赖安装 (npm) ...
cd /d "%PROJECT%\frontend"
call npm install
if errorlevel 1 (
    echo [错误] 前端依赖安装失败，请检查 Node.js 版本（需 18+）和网络。
    pause
    exit /b 1
)
echo.

REM ---------------- 算法 ----------------
echo [3/3] 算法服务依赖安装 (pip) ...
cd /d "%PROJECT%\algorithm"
call python -m pip install -r requirements.txt
if errorlevel 1 (
    echo [错误] 算法依赖安装失败，请检查 Python 版本（需 3.9+）。
    pause
    exit /b 1
)
echo.

echo ============================================================
echo  依赖安装与打包全部完成！
echo  接下来双击 03-启动服务.bat 启动系统。
echo ============================================================
echo.
pause
