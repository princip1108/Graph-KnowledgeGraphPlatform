package com.sdu.kgplatform.config;

import com.sdu.kgplatform.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 登录成功处理器 - 更新最后登录时间
 */
@Component
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserService userService;

    public LoginSuccessHandler(UserService userService) {
        this.userService = userService;
        setDefaultTargetUrl("/graph/home.html");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        // 更新最后登录时间
        try {
            userService.updateLastLoginTime(authentication.getName());
        } catch (Exception e) {
            // 记录日志但不影响登录流程
            logger.warn("更新最后登录时间失败: " + e.getMessage());
        }
        
        super.onAuthenticationSuccess(request, response, authentication);
    }
}
