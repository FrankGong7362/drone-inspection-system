package com.tongji.uav.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tongji.uav.entity.PlanningResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 路径规划结果 Mapper（MyBatis-Plus，基础 CRUD 由 BaseMapper 提供）。
 */
@Mapper
public interface PlanningResultMapper extends BaseMapper<PlanningResult> {
}
