package com.tongji.uav.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 禁飞区实体，对应表 no_fly_zone（圆形区域：中心经纬度 + 半径 km）。
 */
@Data
@TableName("no_fly_zone")
public class NoFlyZone {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private BigDecimal centerLng;

    private BigDecimal centerLat;

    /** 半径（km） */
    private BigDecimal radiusKm;
}
