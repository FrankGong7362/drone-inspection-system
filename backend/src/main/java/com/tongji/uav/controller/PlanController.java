package com.tongji.uav.controller;

import com.tongji.uav.dto.PlanCommand;
import com.tongji.uav.dto.PlanResultVO;
import com.tongji.uav.service.PlanService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

/**
 * 路径规划接口：接收前端请求 → 调 Python 算法服务 → 结果落库并返回。
 */
@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping
    public PlanResultVO plan(@RequestBody PlanCommand command) {
        try {
            return planService.plan(command);
        } catch (HttpClientErrorException e) {
            // 算法服务返回 4xx（参数非法 / 算法名不受支持等）
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "算法服务拒绝请求: " + e.getResponseBodyAsString(), e);
        } catch (ResourceAccessException e) {
            // 算法服务不可达（未启动 / 超时）
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "算法服务不可用，请确认 FastAPI 服务已启动(:8000)", e);
        } catch (RestClientException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "调用算法服务失败: " + e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }
}
