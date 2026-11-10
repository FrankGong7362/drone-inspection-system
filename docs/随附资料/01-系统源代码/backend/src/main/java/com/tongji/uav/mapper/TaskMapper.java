package com.tongji.uav.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tongji.uav.entity.Task;
import org.apache.ibatis.annotations.Mapper;

/**
 * 巡检任务 Mapper（MyBatis-Plus，基础 CRUD 由 BaseMapper 提供）。
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {
}
