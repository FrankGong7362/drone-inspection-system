@echo off
chcp 65001 >nul
title 无人机巡检系统 - 一键启动

REM ============================================================
REM 无人机巡检系统 一键启动脚本
REM 启动三个独立窗口：前端 Vite(5173) / 后端 Spring Boot(8080) / 算法 FastAPI(8000)
REM ============================================================

set "PROJECT=%~dp0"
cd /d "%PROJECT%"

echo [1/3] 启动算法服务 FastAPI (127.0.0.1:8000) ...
start "算法服务 FastAPI" cmd /k "cd /d "%PROJECT%algorithm" && python -m uvicorn app.main:app --host 127.0.0.1 --port 8000"

echo [2/3] 启动后端 Spring Boot (127.0.0.1:8080) ...
REM 后端 jar 需 JDK 17，Windows 全局 java 可能是 8
set "JAVA17=C:\Program Files\Java\jdk-17\bin\java.exe"
if exist "%JAVA17%" (
    start "后端 Spring Boot" cmd /k "cd /d "%PROJECT%backend" && "%JAVA17%" -jar target\uav-backend-0.0.1-SNAPSHOT.jar"
) else (
    start "后端 Spring Boot" cmd /k "cd /d "%PROJECT%backend" && java -jar target\uav-backend-0.0.1-SNAPSHOT.jar"
)

echo [3/3] 启动前端 Vite dev (localhost:5173) ...
start "前端 Vite dev" cmd /k "cd /d "%PROJECT%frontend" && npm run dev"

echo.
echo ============================================================
echo 三个窗口已分别启动，稍等 10-20 秒即可访问：
echo   前端:   http://localhost:5173
echo   后端:   http://127.0.0.1:8080
echo   算法:   http://127.0.0.1:8000/health
echo ============================================================
echo.
echo 关闭本窗口不会停止服务，需手动关闭各子窗口或执行 stop.bat 结束。
pause
