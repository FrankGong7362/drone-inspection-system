package com.tongji.uav.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tongji.uav.dto.DroneRequest;
import com.tongji.uav.entity.Drone;
import com.tongji.uav.mapper.DroneMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 无人机档案接口：查询供所有登录用户查看；新增/修改/删除由 AuthInterceptor 限定仅 admin。
 */
@RestController
@RequestMapping("/api/drones")
public class DroneController {

    private final DroneMapper droneMapper;

    public DroneController(DroneMapper droneMapper) {
        this.droneMapper = droneMapper;
    }

    /** 无人机列表，按 id 升序 */
    @GetMapping
    public List<Drone> list() {
        return droneMapper.selectList(new LambdaQueryWrapper<Drone>().orderByAsc(Drone::getId));
    }

    /** 新增无人机（仅 admin）。机号唯一。 */
    @PostMapping
    public Drone create(@Valid @RequestBody DroneRequest req) {
        ensureDroneNoUnique(req.getDroneNo(), null);
        Drone drone = new Drone();
        apply(drone, req);
        if (drone.getStatus() == null) {
            drone.setStatus("可用");
        }
        droneMapper.insert(drone);
        return droneMapper.selectById(drone.getId());
    }

    /** 修改无人机（仅 admin）。正在执行任务时不允许改状态。 */
    @PutMapping("/{id}")
    public Drone update(@PathVariable Long id, @Valid @RequestBody DroneRequest req) {
        Drone drone = droneMapper.selectById(id);
        if (drone == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "无人机不存在");
        }
        ensureDroneNoUnique(req.getDroneNo(), id);
        if (drone.getBindTaskId() != null && !"作业".equals(req.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该无人机正在执行任务，不能改为非作业状态");
        }
        apply(drone, req);
        if (drone.getStatus() == null) {
            drone.setStatus("可用");
        }
        droneMapper.updateById(drone);
        return droneMapper.selectById(id);
    }

    /** 删除无人机（仅 admin）。正在执行任务时不允许删除。 */
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Drone drone = droneMapper.selectById(id);
        if (drone == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "无人机不存在");
        }
        if (drone.getBindTaskId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该无人机正在执行任务，不能删除");
        }
        droneMapper.deleteById(id);
    }

    private void ensureDroneNoUnique(String droneNo, Long excludeId) {
        LambdaQueryWrapper<Drone> q = new LambdaQueryWrapper<Drone>().eq(Drone::getDroneNo, droneNo.trim());
        if (excludeId != null) {
            q.ne(Drone::getId, excludeId);
        }
        Long count = droneMapper.selectCount(q);
        if (count != null && count > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "机号已存在");
        }
    }

    private void apply(Drone drone, DroneRequest req) {
        drone.setDroneNo(req.getDroneNo().trim());
        drone.setModel(req.getModel());
        drone.setBatteryCapacity(req.getBatteryCapacity());
        drone.setMaxFlightRange(req.getMaxFlightRange());
        drone.setMaxSpeed(req.getMaxSpeed());
        if (req.getStatus() != null) {
            drone.setStatus(req.getStatus());
        }
    }
}
