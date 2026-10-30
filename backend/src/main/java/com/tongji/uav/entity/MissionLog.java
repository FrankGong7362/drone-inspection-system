package com.tongji.uav.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务执行日志实体，对应表 mission_log。
 */
@Data
@TableName("mission_log")
public class MissionLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private Long droneId;

    /** 起飞 / 到点 / 返航 / 降落 / 异常 */
    private String action;

    private Long waypointId;

    private LocalDateTime logTime;

    private String detail;
}
