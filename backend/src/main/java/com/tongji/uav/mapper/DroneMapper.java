package com.tongji.uav.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tongji.uav.entity.Drone;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DroneMapper extends BaseMapper<Drone> {
}
