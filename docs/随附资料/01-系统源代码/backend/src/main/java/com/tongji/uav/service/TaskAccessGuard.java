package com.tongji.uav.service;

import com.tongji.uav.entity.Task;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * 任务归属与可见性守卫：供 TaskController / PlanController 复用。
 * 权限模型：admin 全部可见可改；普通用户对自己的任务全部可改，对公开任务可查看与规划，私有任务不可见。
 */
@Component
public class TaskAccessGuard {

    private final TaskService taskService;

    public TaskAccessGuard(TaskService taskService) {
        this.taskService = taskService;
    }

    /** 写操作（改任务/航点/删除）：owner 或 admin，否则 403；任务不存在 404。 */
    public Task requireOwned(Long taskId, Long userId, String role) {
        Task task = requireTask(taskId);
        if (!isAdmin(role) && !Objects.equals(task.getCreatedBy(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅任务所有者或管理员可操作");
        }
        return task;
    }

    /** 读/规划：owner、admin 或公开任务，否则 403；任务不存在 404。 */
    public Task requireVisible(Long taskId, Long userId, String role) {
        Task task = requireTask(taskId);
        boolean allowed = isAdmin(role)
                || Boolean.TRUE.equals(task.getVisible())
                || Objects.equals(task.getCreatedBy(), userId);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "该任务对您不可见");
        }
        return task;
    }

    private Task requireTask(Long taskId) {
        Task task = taskService.getById(taskId);
        if (task == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        }
        return task;
    }

    private boolean isAdmin(String role) {
        return "admin".equals(role);
    }
}
