package com.tongji.uav.dto;

import lombok.Data;

/**
 * 登录/注册响应：JWT 令牌 + 用户信息。
 * 面向前端，字段 camelCase（与 PlanResultVO 等前端响应 DTO 一致）。
 */
@Data
public class LoginResponse {

    private String token;

    private Long userId;

    private String username;

    private String nickname;

    /** admin / user */
    private String role;
}
