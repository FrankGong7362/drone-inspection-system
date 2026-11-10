package com.tongji.uav.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 航点/巡检点实体，对应表 waypoint。
 */
@Data
@TableName("waypoint")
public class Waypoint {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String name;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private BigDecimal altitude;

    /** 预设顺序 */
    private Integer seq;

    /** 是否禁飞点 0/1 */
    private Integer isForbidden;
}
