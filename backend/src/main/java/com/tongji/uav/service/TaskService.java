package com.tongji.uav.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.tongji.uav.entity.Task;
import com.tongji.uav.mapper.TaskMapper;
import org.springframework.stereotype.Service;

/**
 * 巡检任务业务逻辑。
 */
@Service
public class TaskService extends ServiceImpl<TaskMapper, Task> {

    /**
     * 创建任务：自动生成任务编号、默认状态"待执行"。
     */
    public void createTask(Task task) {
        if (task.getTaskNo() == null || task.getTaskNo().isBlank()) {
            task.setTaskNo("T" + System.currentTimeMillis());
        }
        if (task.getStatus() == null || task.getStatus().isBlank()) {
            task.setStatus("待执行");
        }
        save(task);
    }
}
