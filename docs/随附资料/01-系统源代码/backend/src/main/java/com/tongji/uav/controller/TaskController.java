package com.tongji.uav.controller;

import com.tongji.uav.entity.Task;
import com.tongji.uav.entity.Waypoint;
import com.tongji.uav.service.TaskAccessGuard;
import com.tongji.uav.service.TaskService;
import com.tongji.uav.service.WaypointService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * 巡检任务接口。REST 风格，供前端调用；含任务航点子资源。
 * 归属规则：所有人可建任务（创建者即 owner）；owner/admin 可改可删；
 * 私有任务仅 owner/admin 可见，公开任务所有人可查看并规划（由 TaskAccessGuard 判定）。
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final WaypointService waypointService;
    private final TaskAccessGuard accessGuard;

    public TaskController(TaskService taskService, WaypointService waypointService,
                          TaskAccessGuard accessGuard) {
        this.taskService = taskService;
        this.waypointService = waypointService;
        this.accessGuard = accessGuard;
    }

    /** 可见任务列表：admin 全部；普通用户 = 自己的 + 公开的 */
    @GetMapping
    public List<Task> list(HttpServletRequest request) {
        return taskService.listVisible(currentUserId(request), currentRole(request).equals("admin"));
    }

    @GetMapping("/{id}")
    public Task get(@PathVariable Long id, HttpServletRequest request) {
        return accessGuard.requireVisible(id, currentUserId(request), currentRole(request));
    }

    @PostMapping
    public Task create(@RequestBody Task task, HttpServletRequest request) {
        taskService.createTask(task, currentUserId(request));
        return task;
    }

    @PutMapping("/{id}")
    public Task update(@PathVariable Long id, @RequestBody Task task, HttpServletRequest request) {
        accessGuard.requireOwned(id, currentUserId(request), currentRole(request));
        task.setId(id);
        taskService.updateById(task);
        return task;
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, HttpServletRequest request) {
        accessGuard.requireOwned(id, currentUserId(request), currentRole(request));
        taskService.removeById(id);
    }

    /** 任务航点列表（按预设顺序） */
    @GetMapping("/{id}/waypoints")
    public List<Waypoint> waypoints(@PathVariable Long id, HttpServletRequest request) {
        accessGuard.requireVisible(id, currentUserId(request), currentRole(request));
        return waypointService.listByTask(id);
    }

    /** 新增单个真实巡检航点（前端地图选点），body: {name, longitude, latitude} */
    @PostMapping("/{id}/waypoints")
    public Waypoint addWaypoint(@PathVariable Long id, @RequestBody Waypoint body,
                                HttpServletRequest request) {
        accessGuard.requireOwned(id, currentUserId(request), currentRole(request));
        try {
            return waypointService.addWaypoint(id, body.getName(), body.getLongitude(), body.getLatitude());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** 删除单个航点（地图右键菜单），删除起点时自动移交起点 */
    @DeleteMapping("/{id}/waypoints/{waypointId}")
    public void deleteWaypoint(@PathVariable Long id, @PathVariable Long waypointId,
                               HttpServletRequest request) {
        accessGuard.requireOwned(id, currentUserId(request), currentRole(request));
        try {
            waypointService.removeWaypoint(id, waypointId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** 重命名单个航点（航点列表改名），body: {name} */
    @PutMapping("/{id}/waypoints/{waypointId}")
    public Waypoint renameWaypoint(@PathVariable Long id, @PathVariable Long waypointId,
                                   @RequestBody Waypoint body, HttpServletRequest request) {
        accessGuard.requireOwned(id, currentUserId(request), currentRole(request));
        try {
            return waypointService.renameWaypoint(id, waypointId, body.getName());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** 为任务生成 count 个模拟航点（默认 8 个）：以 centerLng/centerLat 为起飞点中心，第一个航点精确落在中心并作为起点 */
    @PostMapping("/{id}/waypoints/generate")
    public List<Waypoint> generateWaypoints(@PathVariable Long id,
                                            @RequestParam(defaultValue = "8") int count,
                                            @RequestParam BigDecimal centerLng,
                                            @RequestParam BigDecimal centerLat,
                                            HttpServletRequest request) {
        accessGuard.requireOwned(id, currentUserId(request), currentRole(request));
        try {
            return waypointService.generateForTask(id, count, centerLng, centerLat);
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
