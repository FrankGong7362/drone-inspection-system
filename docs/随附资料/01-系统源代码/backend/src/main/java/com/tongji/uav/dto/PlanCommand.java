package com.tongji.uav.dto;

import lombok.Data;

import java.util.List;

/**
 * 前端发起规划请求（POST /api/plan），camelCase，由后端转换为 PlanRequest 调算法服务。
 */
@Data
public class PlanCommand {

    /** 巡检任务 id，用于结果落库 */
    private Long taskId;

    /** ga / aco / astar / greedy / nearest，默认 greedy */
    private String algorithm;

    private List<PlanRequest.Waypoint> waypoints;

    private PlanRequest.Point startPoint;

    private PlanRequest.Constraint constraints;
}
