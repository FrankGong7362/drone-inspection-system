package com.tongji.uav.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.tongji.uav.dto.LatestPlanVO;
import com.tongji.uav.dto.MissionLogRequest;
import com.tongji.uav.entity.Drone;
import com.tongji.uav.entity.MissionLog;
import com.tongji.uav.entity.PlanningResult;
import com.tongji.uav.entity.Task;
import com.tongji.uav.mapper.DroneMapper;
import com.tongji.uav.mapper.MissionLogMapper;
import com.tongji.uav.mapper.PlanningResultMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

/**
 * 任务执行模拟：任务下发（绑定无人机）→ 飞行过程中到点日志 → 完成/取消（释放无人机）。
 * 演示环境下飞行轨迹由前端按规划折线回放，后端负责状态机与执行日志留痕。
 */
@Service
public class ExecutionService {

    /** 前端可追加的日志动作 */
    private static final Set<String> LOG_ACTIONS = Set.of("到点", "异常");

    private final TaskService taskService;
    private final DroneMapper droneMapper;
    private final MissionLogMapper missionLogMapper;
    private final PlanningResultMapper planningResultMapper;

    public ExecutionService(TaskService taskService, DroneMapper droneMapper,
                            MissionLogMapper missionLogMapper,
                            PlanningResultMapper planningResultMapper) {
        this.taskService = taskService;
        this.droneMapper = droneMapper;
        this.missionLogMapper = missionLogMapper;
        this.planningResultMapper = planningResultMapper;
    }

    /** 任务最近一次规划结果（供执行页面绘制航线）；无规划返回 null。 */
    public LatestPlanVO latestPlan(Long taskId) {
        PlanningResult pr = latestPlanningResult(taskId);
        if (pr == null) {
            return null;
        }
        LatestPlanVO vo = new LatestPlanVO();
        vo.setResultId(pr.getId());
        vo.setAlgorithm(pr.getAlgorithm());
        vo.setRoute(pr.getRoute());
        vo.setTotalDistanceKm(pr.getTotalDistance() == null ? null : pr.getTotalDistance().doubleValue());
        vo.setFeasible(pr.getFeasible());
        vo.setDetailPaths(pr.getDetailPaths());
        return vo;
    }

    /** 下发任务：校验无人机可用 + 已有规划 → 任务执行中、无人机作业并绑定。 */
    @Transactional
    public Task dispatch(Task task, Long droneId) {
        if ("执行中".equals(task.getStatus())) {
            throw new IllegalArgumentException("任务已在执行中");
        }
        PlanningResult pr = latestPlanningResult(task.getId());
        if (pr == null || pr.getRoute() == null || pr.getRoute().isEmpty()) {
            throw new IllegalArgumentException("请先完成路径规划后再下发任务");
        }
        if (droneId == null) {
            throw new IllegalArgumentException("请选择无人机");
        }
        Drone drone = droneMapper.selectById(droneId);
        if (drone == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "无人机不存在");
        }
        if (!"可用".equals(drone.getStatus())) {
            throw new IllegalArgumentException("无人机当前不可用（状态：" + drone.getStatus() + "）");
        }

        task.setStatus("执行中");
        taskService.updateById(task);

        drone.setStatus("作业");
        drone.setBindTaskId(task.getId());
        droneMapper.updateById(drone);

        MissionLog log = new MissionLog();
        log.setTaskId(task.getId());
        log.setDroneId(drone.getId());
        log.setAction("起飞");
        log.setDetail("任务下发，无人机 " + drone.getDroneNo() + " 起飞");
        missionLogMapper.insert(log);
        return task;
    }

    /** 追加执行过程日志（到点 / 异常），任务必须处于执行中。 */
    public MissionLog appendLog(Task task, MissionLogRequest req) {
        if (!"执行中".equals(task.getStatus())) {
            throw new IllegalArgumentException("仅执行中的任务可追加执行日志");
        }
        String action = req.getAction() == null ? "到点" : req.getAction();
        if (!LOG_ACTIONS.contains(action)) {
            throw new IllegalArgumentException("仅支持追加 到点/异常 日志");
        }
        MissionLog log = new MissionLog();
        log.setTaskId(task.getId());
        Drone drone = boundDrone(task.getId());
        if (drone != null) {
            log.setDroneId(drone.getId());
        }
        log.setAction(action);
        log.setWaypointId(req.getWaypointId());
        log.setDetail(req.getDetail());
        missionLogMapper.insert(log);
        return log;
    }

    /** 完成执行：任务已完成、无人机释放为可用、写入降落日志。 */
    @Transactional
    public Task finish(Task task) {
        if (!"执行中".equals(task.getStatus())) {
            throw new IllegalArgumentException("仅执行中的任务可完成");
        }
        task.setStatus("已完成");
        taskService.updateById(task);

        Drone drone = boundDrone(task.getId());
        if (drone != null) {
            MissionLog log = new MissionLog();
            log.setTaskId(task.getId());
            log.setDroneId(drone.getId());
            log.setAction("降落");
            log.setDetail("无人机 " + drone.getDroneNo() + " 完成航线并降落，任务结束");
            missionLogMapper.insert(log);
            releaseDrone(drone);
        }
        return task;
    }

    /** 取消任务：待执行/执行中可取消；执行中则释放无人机并写入返航日志。 */
    @Transactional
    public Task cancel(Task task) {
        if ("已完成".equals(task.getStatus()) || "已取消".equals(task.getStatus())) {
            throw new IllegalArgumentException("任务已结束，无需取消");
        }
        boolean flying = "执行中".equals(task.getStatus());
        task.setStatus("已取消");
        taskService.updateById(task);

        Drone drone = boundDrone(task.getId());
        if (drone != null) {
            MissionLog log = new MissionLog();
            log.setTaskId(task.getId());
            log.setDroneId(drone.getId());
            log.setAction(flying ? "返航" : "异常");
            log.setDetail(flying
                    ? "任务取消，无人机 " + drone.getDroneNo() + " 返航"
                    : "任务取消，无人机未起飞");
            missionLogMapper.insert(log);
            releaseDrone(drone);
        }
        return task;
    }

    /** 执行日志（按时间正序）。 */
    public List<MissionLog> logs(Long taskId) {
        return missionLogMapper.selectList(new LambdaQueryWrapper<MissionLog>()
                .eq(MissionLog::getTaskId, taskId)
                .orderByAsc(MissionLog::getId));
    }

    private PlanningResult latestPlanningResult(Long taskId) {
        return planningResultMapper.selectOne(new LambdaQueryWrapper<PlanningResult>()
                .eq(PlanningResult::getTaskId, taskId)
                .orderByDesc(PlanningResult::getId)
                .last("limit 1"));
    }

    private Drone boundDrone(Long taskId) {
        return droneMapper.selectOne(new LambdaQueryWrapper<Drone>()
                .eq(Drone::getBindTaskId, taskId)
                .last("limit 1"));
    }

    private void releaseDrone(Drone drone) {
        drone.setBindTaskId(null);
        drone.setStatus("可用");
        droneMapper.updateById(drone);
    }
}
