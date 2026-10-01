package com.tongji.uav.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 巡检任务实体，对应表 inspection_task。
 */
@Data
@TableName("inspection_task")
public class Task {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskNo;

    private String taskName;

    /** 电力 / 园区 / 交通 */
    private String taskType;

    /** 待执行 / 执行中 / 已完成 / 已取消 */
    private String status;

    private Long startPointId;

    private BigDecimal totalDistance;

    private Integer estimatedDuration;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
