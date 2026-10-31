package com.tongji.uav.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 操作审计日志条目（mission_log + 任务 / 无人机 / 航点名称）。
 */
@Data
public class AuditLogVO {

    private Long id;

    private Long taskId;

    private String taskNo;

    private String taskName;

    private Long droneId;

    private String droneNo;

    /** 起飞 / 到点 / 返航 / 降落 / 异常 */
    private String action;

    private Long waypointId;

    private String waypointName;

    private LocalDateTime logTime;

    private String detail;
}
