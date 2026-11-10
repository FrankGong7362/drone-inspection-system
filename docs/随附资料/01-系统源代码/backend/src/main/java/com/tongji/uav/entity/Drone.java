package com.tongji.uav.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 无人机档案实体，对应表 drone。
 */
@Data
@TableName("drone")
public class Drone {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 机号/SN，唯一 */
    private String droneNo;

    private String model;

    /** 电量(mAh) */
    private BigDecimal batteryCapacity;

    /** 最大航程(km) */
    private BigDecimal maxFlightRange;

    /** 巡航速度(km/h) */
    private BigDecimal maxSpeed;

    /** 可用 / 作业 / 维护 / 离线 */
    private String status;

    /** 当前绑定任务（执行中时非空；释放时必须能把 null 写回，故用 ALWAYS 策略） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long bindTaskId;
}
