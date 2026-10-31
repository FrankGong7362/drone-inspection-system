package com.tongji.uav.controller;

import com.tongji.uav.dto.PlanDetailVO;
import com.tongji.uav.dto.PlanHistoryVO;
import com.tongji.uav.entity.Task;
import com.tongji.uav.service.PlanHistoryService;
import com.tongji.uav.service.TaskAccessGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * 规划历史与导出接口（挂在 /api/tasks/{id}/plans 下）：
 * 读权限与任务可见性一致（owner / admin / 公开任务可查看）。
 */
@RestController
@RequestMapping("/api/tasks/{id}/plans")
public class PlanHistoryController {

    private final PlanHistoryService planHistoryService;
    private final TaskAccessGuard accessGuard;

    public PlanHistoryController(PlanHistoryService planHistoryService, TaskAccessGuard accessGuard) {
        this.planHistoryService = planHistoryService;
        this.accessGuard = accessGuard;
    }

    /** 历次规划记录（最新在前，不含 route/detailPaths 大字段） */
    @GetMapping
    public List<PlanHistoryVO> list(@PathVariable Long id, HttpServletRequest request) {
        accessGuard.requireVisible(id, currentUserId(request), currentRole(request));
        return planHistoryService.list(id);
    }

    /** 单次规划详情，用于历史回看并在前端重绘 */
    @GetMapping("/{resultId}")
    public PlanDetailVO detail(@PathVariable Long id, @PathVariable Long resultId,
                               HttpServletRequest request) {
        accessGuard.requireVisible(id, currentUserId(request), currentRole(request));
        return planHistoryService.detail(id, resultId);
    }

    /** 导出航线：format=kml（默认）/ csv */
    @GetMapping("/{resultId}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id, @PathVariable Long resultId,
                                         @RequestParam(defaultValue = "kml") String format,
                                         HttpServletRequest request) {
        Task task = accessGuard.requireVisible(id, currentUserId(request), currentRole(request));
        try {
            PlanHistoryService.ExportFile file = planHistoryService.export(task, resultId, format);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                    .contentType(MediaType.parseMediaType(file.contentType()))
                    .body(file.content());
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
