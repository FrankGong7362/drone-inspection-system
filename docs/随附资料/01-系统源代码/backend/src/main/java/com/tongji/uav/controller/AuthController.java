package com.tongji.uav.controller;

import com.tongji.uav.dto.LoginRequest;
import com.tongji.uav.dto.LoginResponse;
import com.tongji.uav.dto.ProfileUpdateRequest;
import com.tongji.uav.dto.RegisterRequest;
import com.tongji.uav.dto.UserVO;
import com.tongji.uav.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 登录认证接口：注册 / 登录 / 当前用户资料查询与自助修改。
 * 登录与注册路径由 AuthInterceptor 白名单放行；
 * /api/auth/me 由拦截器放行但仍需 token（所有登录用户可自助）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public LoginResponse register(@Valid @RequestBody RegisterRequest req) {
        try {
            return authService.register(req);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        try {
            return authService.login(req);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage(), e);
        }
    }

    /** 当前用户资料查询（登录后可用，返回 UserVO 不含 password） */
    @GetMapping("/me")
    public UserVO me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute("userId");
        return authService.getProfile(userId);
    }

    /** 自助修改昵称（仅 nickname 字段） */
    @PutMapping("/me")
    public UserVO updateNickname(@RequestBody ProfileUpdateRequest req, HttpServletRequest request) {
        if (req.getNickname() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "nickname 必填");
        }
        try {
            return authService.updateNickname((Long) request.getAttribute("userId"), req.getNickname());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    /** 自助修改密码（oldPassword + newPassword）。成功返回 200，前端应强制退出重新登录。 */
    @PostMapping("/me/password")
    public void changePassword(@RequestBody ProfileUpdateRequest req, HttpServletRequest request) {
        if (req.getOldPassword() == null || req.getNewPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "旧密码和新密码都不能为空");
        }
        try {
            authService.changePassword((Long) request.getAttribute("userId"),
                    req.getOldPassword(), req.getNewPassword());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }
}
