package com.tongji.uav.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tongji.uav.entity.NoFlyZone;
import com.tongji.uav.entity.Task;
import com.tongji.uav.entity.Waypoint;
import com.tongji.uav.mapper.NoFlyZoneMapper;
import com.tongji.uav.mapper.WaypointMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 航点业务：查询任务航点、生成模拟航点（毕业设计演示数据，围绕指定起飞点中心随机分布）。
 */
@Service
public class WaypointService extends ServiceImpl<WaypointMapper, Waypoint> {

    @Autowired
    private TaskService taskService;

    @Autowired
    private NoFlyZoneMapper noFlyZoneMapper;

    /** 地球半径（km），用于 haversine 球面距离。 */
    private static final double EARTH_R_KM = 6371.0;

    /**
     * 判断坐标是否落在任一禁飞区圆内（haversine 距离 &lt; 半径）。
     */
    private boolean inNoFlyZone(BigDecimal lng, BigDecimal lat) {
        double lat1 = Math.toRadians(lat.doubleValue());
        double lng1 = Math.toRadians(lng.doubleValue());
        for (NoFlyZone z : noFlyZoneMapper.selectList(null)) {
            double lat2 = Math.toRadians(z.getCenterLat().doubleValue());
            double lng2 = Math.toRadians(z.getCenterLng().doubleValue());
            double a = Math.pow(Math.sin((lat2 - lat1) / 2), 2)
                    + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin((lng2 - lng1) / 2), 2);
            double dist = 2 * EARTH_R_KM * Math.asin(Math.sqrt(a));
            if (dist < z.getRadiusKm().doubleValue()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 查询任务航点，按预设顺序排序。
     */
    public List<Waypoint> listByTask(Long taskId) {
        return list(new LambdaQueryWrapper<Waypoint>()
                .eq(Waypoint::getTaskId, taskId)
                .orderByAsc(Waypoint::getSeq));
    }

    /**
     * 新增单个航点（前端地图选点）：seq 取当前最大值+1，首航点自动设为任务起点。
     */
    @Transactional
    public Waypoint addWaypoint(Long taskId, String name, BigDecimal longitude, BigDecimal latitude) {
        Task task = taskService.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        if (longitude == null || latitude == null
                || longitude.doubleValue() < -180 || longitude.doubleValue() > 180
                || latitude.doubleValue() < -90 || latitude.doubleValue() > 90) {
            throw new IllegalArgumentException("经纬度数值非法");
        }
        List<Waypoint> existing = listByTask(taskId);
        int nextSeq = existing.stream()
                .mapToInt(w -> w.getSeq() == null ? 0 : w.getSeq())
                .max().orElse(0) + 1;

        Waypoint wp = new Waypoint();
        wp.setTaskId(taskId);
        wp.setName(name == null || name.isBlank() ? "巡检点" + nextSeq : name.trim());
        wp.setLongitude(longitude.setScale(6, RoundingMode.HALF_UP));
        wp.setLatitude(latitude.setScale(6, RoundingMode.HALF_UP));
        wp.setSeq(nextSeq);
        wp.setIsForbidden(0);
        save(wp);

        // 任务首个航点自动设为起点
        if (existing.isEmpty()) {
            task.setStartPointId(wp.getId());
            taskService.updateById(task);
        }
        return wp;
    }

    /**
     * 删除单个航点：若删除的是任务起点，起点自动移交剩余航点中 seq 最小者。
     */
    @Transactional
    public void removeWaypoint(Long taskId, Long waypointId) {
        Waypoint wp = getById(waypointId);
        if (wp == null || !wp.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("航点不存在或不属于该任务");
        }
        removeById(waypointId);

        Task task = taskService.getById(taskId);
        if (task != null && waypointId.equals(task.getStartPointId())) {
            List<Waypoint> rest = listByTask(taskId);
            task.setStartPointId(rest.isEmpty() ? null : rest.get(0).getId());
            taskService.updateById(task);
        }
    }

    /** 重命名航点（航点列表改名），校验归属与名称非空 */
    public Waypoint renameWaypoint(Long taskId, Long waypointId, String name) {
        Waypoint wp = getById(waypointId);
        if (wp == null || !wp.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("航点不存在或不属于该任务");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("名称不能为空");
        }
        wp.setName(name.trim());
        updateById(wp);
        return wp;
    }

    /**
     * 为任务生成 count 个模拟航点：先清空旧航点，再围绕指定中心随机生成。
     * 第一个航点坐标精确等于中心（centerLng/centerLat），名称"起飞点"，并作为任务起点
     * （回填 inspection_task.start_point_id）；其余 count-1 个围绕中心随机分布。
     */
    @Transactional
    public List<Waypoint> generateForTask(Long taskId, int count, BigDecimal centerLng, BigDecimal centerLat) {
        Task task = taskService.getById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + taskId);
        }
        if (count < 2 || count > 30) {
            throw new IllegalArgumentException("航点数量需在 2~30 之间");
        }
        if (centerLng == null || centerLat == null
                || centerLng.doubleValue() < -180 || centerLng.doubleValue() > 180
                || centerLat.doubleValue() < -90 || centerLat.doubleValue() > 90) {
            throw new IllegalArgumentException("起飞点中心经纬度缺失或非法");
        }
        if (inNoFlyZone(centerLng, centerLat)) {
            throw new IllegalArgumentException("起飞点位于禁飞区，请更换起飞点");
        }
        remove(new LambdaQueryWrapper<Waypoint>().eq(Waypoint::getTaskId, taskId));

        ThreadLocalRandom random = ThreadLocalRandom.current();
        List<Waypoint> waypoints = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Waypoint wp = new Waypoint();
            wp.setTaskId(taskId);
            if (i == 0) {
                // 第一个航点 = 起飞点，坐标取中心精确值
                wp.setName("起飞点");
                wp.setLongitude(centerLng.setScale(6, RoundingMode.HALF_UP));
                wp.setLatitude(centerLat.setScale(6, RoundingMode.HALF_UP));
            } else {
                // 随机生成候选航点，若落入禁飞区则重试，最多 50 次
                BigDecimal lng = null;
                BigDecimal lat = null;
                boolean ok = false;
                for (int retry = 0; retry < 50; retry++) {
                    lng = BigDecimal.valueOf(centerLng.doubleValue() + random.nextDouble(-0.02, 0.02))
                            .setScale(6, RoundingMode.HALF_UP);
                    lat = BigDecimal.valueOf(centerLat.doubleValue() + random.nextDouble(-0.02, 0.02))
                            .setScale(6, RoundingMode.HALF_UP);
                    if (!inNoFlyZone(lng, lat)) {
                        ok = true;
                        break;
                    }
                }
                if (!ok) {
                    throw new IllegalArgumentException("无法在起飞点附近生成足够航点，请更换起飞点或调整禁飞区");
                }
                wp.setName("巡检点" + i);
                wp.setLongitude(lng);
                wp.setLatitude(lat);
            }
            wp.setAltitude(BigDecimal.valueOf(50 + random.nextInt(30)));
            wp.setSeq(i + 1);
            wp.setIsForbidden(0);
            waypoints.add(wp);
        }
        saveBatch(waypoints);

        // 第一个航点作为起点
        task.setStartPointId(waypoints.get(0).getId());
        taskService.updateById(task);
        return waypoints;
    }
}
