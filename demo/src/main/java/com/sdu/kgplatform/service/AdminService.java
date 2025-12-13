package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.Role;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.entity.UserStatus;
import com.sdu.kgplatform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员服务 - 处理管理员相关业务逻辑
 */
@Service
public class AdminService {

    private final UserRepository userRepository;

    public AdminService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 获取所有用户
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * 获取用户统计信息
     */
    public Map<String, Object> getUserStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalUsers = userRepository.count();
        long adminCount = userRepository.countByRole(Role.ADMIN);
        long userCount = userRepository.countByRole(Role.USER);
        long visitorCount = userRepository.countByRole(Role.VISITOR);
        
        stats.put("totalUsers", totalUsers);
        stats.put("adminCount", adminCount);
        stats.put("userCount", userCount);
        stats.put("visitorCount", visitorCount);
        
        return stats;
    }

    /**
     * 更新用户角色
     */
    @Transactional
    public void updateUserRole(Integer userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        user.setRole(role);
        userRepository.save(user);
    }

    /**
     * 更新用户状态
     */
    @Transactional
    public void updateUserStatus(Integer userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        try {
            UserStatus userStatus = UserStatus.valueOf(status.toUpperCase());
            user.setUserStatus(userStatus);
            userRepository.save(user);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的状态: " + status);
        }
    }

    /**
     * 删除用户
     */
    @Transactional
    public void deleteUser(Integer userId) {
        if (!userRepository.existsById(userId)) {
            throw new IllegalArgumentException("用户不存在: " + userId);
        }
        userRepository.deleteById(userId);
    }
}
