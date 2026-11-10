package com.tongji.uav.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 单次规划结果详情（含访问顺序与避障折线），用于历史回看与前端重绘。
 */
@Data
public class PlanDetailVO {

    private Long resultId;

    private Long taskId;

    private String algorithm;

    private List<Long> route;

    private Double totalDistanceKm;

    private Long runTimeMs;

    private Boolean feasible;

    private List<List<Double>> detailPaths;

    private LocalDateTime createTime;
}
