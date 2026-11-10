package com.tongji.uav.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tongji.uav.dto.AuditLogVO;
import com.tongji.uav.dto.AuditPageVO;
import com.tongji.uav.entity.Drone;
import com.tongji.uav.entity.MissionLog;
import com.tongji.uav.entity.Task;
import com.tongji.uav.entity.Waypoint;
import com.tongji.uav.mapper.DroneMapper;
import com.tongji.uav.mapper.MissionLogMapper;
import com.tongji.uav.mapper.TaskMapper;
import com.tongji.uav.mapper.WaypointMapper;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 操作审计日志：基于 mission_log 汇总任务执行留痕（下发/到点/返航/降落/异常），
 * 供管理员按任务、动作、关键字筛选并分页查看。
 */
@Service
public class AuditService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MissionLogMapper missionLogMapper;
    private final TaskMapper taskMapper;
    private final DroneMapper droneMapper;
    private final WaypointMapper waypointMapper;

    public AuditService(MissionLogMapper missionLogMapper, TaskMapper taskMapper,
                        DroneMapper droneMapper, WaypointMapper waypointMapper) {
        this.missionLogMapper = missionLogMapper;
        this.taskMapper = taskMapper;
        this.droneMapper = droneMapper;
        this.waypointMapper = waypointMapper;
    }

    /** 分页查询审计日志，按 id 倒序（最新在前）。 */
    public AuditPageVO page(int page, int size, Long taskId, String action, String keyword) {
        int pageNo = Math.max(page, 1);
        int pageSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Long total = missionLogMapper.selectCount(buildWrapper(taskId, action, keyword));
        List<MissionLog> rows = missionLogMapper.selectList(buildWrapper(taskId, action, keyword)
                .orderByDesc(MissionLog::getId)
                .last("limit " + ((pageNo - 1) * pageSize) + "," + pageSize));

        Map<Long, Task> tasks = batchTasks(rows);
        Map<Long, Drone> drones = batchDrones(rows);
        Map<Long, Waypoint> waypoints = batchWaypoints(rows);

        AuditPageVO vo = new AuditPageVO();
        vo.setTotal(total == null ? 0L : total);
        vo.setPage(pageNo);
        vo.setSize(pageSize);
        vo.setRecords(rows.stream().map(log -> toVO(log, tasks, drones, waypoints))
                .collect(Collectors.toList()));
        return vo;
    }

    private LambdaQueryWrapper<MissionLog> buildWrapper(Long taskId, String action, String keyword) {
        LambdaQueryWrapper<MissionLog> q = new LambdaQueryWrapper<>();
        if (taskId != null) {
            q.eq(MissionLog::getTaskId, taskId);
        }
        if (action != null && !action.isBlank()) {
            q.eq(MissionLog::getAction, action.trim());
        }
        if (keyword != null && !keyword.isBlank()) {
            q.like(MissionLog::getDetail, keyword.trim());
        }
        return q;
    }

    private AuditLogVO toVO(MissionLog log, Map<Long, Task> tasks,
                            Map<Long, Drone> drones, Map<Long, Waypoint> waypoints) {
        AuditLogVO vo = new AuditLogVO();
        vo.setId(log.getId());
        vo.setTaskId(log.getTaskId());
        vo.setDroneId(log.getDroneId());
        vo.setAction(log.getAction());
        vo.setWaypointId(log.getWaypointId());
        vo.setLogTime(log.getLogTime());
        vo.setDetail(log.getDetail());
        Task task = tasks.get(log.getTaskId());
        if (task != null) {
            vo.setTaskNo(task.getTaskNo());
            vo.setTaskName(task.getTaskName());
        }
        Drone drone = drones.get(log.getDroneId());
        if (drone != null) {
            vo.setDroneNo(drone.getDroneNo());
        }
        Waypoint waypoint = waypoints.get(log.getWaypointId());
        if (waypoint != null) {
            vo.setWaypointName(waypoint.getName());
        }
        return vo;
    }

    private Map<Long, Task> batchTasks(List<MissionLog> rows) {
        Set<Long> ids = rows.stream().map(MissionLog::getTaskId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return taskMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Task::getId, Function.identity()));
    }

    private Map<Long, Drone> batchDrones(List<MissionLog> rows) {
        Set<Long> ids = rows.stream().map(MissionLog::getDroneId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return droneMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Drone::getId, Function.identity()));
    }

    private Map<Long, Waypoint> batchWaypoints(List<MissionLog> rows) {
        Set<Long> ids = rows.stream().map(MissionLog::getWaypointId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return waypointMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Waypoint::getId, Function.identity()));
    }
}
