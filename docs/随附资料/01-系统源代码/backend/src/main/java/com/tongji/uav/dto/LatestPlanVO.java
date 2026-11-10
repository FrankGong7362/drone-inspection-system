package com.tongji.uav.dto;

import lombok.Data;

import java.util.List;

/**
 * 任务最近一次规划结果（供执行模拟页面绘制航线），camelCase。
 */
@Data
public class LatestPlanVO {

    private Long resultId;

    private String algorithm;

    private List<Long> route;

    private Double totalDistanceKm;

    private Boolean feasible;

    /** 避障折线 [[lat, lng], ...]，无则前端回退用航点连线 */
    private List<List<Double>> detailPaths;
}
