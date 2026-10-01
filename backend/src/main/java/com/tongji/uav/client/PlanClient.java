package com.tongji.uav.client;

import com.tongji.uav.dto.PlanRequest;
import com.tongji.uav.dto.PlanResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 算法服务客户端：调用 Python FastAPI 的 POST /api/plan。
 */
@Component
public class PlanClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PlanClient(RestTemplate restTemplate,
                      @Value("${app.algorithm-service.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public PlanResponse plan(PlanRequest request) {
        String url = baseUrl + "/api/plan";
        ResponseEntity<PlanResponse> resp = restTemplate.postForEntity(url, request, PlanResponse.class);
        return resp.getBody();
    }
}
