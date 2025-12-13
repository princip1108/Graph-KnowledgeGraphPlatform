package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.UserProfileDto;
import com.sdu.kgplatform.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 个人中心页面
     */
    @GetMapping("/profile")
    public String profilePage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            UserProfileDto profile = userService.getUserProfile(auth.getName());
            model.addAttribute("user", profile);
        }
        return "user/profile";
    }

    /**
     * 获取当前登录用户资料 (REST API)
     */
    @GetMapping("/api/profile")
    @ResponseBody
    public ResponseEntity<?> getCurrentUserProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        System.out.println("=== 获取用户资料 ===");
        System.out.println("Auth: " + auth);
        System.out.println("Auth Name: " + (auth != null ? auth.getName() : "null"));
        
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        
        try {
            UserProfileDto profile = userService.getUserProfile(auth.getName());
            System.out.println("获取到用户: " + profile.getUserName());
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            System.out.println("获取用户失败: " + e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "获取用户信息失败"));
        }
    }

    /**
     * 更新当前用户资料 (REST API)
     */
    @PutMapping("/api/profile")
    @ResponseBody
    public ResponseEntity<?> updateCurrentUserProfile(@RequestBody UserProfileDto profileDto) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        
        try {
            UserProfileDto updatedProfile = userService.updateUserProfile(auth.getName(), profileDto);
            return ResponseEntity.ok(updatedProfile);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "更新用户信息失败: " + e.getMessage()));
        }
    }

    /**
     * 检查登录状态 (REST API)
     */
    @GetMapping("/api/check-auth")
    @ResponseBody
    public ResponseEntity<?> checkAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> result = new HashMap<>();
        
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            result.put("authenticated", true);
            result.put("username", auth.getName());
            try {
                UserProfileDto profile = userService.getUserProfile(auth.getName());
                result.put("user", profile);
            } catch (Exception e) {
                // 用户信息获取失败，但仍然是已登录状态
            }
        } else {
            result.put("authenticated", false);
        }
        
        return ResponseEntity.ok(result);
    }
}
