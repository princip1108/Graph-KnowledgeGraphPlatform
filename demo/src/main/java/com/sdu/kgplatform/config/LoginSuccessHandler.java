package com.sdu.kgplatform.config;

import com.sdu.kgplatform.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 登录成功处理器 - 返回 JSON 响应并更新最后登录时间
 */
@Component
public class LoginSuccessHandler implements AuthenticationSuccessHandler {

    private static final Logger logger = LoggerFactory.getLogger(LoginSuccessHandler.class);

    private final UserService userService;

    public LoginSuccessHandler(UserService userService) {
        this.userService = userService;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, 
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        logger.info("[登录成功] 用户: {}, Principal类型: {}", authentication.getName(), 
                    authentication.getPrincipal().getClass().getSimpleName());

        // 显式保存 SecurityContext 到 HttpSession
        SecurityContext context = SecurityContextHolder.getContext();
        HttpSessionSecurityContextRepository repo = new HttpSessionSecurityContextRepository();
        repo.saveContext(context, request, response);

        // 显式写入 JSESSIONID cookie，确保浏览器获得正确的 session ID
        jakarta.servlet.http.HttpSession session = request.getSession(false);
        if (session != null) {
            jakarta.servlet.http.Cookie sessionCookie = new jakarta.servlet.http.Cookie("JSESSIONID", session.getId());
            sessionCookie.setPath("/");
            sessionCookie.setHttpOnly(true);
            response.addCookie(sessionCookie);
            logger.info("[登录成功] Session ID: {}, 已显式写入 cookie", session.getId());
        } else {
            logger.error("[登录成功] Session 为 null！认证状态无法持久化！");
        }

        // 更新最后登录时间
        try {
            userService.updateLastLoginTime(authentication.getName());
        } catch (Exception e) {
            logger.warn("更新最后登录时间失败: " + e.getMessage());
        }

        // 返回 JSON 响应
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\": true, \"redirect\": \"/graph/home.html\"}");
    }
}
