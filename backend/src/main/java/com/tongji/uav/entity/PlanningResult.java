package com.tongji.uav.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 路径规划结果实体，对应表 planning_result（算法输出落库）。
 */
@Data
@TableName(value = "planning_result", autoResultMap = true)
public class PlanningResult {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /** ga / aco / astar / greedy / nearest */
    private String algorithm;

    /** 访问顺序（waypoint.id 数组，含起点往返），MySQL JSON 列 */
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Long> route;

    private BigDecimal totalDistance;

    private Long runTimeMs;

    /** 是否满足约束 */
    private Boolean feasible;

    private LocalDateTime createTime;
}
