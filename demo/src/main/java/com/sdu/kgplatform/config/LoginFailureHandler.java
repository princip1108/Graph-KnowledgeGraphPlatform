package com.sdu.kgplatform.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 自定义登录失败处理器 - 返回 JSON 格式错误信息
 */
@Component
public class LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        
        String errorMessage;
        if (exception instanceof UsernameNotFoundException) {
            errorMessage = "账号不存在";
        } else if (exception instanceof BadCredentialsException) {
            errorMessage = "邮箱/手机号或密码错误";
        } else {
            errorMessage = "登录失败：" + exception.getMessage();
        }
        
        response.getWriter().write("{\"error\": \"" + errorMessage + "\"}");
    }
}
