package com.tongji.uav.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * 路径规划响应，对应算法服务 POST /api/plan 的响应体。
 * 字段反序列化为 snake_case，与 docs/api-contract.md 契约一致。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlanResponse {

    private String status;

    private String algorithm;

    /** 访问顺序，元素为航点 id */
    private List<Long> route;

    private Double totalDistanceKm;

    /** 是否满足约束（续航、禁飞区） */
    private Boolean feasible;

    private Long runTimeMs;

    private String message;

    /** 每代全局最优总航程（km），仅 GA/ACO 返回，供收敛曲线展示 */
    private List<Double> convergence;

    /** 避障折线：完整航线经纬度序列（Leaflet 顺序 [[lat, lng], ...]），有禁飞区时含弯折 */
    private List<List<Double>> detailPaths;
}
