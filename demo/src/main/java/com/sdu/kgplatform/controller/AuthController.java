package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.UserRegistrationDto;
import com.sdu.kgplatform.service.EmailVerificationService;
import com.sdu.kgplatform.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
public class AuthController {
    private final UserService userService;
    private final EmailVerificationService emailVerificationService;
    
    public AuthController(UserService userService, EmailVerificationService emailVerificationService){
        this.userService = userService;
        this.emailVerificationService = emailVerificationService;
    }
    /**
     * 响应get显示登录注册页
     * 路径 SecurityConfig中
     */
    @GetMapping("/login")
    public String showLoginPage(Model model){
        //提供空dto对象绑定数据
        model.addAttribute("registrationDto",new UserRegistrationDto());
        return "user/login_register";
    }

    /**
     * 处理注册请求 - 返回 JSON，注册成功后自动登录
     */
    @PostMapping("/register")
    @ResponseBody
    public ResponseEntity<?> registerUser(@RequestParam String userName,
                                          @RequestParam String email,
                                          @RequestParam String verificationCode,
                                          @RequestParam(required = false) String phone,
                                          @RequestParam String password,
                                          HttpServletRequest request) {
        try {
            // 校验邮箱验证码
            if (email == null || email.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "邮箱为必填项"));
            }
            if (verificationCode == null || verificationCode.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "请输入邮箱验证码"));
            }
            if (!emailVerificationService.verifyCode(email.trim(), verificationCode.trim())) {
                return ResponseEntity.badRequest().body(Map.of("error", "验证码错误或已过期"));
            }

            // 构建注册 DTO
            UserRegistrationDto registrationDto = new UserRegistrationDto();
            registrationDto.setUserName(userName);
            registrationDto.setEmail(email.trim());
            registrationDto.setPhone(phone != null && !phone.trim().isEmpty() ? phone.trim() : null);
            registrationDto.setPassword(password);

            // 注册用户
            userService.register(registrationDto);
            
            // 自动登录：加载用户信息并设置认证
            String account = email.trim();
            UserDetails userDetails = userService.loadUserByUsername(account);
            UsernamePasswordAuthenticationToken authToken = 
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
            
            SecurityContextHolder.getContext().setAuthentication(authToken);
            
            // 保存到 Session
            request.getSession().setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                SecurityContextHolder.getContext()
            );
            
            // 更新最后登录时间
            userService.updateLastLoginTime(account);
            
            return ResponseEntity.ok(Map.of("success", true, "redirect", "/graph/home.html"));
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "注册失败：" + e.getMessage()));
        }
    }
    
    /**
     * 发送邮箱验证码 (REST API)
     */
    @PostMapping("/api/auth/send-code")
    @ResponseBody
    public ResponseEntity<?> sendVerificationCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        if (email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请输入邮箱地址"));
        }
        try {
            String purpose = body.getOrDefault("purpose", "register");
            emailVerificationService.sendCode(email.trim(), purpose);
            return ResponseEntity.ok(Map.of("message", "验证码已发送"));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "发送失败，请稍后重试"));
        }
    }

    /**
     * 找回密码 - 校验验证码
     */
    @PostMapping("/api/auth/verify-reset-code")
    @ResponseBody
    public ResponseEntity<?> verifyResetCode(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String code = body.get("code");
        if (email == null || email.trim().isEmpty() || code == null || code.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "请输入邮箱和验证码"));
        }
        // 检查邮箱是否已注册
        if (!userService.existsByEmail(email.trim())) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "该邮箱未注册"));
        }
        boolean valid = emailVerificationService.verifyCode(email.trim(), code.trim());
        if (valid) {
            return ResponseEntity.ok(Map.of("success", true));
        } else {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "验证码错误或已过期"));
        }
    }

    /**
     * 找回密码 - 重置密码
     */
    @PostMapping("/api/auth/reset-password")
    @ResponseBody
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> body) {
        String email = body.get("email");
        String newPassword = body.get("newPassword");

        if (email == null || newPassword == null
                || email.trim().isEmpty() || newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "参数不完整"));
        }
        if (newPassword.length() < 8 || newPassword.length() > 20) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "密码长度应为8-20位"));
        }

        try {
            userService.resetPassword(email.trim(), newPassword);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }
}

