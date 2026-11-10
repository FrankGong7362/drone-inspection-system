@echo off
chcp 65001 >nul
title 初始化数据库

REM ============================================================
REM 无人机巡检系统 - 数据库初始化脚本
REM 创建 uav_inspection 数据库并导入 schema.sql
REM ============================================================

set "PROJECT=%~dp0.."
set "DB_USER=root"
set "DB_PASS=123456"
set "DB_NAME=uav_inspection"

echo ============================================================
echo  数据库初始化
echo  目标: %DB_NAME%
echo ============================================================
echo.

REM 检测 mysql 命令是否可用
where mysql >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到 mysql 命令。请确认 MySQL 已安装且 bin 目录已加入 PATH。
    echo 或者手动用 Navicat 导入: db\schema.sql
    echo.
    pause
    exit /b 1
)

echo [1/2] 创建数据库 %DB_NAME% ...
mysql -u%DB_USER% -p%DB_PASS% -e "CREATE DATABASE IF NOT EXISTS %DB_NAME% DEFAULT CHARSET utf8mb4;"
if errorlevel 1 (
    echo [错误] 数据库连接失败，请检查账号密码是否正确。
    echo 当前配置: 用户=%DB_USER% 密码=%DB_PASS%
    echo 如需修改密码，请编辑本脚本顶部的 DB_PASS。
    pause
    exit /b 1
)

echo [2/2] 导入 schema.sql ...
mysql -u%DB_USER% -p%DB_PASS% %DB_NAME% < "%PROJECT%\db\schema.sql"
if errorlevel 1 (
    echo [错误] 导入失败，请检查 schema.sql 路径是否正确。
    pause
    exit /b 1
)

echo.
echo ============================================================
echo  数据库 %DB_NAME% 初始化完成！
echo ============================================================
echo.
pause
