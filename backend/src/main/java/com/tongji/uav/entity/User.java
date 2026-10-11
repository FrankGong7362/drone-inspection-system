package com.tongji.uav.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户实体，对应表 sys_user。用于登录认证。
 */
@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 密文，任何接口不得返回该字段 */
    private String password;

    private String nickname;

    /** admin / user（本次仅存储，不做权限区分） */
    private String role;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
