-- 无人机巡检任务管理与路径规划系统 —— 数据库建表脚本
-- MySQL 8，utf8mb4

CREATE DATABASE IF NOT EXISTS uav_inspection DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE uav_inspection;

-- 巡检任务表
CREATE TABLE IF NOT EXISTS inspection_task (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_no          VARCHAR(32)  NOT NULL COMMENT '任务编号',
    task_name        VARCHAR(128) NOT NULL COMMENT '任务名称',
    task_type        VARCHAR(20)  DEFAULT '园区' COMMENT '电力/园区/交通',
    status           VARCHAR(20)  DEFAULT '待执行' COMMENT '待执行/执行中/已完成/已取消',
    created_by       BIGINT       DEFAULT NULL COMMENT '创建者用户id(sys_user.id)',
    visible          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '对其他用户可见 1公开 0私有',
    start_point_id   BIGINT       DEFAULT NULL COMMENT '起始航点id',
    total_distance   DECIMAL(10,2) DEFAULT NULL COMMENT '规划总航程(km)',
    estimated_duration INT        DEFAULT NULL COMMENT '预计时长(分钟)',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_task_no (task_no)
) COMMENT='巡检任务';

-- 航点/巡检点表
CREATE TABLE IF NOT EXISTS waypoint (
    id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT      NOT NULL COMMENT '所属任务',
    name        VARCHAR(64) DEFAULT NULL COMMENT '点名称',
    longitude   DECIMAL(10,7) NOT NULL COMMENT '经度',
    latitude    DECIMAL(10,7) NOT NULL COMMENT '纬度',
    altitude    DECIMAL(8,2) DEFAULT 0 COMMENT '高度(m)',
    seq         INT         DEFAULT 0 COMMENT '预设顺序',
    is_forbidden TINYINT    DEFAULT 0 COMMENT '是否禁飞点 0/1',
    PRIMARY KEY (id),
    KEY idx_task (task_id)
) COMMENT='航点/巡检点';

-- 无人机档案表
CREATE TABLE IF NOT EXISTS drone (
    id                BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    drone_no          VARCHAR(32) NOT NULL COMMENT '机号/SN',
    model             VARCHAR(64) DEFAULT NULL COMMENT '型号',
    battery_capacity  DECIMAL(10,2) DEFAULT NULL COMMENT '电量(mAh)',
    max_flight_range  DECIMAL(10,2) DEFAULT NULL COMMENT '最大航程(km)',
    max_speed         DECIMAL(6,2) DEFAULT NULL COMMENT '巡航速度(km/h)',
    status            VARCHAR(20) DEFAULT '可用' COMMENT '可用/作业/维护/离线',
    bind_task_id      BIGINT      DEFAULT NULL COMMENT '当前绑定任务',
    PRIMARY KEY (id),
    UNIQUE KEY uk_drone_no (drone_no)
) COMMENT='无人机档案';

-- 执行日志表
CREATE TABLE IF NOT EXISTS mission_log (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id     BIGINT       NOT NULL COMMENT '任务',
    drone_id    BIGINT       DEFAULT NULL COMMENT '无人机',
    action      VARCHAR(20)  DEFAULT NULL COMMENT '起飞/到点/返航/降落/异常',
    waypoint_id BIGINT       DEFAULT NULL COMMENT '相关航点',
    log_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '记录时间',
    detail      VARCHAR(255) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (id),
    KEY idx_task (task_id)
) COMMENT='执行日志';

-- 路径规划结果表（算法输出）
CREATE TABLE IF NOT EXISTS planning_result (
    id               BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    task_id          BIGINT       NOT NULL COMMENT '任务',
    algorithm        VARCHAR(20)  NOT NULL COMMENT 'ga/aco/astar/greedy/nearest',
    route            JSON         DEFAULT NULL COMMENT '访问顺序(waypoint.id数组)',
    total_distance   DECIMAL(10,2) DEFAULT NULL COMMENT '总航程(km)',
    run_time_ms      BIGINT       DEFAULT NULL COMMENT '耗时(ms)',
    feasible         TINYINT      DEFAULT 1 COMMENT '是否满足约束',
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_task (task_id)
) COMMENT='路径规划结果';

-- 禁飞区表
CREATE TABLE IF NOT EXISTS no_fly_zone (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  DEFAULT NULL COMMENT '名称',
    center_lng  DECIMAL(10,7) NOT NULL COMMENT '中心经度',
    center_lat  DECIMAL(10,7) NOT NULL COMMENT '中心纬度',
    radius_km   DECIMAL(6,3) DEFAULT 0 COMMENT '半径(km)',
    PRIMARY KEY (id)
) COMMENT='禁飞区';

-- 系统用户表（登录认证，JWT）
CREATE TABLE IF NOT EXISTS sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(32)  NOT NULL COMMENT '用户名',
    password    VARCHAR(100) NOT NULL COMMENT '密码(BCrypt密文)',
    nickname    VARCHAR(64)  DEFAULT NULL COMMENT '昵称',
    role        VARCHAR(20)  DEFAULT 'user' COMMENT '角色 admin/user（暂不区分权限）',
    create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) COMMENT='系统用户';

-- 预置管理员账号 admin / 123456（password 字段为 BCrypt 密文，请勿存明文）
INSERT INTO sys_user (username, password, nickname, role)
VALUES ('admin', '$2a$10$uM1kjqlTnwTLxrX0OY1JF.rsQGFQsDmiNfIH8WMwJnx54qUDV5x5q', '管理员', 'admin')
ON DUPLICATE KEY UPDATE username = username;
