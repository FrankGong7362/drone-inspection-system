package com.tongji.uav.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 禁飞区新增/修改请求。字段 camelCase（与前端契约一致）。
 */
@Data
public class NoFlyZoneRequest {

    @Size(max = 64, message = "名称最长 64 个字符")
    private String name;

    @NotNull(message = "中心经度不能为空")
    @DecimalMin(value = "-180", message = "经度范围 -180 ~ 180")
    @DecimalMax(value = "180", message = "经度范围 -180 ~ 180")
    private BigDecimal centerLng;

    @NotNull(message = "中心纬度不能为空")
    @DecimalMin(value = "-90", message = "纬度范围 -90 ~ 90")
    @DecimalMax(value = "90", message = "纬度范围 -90 ~ 90")
    private BigDecimal centerLat;

    @NotNull(message = "半径不能为空")
    @DecimalMin(value = "0", inclusive = false, message = "半径必须大于 0")
    @DecimalMax(value = "50", message = "半径不能超过 50 km")
    private BigDecimal radiusKm;
}
