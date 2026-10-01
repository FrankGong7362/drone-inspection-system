package com.tongji.uav.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tongji.uav.entity.Waypoint;
import org.apache.ibatis.annotations.Mapper;

/**
 * 航点 Mapper（MyBatis-Plus，基础 CRUD 由 BaseMapper 提供）。
 */
@Mapper
public interface WaypointMapper extends BaseMapper<Waypoint> {
}
