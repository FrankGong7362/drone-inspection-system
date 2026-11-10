package com.tongji.uav.dto;

import lombok.Data;

/**
 * 执行过程日志上报请求（模拟飞行到点 / 异常）。
 */
@Data
public class MissionLogRequest {

    /** 到点 / 异常 */
    private String action;

    private Long waypointId;

    private String detail;
}
