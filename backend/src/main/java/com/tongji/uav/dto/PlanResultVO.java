package com.tongji.uav.dto;

import lombok.Data;

import java.util.List;

/**
 * 规划结果视图，返回给前端（camelCase），同时已落库 planning_result。
 */
@Data
public class PlanResultVO {

    /** planning_result 表主键 */
    private Long resultId;

    private Long taskId;

    private String algorithm;

    /** 访问顺序（waypoint.id 数组，含起点往返） */
    private List<Long> route;

    private Double totalDistanceKm;

    private Boolean feasible;

    private Long runTimeMs;

    private String message;

    /** 每代全局最优总航程（km），仅 GA/ACO 返回，供收敛曲线展示 */
    private List<Double> convergence;

    /** 避障折线（camelCase 透传给前端）：优先用它画绕行航线，无则回退 route 直线 */
    private List<List<Double>> detailPaths;
}
