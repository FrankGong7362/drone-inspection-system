package com.tongji.uav.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 规划历史列表项（不含 route / detailPaths 大字段，详情按需拉取）。
 */
@Data
public class PlanHistoryVO {

    private Long resultId;

    private Long taskId;

    /** ga / aco / astar / greedy / nearest */
    private String algorithm;

    private Double totalDistanceKm;

    private Long runTimeMs;

    private Boolean feasible;

    /** 访问的巡检点数（不含末位回到起点） */
    private Integer waypointCount;

    private LocalDateTime createTime;
}
