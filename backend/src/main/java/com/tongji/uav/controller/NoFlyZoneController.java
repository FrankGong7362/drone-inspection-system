package com.tongji.uav.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tongji.uav.dto.NoFlyZoneRequest;
import com.tongji.uav.entity.NoFlyZone;
import com.tongji.uav.mapper.NoFlyZoneMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 禁飞区接口：查询供所有登录用户查看；新增/修改/删除由 AuthInterceptor 限定仅 admin。
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

    /** 新增禁飞区（仅 admin）。 */
    @PostMapping
    public NoFlyZone create(@Valid @RequestBody NoFlyZoneRequest req) {
        NoFlyZone zone = new NoFlyZone();
        apply(zone, req);
        noFlyZoneMapper.insert(zone);
        return noFlyZoneMapper.selectById(zone.getId());
    }

    /** 修改禁飞区（仅 admin）。 */
    @PutMapping("/{id}")
    public NoFlyZone update(@PathVariable Long id, @Valid @RequestBody NoFlyZoneRequest req) {
        NoFlyZone zone = noFlyZoneMapper.selectById(id);
        if (zone == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "禁飞区不存在");
        }
        apply(zone, req);
        noFlyZoneMapper.updateById(zone);
        return noFlyZoneMapper.selectById(id);
    }

    /** 删除禁飞区（仅 admin）。 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        if (noFlyZoneMapper.selectById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "禁飞区不存在");
        }
        noFlyZoneMapper.deleteById(id);
    }

    private void apply(NoFlyZone zone, NoFlyZoneRequest req) {
        zone.setName(req.getName() == null || req.getName().isBlank() ? "禁飞区" : req.getName().trim());
        zone.setCenterLng(req.getCenterLng());
        zone.setCenterLat(req.getCenterLat());
        zone.setRadiusKm(req.getRadiusKm());
    }
}
