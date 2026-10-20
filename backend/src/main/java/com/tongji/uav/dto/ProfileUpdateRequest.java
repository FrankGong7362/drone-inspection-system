package com.tongji.uav.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 个人资料修改请求：仅 nickname 与 password 可改。
 * 同时承载"改昵称"（仅 nickname）与"改密码"（oldPassword + newPassword）两种场景。
 */
@Data
public class ProfileUpdateRequest {

    /** 新昵称（可选，传了就更新） */
    @Size(max = 30, message = "昵称最长 30 个字符")
    private String nickname;

    /** 旧密码（修改密码时必填，BCrypt 校验） */
    private String oldPassword;

    /** 新密码（修改密码时必填，8-30 位、至少含字母与数字） */
    @Size(min = 8, max = 30, message = "新密码长度需在 8 到 30 位之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新密码需同时包含字母和数字")
    private String newPassword;
}
