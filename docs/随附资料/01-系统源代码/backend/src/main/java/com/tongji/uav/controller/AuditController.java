package com.tongji.uav.controller;

import com.tongji.uav.dto.AuditPageVO;
import com.tongji.uav.service.AuditService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作审计日志接口（仅 admin，鉴权在 AuthInterceptor 完成）。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** 分页查询执行留痕：可按任务、动作、关键字过滤 */
    @GetMapping("/logs")
    public AuditPageVO logs(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int size,
                            @RequestParam(required = false) Long taskId,
                            @RequestParam(required = false) String action,
                            @RequestParam(required = false) String keyword) {
        return auditService.page(page, size, taskId, action, keyword);
    }
}
