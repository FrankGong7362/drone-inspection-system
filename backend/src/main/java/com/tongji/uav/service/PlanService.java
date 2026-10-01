package com.tongji.uav.service;

import com.tongji.uav.client.PlanClient;
import com.tongji.uav.dto.PlanCommand;
import com.tongji.uav.dto.PlanRequest;
import com.tongji.uav.dto.PlanResponse;
import com.tongji.uav.dto.PlanResultVO;
import com.tongji.uav.entity.PlanningResult;
import com.tongji.uav.entity.Task;
import com.tongji.uav.mapper.PlanningResultMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Set;

/**
 * 路径规划业务：调算法服务、结果落库、回填任务总航程。
 */
@Service
public class PlanService {

    /** 契约约定的算法取值 */
    private static final Set<String> SUPPORTED = Set.of("ga", "aco", "astar", "greedy", "nearest");

    private final PlanClient planClient;
    private final PlanningResultMapper planningResultMapper;
    private final TaskService taskService;

    public PlanService(PlanClient planClient,
                       PlanningResultMapper planningResultMapper,
                       TaskService taskService) {
        this.planClient = planClient;
        this.planningResultMapper = planningResultMapper;
        this.taskService = taskService;
    }

    @Transactional
    public PlanResultVO plan(PlanCommand command) {
        // 1. 参数校验
        if (command.getTaskId() == null) {
            throw new IllegalArgumentException("缺少 taskId");
        }
        if (command.getStartPoint() == null || command.getStartPoint().getLng() == null) {
            throw new IllegalArgumentException("缺少起点 start_point");
        }
        if (command.getWaypoints() == null || command.getWaypoints().isEmpty()) {
            throw new IllegalArgumentException("航点列表不能为空");
        }
        String algorithm = command.getAlgorithm() == null || command.getAlgorithm().isBlank()
                ? "greedy" : command.getAlgorithm();
        if (!SUPPORTED.contains(algorithm)) {
            throw new IllegalArgumentException("未知算法: " + algorithm);
        }

        // 2. 调用算法服务（PlanRequest 序列化为 snake_case，符合契约）
        PlanRequest request = new PlanRequest();
        request.setAlgorithm(algorithm);
        request.setWaypoints(command.getWaypoints());
        request.setStartPoint(command.getStartPoint());
        request.setConstraints(command.getConstraints());

        PlanResponse resp = planClient.plan(request);
        if (resp == null || !"ok".equals(resp.getStatus())) {
            String msg = resp == null ? "算法服务无响应" : resp.getMessage();
            throw new IllegalStateException("路径规划失败: " + msg);
        }

        // 3. 结果落库 planning_result
        PlanningResult result = new PlanningResult();
        result.setTaskId(command.getTaskId());
        result.setAlgorithm(resp.getAlgorithm());
        result.setRoute(resp.getRoute());
        result.setTotalDistance(resp.getTotalDistanceKm() == null
                ? null : BigDecimal.valueOf(resp.getTotalDistanceKm()).setScale(2, RoundingMode.HALF_UP));
        result.setRunTimeMs(resp.getRunTimeMs());
        result.setFeasible(resp.getFeasible());
        planningResultMapper.insert(result);

        // 4. 回填任务总航程
        Task task = taskService.getById(command.getTaskId());
        if (task == null) {
            throw new IllegalArgumentException("任务不存在: " + command.getTaskId());
        }
        task.setTotalDistance(result.getTotalDistance());
        taskService.updateById(task);

        // 5. 返回视图
        PlanResultVO vo = new PlanResultVO();
        vo.setResultId(result.getId());
        vo.setTaskId(command.getTaskId());
        vo.setAlgorithm(resp.getAlgorithm());
        vo.setRoute(resp.getRoute());
        vo.setTotalDistanceKm(resp.getTotalDistanceKm());
        vo.setFeasible(resp.getFeasible());
        vo.setRunTimeMs(resp.getRunTimeMs());
        vo.setMessage(resp.getMessage());
        vo.setConvergence(resp.getConvergence());
        vo.setDetailPaths(resp.getDetailPaths());
        return vo;
    }
}
