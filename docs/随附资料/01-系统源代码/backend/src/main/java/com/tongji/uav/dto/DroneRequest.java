package com.tongji.uav.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 无人机档案新增/修改请求（camelCase，与前端契约一致）。
 */
@Data
public class DroneRequest {

    @NotBlank(message = "机号不能为空")
    @Size(max = 32, message = "机号最长 32 个字符")
    private String droneNo;

    @Size(max = 64, message = "型号最长 64 个字符")
    private String model;

    /** 电量(mAh) */
    @DecimalMin(value = "0", message = "电量不能为负")
    @DecimalMax(value = "100000", message = "电量超出合理范围")
    private BigDecimal batteryCapacity;

    /** 最大航程(km) */
    @DecimalMin(value = "0", message = "最大航程不能为负")
    @DecimalMax(value = "1000", message = "最大航程超出合理范围")
    private BigDecimal maxFlightRange;

    /** 巡航速度(km/h) */
    @DecimalMin(value = "0", message = "巡航速度不能为负")
    @DecimalMax(value = "200", message = "巡航速度超出合理范围")
    private BigDecimal maxSpeed;

    /** 可用 / 作业 / 维护 / 离线 */
    @Pattern(regexp = "可用|作业|维护|离线", message = "状态只能是 可用/作业/维护/离线")
    private String status;
}
