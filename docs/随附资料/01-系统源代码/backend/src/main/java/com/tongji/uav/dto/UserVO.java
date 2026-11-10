package com.tongji.uav.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 当前用户资料响应：不含 password。字段 camelCase 与前端契约一致。
 */
@Data
public class UserVO {

    private Long userId;

    private String username;

    private String nickname;

    /** admin / user */
    private String role;

    private LocalDateTime createTime;
}
