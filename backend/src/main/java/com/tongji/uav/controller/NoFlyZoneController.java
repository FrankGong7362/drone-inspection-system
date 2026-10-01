package com.tongji.uav.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tongji.uav.entity.NoFlyZone;
import com.tongji.uav.mapper.NoFlyZoneMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 禁飞区只读接口：供前端地图展示（本次仅查询，不做增删改）。
 */
@RestController
@RequestMapping("/api/no-fly-zones")
public class NoFlyZoneController {

    private final NoFlyZoneMapper noFlyZoneMapper;

    public NoFlyZoneController(NoFlyZoneMapper noFlyZoneMapper) {
        this.noFlyZoneMapper = noFlyZoneMapper;
    }

    /** 禁飞区列表，按 id 升序 */
    @GetMapping
    public List<NoFlyZone> list() {
        return noFlyZoneMapper.selectList(new LambdaQueryWrapper<NoFlyZone>().orderByAsc(NoFlyZone::getId));
    }
}
