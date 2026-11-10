package com.tongji.uav.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

/**
 * 路径规划请求，对应算法服务 POST /api/plan 的请求体。
 * 字段序列化为 snake_case，与 docs/api-contract.md 契约一致。
 */
@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlanRequest {

    /** ga / aco / astar / greedy / nearest */
    private String algorithm;

    private List<Waypoint> waypoints;

    private Point startPoint;

    private Constraint constraints;

    @Data
    public static class Waypoint {
        private Long id;
        private Double lng;
        private Double lat;
    }

    @Data
    public static class Point {
        private Long id;
        private Double lng;
        private Double lat;
    }

    /**
     * 约束条件。@JsonNaming 使序列化输出 snake_case（契约），
     * @JsonAlias 使反序列化同时兼容前端 camelCase 与算法服务 snake_case 两种输入。
     */
    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class Constraint {
        @JsonAlias({"maxFlightRangeKm"})
        private Double maxFlightRangeKm;

        @JsonAlias({"noFlyZones"})
        private List<NoFlyZone> noFlyZones;

        /** 禁飞区安全距离 buffer（km），序列化输出 no_fly_buffer_km（契约） */
        @JsonAlias({"noFlyBufferKm"})
        private Double noFlyBufferKm = 0.5;
    }

    /**
     * 禁飞区：输出 snake_case（契约），输入兼容 camelCase。
     */
    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class NoFlyZone {
        @JsonAlias({"centerLng"})
        private Double centerLng;

        @JsonAlias({"centerLat"})
        private Double centerLat;

        @JsonAlias({"radiusKm"})
        private Double radiusKm;
    }
}
