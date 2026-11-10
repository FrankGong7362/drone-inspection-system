package com.tongji.uav.controller;

import com.tongji.uav.dto.DispatchRequest;
import com.tongji.uav.dto.LatestPlanVO;
import com.tongji.uav.dto.MissionLogRequest;
import com.tongji.uav.entity.MissionLog;
import com.tongji.uav.entity.Task;
import com.tongji.uav.service.ExecutionService;
import com.tongji.uav.service.TaskAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 任务执行模拟接口（子资源挂在 /api/tasks 下，权限由 TaskAccessGuard 判定）：
 * 下发 → 飞行过程日志 → 完成/取消。写操作仅任务 owner/admin。
 */
@RestController
@RequestMapping("/api/tasks/{id}")
public class TaskExecutionController {

    private final ExecutionService executionService;
    private final TaskAccessGuard accessGuard;

    public TaskExecutionController(ExecutionService executionService, TaskAccessGuard accessGuard) {
        this.executionService = executionService;
        this.accessGuard = accessGuard;
    }

    /** 最近一次规划结果（供执行页面绘制航线；无规划返回 200 空体） */
    @GetMapping("/latest-plan")
    public LatestPlanVO latestPlan(@PathVariable Long id, HttpServletRequest request) {
        accessGuard.requireVisible(id, currentUserId(request), currentRole(request));
        return executionService.latestPlan(id);
    }

    /** 执行日志（按时间正序） */
    @GetMapping("/logs")
    public List<MissionLog> logs(@PathVariable Long id, HttpServletRequest request) {
        accessGuard.requireVisible(id, currentUserId(request), currentRole(request));
        return executionService.logs(id);
    }

    /** 下发任务：绑定无人机，任务转为执行中 */
    @PostMapping("/dispatch")
    public Task dispatch(@PathVariable Long id, @RequestBody DispatchRequest body,
                         HttpServletRequest request) {
        Task task = accessGuard.requireOwned(id, currentUserId(request), currentRole(request));
        try {
            return executionService.dispatch(task, body == null ? null : body.getDroneId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** 追加执行日志（到点 / 异常） */
    @PostMapping("/logs")
    public MissionLog appendLog(@PathVariable Long id, @RequestBody MissionLogRequest body,
                                HttpServletRequest request) {
        Task task = accessGuard.requireOwned(id, currentUserId(request), currentRole(request));
        try {
            return executionService.appendLog(task, body);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** 完成执行：释放无人机，写入降落日志 */
    @PostMapping("/finish")
    public Task finish(@PathVariable Long id, HttpServletRequest request) {
        Task task = accessGuard.requireOwned(id, currentUserId(request), currentRole(request));
        try {
            return executionService.finish(task);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** 取消任务：释放无人机，写入返航日志 */
    @PostMapping("/cancel")
    public Task cancel(@PathVariable Long id, HttpServletRequest request) {
        Task task = accessGuard.requireOwned(id, currentUserId(request), currentRole(request));
        try {
            return executionService.cancel(task);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }

    private String currentRole(HttpServletRequest request) {
        return (String) request.getAttribute("role");
    }
}
