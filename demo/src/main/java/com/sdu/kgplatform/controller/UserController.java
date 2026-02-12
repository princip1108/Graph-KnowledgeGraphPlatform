package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.UserProfileDto;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.security.CustomOAuth2User;
import com.sdu.kgplatform.security.CustomUserDetails;
import com.sdu.kgplatform.service.AdminService;
import com.sdu.kgplatform.service.EmailVerificationService;
import com.sdu.kgplatform.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/user")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService userService;
    private final AdminService adminService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;

    public UserController(UserService userService,
                          AdminService adminService,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          EmailVerificationService emailVerificationService) {
        this.userService = userService;
        this.adminService = adminService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
    }

    /**
     * 从 Authentication 中提取用户资料（兼容表单登录和 OAuth2 登录）
     */
    private UserProfileDto resolveUserProfile(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomOAuth2User oAuth2User) {
            return userService.getUserProfileById(oAuth2User.getUserId(), false);
        } else if (principal instanceof CustomUserDetails userDetails) {
            return userService.getUserProfileById(userDetails.getUserId(), false);
        }
        // fallback: 按账号查找
        return userService.getUserProfile(auth.getName());
    }

    /**
     * 个人中心页面
     */
    @GetMapping("/profile")
    public String profilePage(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            UserProfileDto profile = resolveUserProfile(auth);
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
        log.debug("=== 获取用户资料 ===");
        log.debug("Auth: {}", auth);
        log.debug("Auth Name: {}", auth != null ? auth.getName() : "null");

        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            UserProfileDto profile = resolveUserProfile(auth);
            log.debug("获取到用户: {}", profile.getUserName());
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            log.error("获取用户失败: {}", e.getMessage());
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
            UserProfileDto current = resolveUserProfile(auth);
            UserProfileDto updatedProfile = userService.updateUserProfileById(current.getUserId(), profileDto);
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
                UserProfileDto profile = resolveUserProfile(auth);
                result.put("user", profile);
                if (profile != null && profile.getRole() != null) {
                    result.put("role", profile.getRole());
                }
            } catch (Exception e) {
                // 用户信息获取失败，但仍然是已登录状态
            }
        } else {
            result.put("authenticated", false);
        }

        return ResponseEntity.ok(result);
    }

    /**
     * 获取指定用户资料 (Public API)
     */
    @GetMapping("/api/users/{id}")
    @ResponseBody
    public ResponseEntity<?> getUserProfileById(@PathVariable Integer id) {
        try {
            UserProfileDto profile = userService.getUserProfileById(id);
            if (profile == null) {
                return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
            }
            // 已注销用户返回特殊响应
            if ("已注销".equals(profile.getStatus())) {
                Map<String, Object> deletedUser = new HashMap<>();
                deletedUser.put("userId", profile.getUserId());
                deletedUser.put("userName", "已注销用户");
                deletedUser.put("bio", "该用户已注销账号");
                deletedUser.put("deleted", true);
                return ResponseEntity.ok(deletedUser);
            }
            // 脱敏处理，只返回必要信息
            profile.setEmail(null);
            profile.setPhone(null);
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "获取用户信息失败"));
        }
    }

    /**
     * 用户自助注销账号
     */
    @PostMapping("/api/deactivate")
    @ResponseBody
    public ResponseEntity<?> deactivateAccount(@RequestBody Map<String, String> request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }

        String confirmPassword = request.get("password");
        if (confirmPassword == null || confirmPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入密码确认注销"));
        }

        try {
            UserProfileDto profile = resolveUserProfile(auth);
            User user = userRepository.findById(profile.getUserId()).orElse(null);
            if (user == null) {
                return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
            }

            // 验证密码（OAuth 用户跳过密码验证，但需要传 "CONFIRM" 作为确认）
            if (user.getOauthProvider() != null && !user.getOauthProvider().isEmpty()) {
                if (!"CONFIRM".equals(confirmPassword)) {
                    return ResponseEntity.badRequest().body(Map.of("error", "请输入 CONFIRM 确认注销"));
                }
            } else {
                if (!passwordEncoder.matches(confirmPassword, user.getPasswordHash())) {
                    return ResponseEntity.badRequest().body(Map.of("error", "密码错误"));
                }
            }

            adminService.deleteUser(user.getUserId());
            SecurityContextHolder.clearContext();

            return ResponseEntity.ok(Map.of("success", true, "message", "账号已注销"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "注销失败: " + e.getMessage()));
        }
    }

    /**
     * 修改密码（需验证旧密码 + 邮箱验证码）
     */
    @PostMapping("/api/change-password")
    @ResponseBody
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(401).body(Map.of("error", "请先登录"));
        }

        String oldPassword = request.get("oldPassword");
        String newPassword = request.get("newPassword");
        String verificationCode = request.get("verificationCode");
        String email = request.get("email");

        if (oldPassword == null || oldPassword.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入当前密码"));
        }
        if (newPassword == null || newPassword.length() < 8 || newPassword.length() > 20) {
            return ResponseEntity.badRequest().body(Map.of("error", "新密码长度应为8-20位"));
        }
        if (verificationCode == null || verificationCode.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入邮箱验证码"));
        }
        if (email == null || email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "邮箱不能为空"));
        }
        if (!emailVerificationService.verifyCode(email.trim(), verificationCode.trim())) {
            return ResponseEntity.badRequest().body(Map.of("error", "验证码错误或已过期"));
        }

        try {
            UserProfileDto profile = resolveUserProfile(auth);
            // 用 userId 查找用户，避免 auth.getName() 对 OAuth2 用户返回无效值
            User user = userRepository.findById(profile.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
            String account = user.getEmail() != null ? user.getEmail()
                    : (user.getPhone() != null ? user.getPhone() : user.getUserName());
            userService.changePassword(account, oldPassword, newPassword);
            return ResponseEntity.ok(Map.of("success", true, "message", "密码修改成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "修改失败: " + e.getMessage()));
        }
    }
}
