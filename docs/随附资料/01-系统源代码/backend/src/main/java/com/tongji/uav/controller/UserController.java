package com.tongji.uav.controller;

import com.tongji.uav.dto.UserVO;
import com.tongji.uav.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * 用户管理接口（仅 admin，鉴权在 AuthInterceptor 完成）。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserVO> list() {
        return userService.list();
    }

    /** 修改角色，body: {role: admin|user} */
    @PutMapping("/{id}/role")
    public void updateRole(@PathVariable Long id, @RequestBody Map<String, String> body,
                           HttpServletRequest request) {
        try {
            userService.updateRole(id, body.get("role"), currentUserId(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id, HttpServletRequest request) {
        try {
            userService.delete(id, currentUserId(request));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        }
    }

    private Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute("userId");
    }
}
