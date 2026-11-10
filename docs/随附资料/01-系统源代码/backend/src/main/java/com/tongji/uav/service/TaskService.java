package com.tongji.uav.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tongji.uav.entity.Task;
import com.tongji.uav.mapper.TaskMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 巡检任务业务逻辑。
 */
@Service
public class TaskService extends ServiceImpl<TaskMapper, Task> {

    /**
     * 创建任务：自动生成任务编号、默认状态"待执行"，记录创建者，默认对其他用户可见。
     */
    public void createTask(Task task, Long userId) {
        if (task.getTaskNo() == null || task.getTaskNo().isBlank()) {
            task.setTaskNo("T" + System.currentTimeMillis());
        }
        if (task.getStatus() == null || task.getStatus().isBlank()) {
            task.setStatus("待执行");
        }
        task.setCreatedBy(userId);
        if (task.getVisible() == null) {
            task.setVisible(true);
        }
        save(task);
    }

    /**
     * 可见任务列表：admin 返回全部；普通用户返回"自己的 + 公开的"。
     */
    public List<Task> listVisible(Long userId, boolean admin) {
        if (admin) {
            return list();
        }
        return list(new QueryWrapper<Task>().eq("visible", true).or().eq("created_by", userId));
    }
}
