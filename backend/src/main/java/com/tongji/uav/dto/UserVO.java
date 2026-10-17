package com.tongji.uav.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户信息视图对象：用户管理接口返回，隔离 User 实体的密码字段。
 * 面向前端，字段 camelCase（与 LoginResponse 一致）。
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    /** admin / user */
    private String role;

    private LocalDateTime createTime;
}
