package com.tongji.uav.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tongji.uav.dto.LoginRequest;
import com.tongji.uav.dto.LoginResponse;
import com.tongji.uav.dto.RegisterRequest;
import com.tongji.uav.entity.User;
import com.tongji.uav.mapper.UserMapper;
import com.tongji.uav.util.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录/注册业务：BCrypt 密码校验 + JWT 签发。
 */
@Service
public class AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.jwtUtil = jwtUtil;
    }

    /** 注册：查重 → BCrypt 加密 → 入库，注册成功直接返回令牌（自动登录）。 */
    public LoginResponse register(RegisterRequest req) {
        Long count = userMapper.selectCount(new QueryWrapper<User>().eq("username", req.getUsername()));
        if (count != null && count > 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(encoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() == null || req.getNickname().isBlank()
                ? req.getUsername() : req.getNickname());
        user.setRole("user");
        userMapper.insert(user);
        return buildResponse(user);
    }

    /** 登录：按用户名查用户 → BCrypt matches → 签发令牌。 */
    public LoginResponse login(LoginRequest req) {
        User user = userMapper.selectOne(new QueryWrapper<User>().eq("username", req.getUsername()));
        if (user == null || !encoder.matches(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        return buildResponse(user);
    }

    private LoginResponse buildResponse(User user) {
        LoginResponse resp = new LoginResponse();
        resp.setToken(jwtUtil.generate(user.getId(), user.getUsername(), user.getRole()));
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setRole(user.getRole());
        return resp;
    }
}
