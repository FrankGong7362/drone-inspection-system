package com.tongji.uav.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tongji.uav.dto.*;
import com.tongji.uav.entity.User;
import com.tongji.uav.mapper.UserMapper;
import com.tongji.uav.util.JwtUtil;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 登录/注册业务：BCrypt 密码校验 + JWT 签发 + 个人中心自助修改。
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

    /** 获取当前登录用户资料（不含 password）。 */
    public UserVO getProfile(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return toVO(user);
    }

    /** 自助修改昵称（昵称字段单独提交；改密码走 changePassword）。 */
    public UserVO updateNickname(Long userId, String nickname) {
        if (nickname == null || nickname.isBlank()) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        if (nickname.length() > 30) {
            throw new IllegalArgumentException("昵称最长 30 个字符");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        user.setNickname(nickname.trim());
        userMapper.updateById(user);
        return toVO(user);
    }

    /** 自助修改密码：BCrypt 校验旧密码 → 新密码 BCrypt 加密 → 入库；新旧不能相同。 */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (oldPassword == null || newPassword == null) {
            throw new IllegalArgumentException("旧密码和新密码都不能为空");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!encoder.matches(oldPassword, user.getPassword())) {
            throw new IllegalArgumentException("旧密码不正确");
        }
        if (oldPassword.equals(newPassword)) {
            throw new IllegalArgumentException("新密码不能与旧密码相同");
        }
        if (newPassword.length() < 8 || newPassword.length() > 30) {
            throw new IllegalArgumentException("新密码长度需在 8 到 30 位之间");
        }
        user.setPassword(encoder.encode(newPassword));
        userMapper.updateById(user);
        // 密码修改后原 token 仍有效（无状态），但前端收到成功应自动退出重新登录
    }

    private UserVO toVO(User user) {
        UserVO vo = new UserVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setCreateTime(user.getCreateTime());
        return vo;
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
