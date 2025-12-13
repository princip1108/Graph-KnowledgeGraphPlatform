package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.UserRegistrationDto;
import com.sdu.kgplatform.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {
    private final UserService userService;
    
    public AuthController(UserService userService){
        this.userService = userService;
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
     * 处理注册请求 - 注册成功后自动登录并跳转首页
     */
    @PostMapping("/register")
    public String registerUser(UserRegistrationDto registrationDto, 
                               HttpServletRequest request,
                               RedirectAttributes redirectAttributes){
        try {
            // 注册用户
            userService.register(registrationDto);
            
            // 获取登录账号（优先邮箱，其次手机号）
            String account = registrationDto.getEmail() != null && !registrationDto.getEmail().isEmpty() 
                    ? registrationDto.getEmail() 
                    : registrationDto.getPhone();
            
            // 自动登录：加载用户信息并设置认证
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
            
            // 跳转首页
            return "redirect:/graph/home.html";
            
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            redirectAttributes.addFlashAttribute("registrationDto", registrationDto); 
            return "redirect:/login"; 
            
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "注册失败，请稍后再试。");
            return "redirect:/login"; 
        }
    }
    
    // Spring Security 会自动处理登录表单提交到 /perform_login
}

