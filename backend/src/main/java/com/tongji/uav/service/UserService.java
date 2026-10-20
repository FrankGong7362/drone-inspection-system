package com.tongji.uav.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.tongji.uav.dto.UserVO;
import com.tongji.uav.entity.User;
import com.tongji.uav.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户管理业务（仅 admin 调用，鉴权由 AuthInterceptor 完成）：
 * 用户列表 / 修改角色 / 删除用户，含自我操作与最后一个 admin 保护。
 */
@Service
public class UserService {

    private final UserMapper userMapper;

    public UserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** 用户列表（按 id 升序），不含密码。 */
    public List<UserVO> list() {
        return userMapper.selectList(new QueryWrapper<User>().orderByAsc("id")).stream()
                .map(this::toVO)
                .toList();
    }

    /** 修改角色：目标非自己；禁止降级/变更最后一个 admin 的角色。 */
    public void updateRole(Long id, String newRole, Long operatorId) {
        if (!"admin".equals(newRole) && !"user".equals(newRole)) {
            throw new IllegalArgumentException("非法角色：" + newRole);
        }
        if (id.equals(operatorId)) {
            throw new IllegalArgumentException("不能修改自己的角色");
        }
        User target = requireUser(id);
        if ("admin".equals(target.getRole()) && countAdmins() <= 1) {
            throw new IllegalArgumentException("系统至少需要保留一名管理员");
        }
        target.setRole(newRole);
        userMapper.updateById(target);
    }

    /** 删除用户：目标非自己；禁止删除最后一个 admin。 */
    public void delete(Long id, Long operatorId) {
        if (id.equals(operatorId)) {
            throw new IllegalArgumentException("不能删除自己的账号");
        }
        User target = requireUser(id);
        if ("admin".equals(target.getRole()) && countAdmins() <= 1) {
            throw new IllegalArgumentException("系统至少需要保留一名管理员");
        }
        userMapper.deleteById(id);
    }

    private User requireUser(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private long countAdmins() {
        Long count = userMapper.selectCount(new QueryWrapper<User>().eq("role", "admin"));
        return count == null ? 0 : count;
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
}
