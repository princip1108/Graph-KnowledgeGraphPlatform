package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.entity.Role;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.service.AdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 管理员控制器 - 仅管理员可访问
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * 获取所有用户列表
     */
    @GetMapping("/users")
    public ResponseEntity<List<User>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    /**
     * 获取用户统计信息
     */
    @GetMapping("/stats/users")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        return ResponseEntity.ok(adminService.getUserStats());
    }

    /**
     * 修改用户角色
     */
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> request) {
        String roleName = request.get("role");
        try {
            Role role = Role.valueOf(roleName.toUpperCase());
            adminService.updateUserRole(userId, role);
            return ResponseEntity.ok(Map.of("success", true, "message", "角色更新成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的角色: " + roleName));
        }
    }

    /**
     * 禁用/启用用户
     */
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> request) {
        String status = request.get("status");
        adminService.updateUserStatus(userId, status);
        return ResponseEntity.ok(Map.of("success", true, "message", "状态更新成功"));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "用户已删除"));
    }
}
