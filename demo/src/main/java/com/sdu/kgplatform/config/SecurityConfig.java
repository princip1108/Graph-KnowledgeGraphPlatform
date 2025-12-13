package com.sdu.kgplatform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)  // 启用方法级别权限注解
public class SecurityConfig {

    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;

    public SecurityConfig(@Lazy LoginSuccessHandler loginSuccessHandler,
                          @Lazy LoginFailureHandler loginFailureHandler) {
        this.loginSuccessHandler = loginSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
    }

    /**
     * 密码编码器 - 使用 BCrypt 加密算法
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全过滤链配置
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // 禁用 CSRF（前后端分离项目通常禁用）
            .csrf(csrf -> csrf.disable())
            // 允许 iframe 嵌入（页面使用 srcdoc iframe 结构）
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
            )
            // 配置请求授权规则
            .authorizeHttpRequests(auth -> auth
                // 公开访问的路径
                .requestMatchers(
                    "/",
                    "/login",                 // 登录页
                    "/register",              // 注册接口
                    "/api/auth/**",           // 认证相关接口
                    "/user/api/check-auth",   // 检查登录状态
                    "/api/public/**",         // 公开接口
                    "/assets/**",             // 静态资源
                    "/uploads/**",            // 上传文件目录
                    "/templates/**",          // 模板文件
                    "/*.html",                // HTML页面
                    "/graph/**",              // 图谱相关页面
                    "/user/**",               // 用户相关页面
                    "/community/**",          // 社区相关页面
                    "/pages/**",              // 其他页面
                    "/admin/**"               // 管理页面（后续可改为需要认证）
                ).permitAll()
                // 管理员专属接口
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/graph/admin/**").hasRole("ADMIN")
                // 公开的图谱接口（无需登录）
                .requestMatchers("/api/graph/public", "/api/graph/search", "/api/graph/popular").permitAll()
                .requestMatchers("/api/graph/share/**").permitAll()
                .requestMatchers("/api/graph/user/**").permitAll()
                // 用户和管理员可访问（需要登录）
                .requestMatchers("/api/graph/my", "/api/graph/my/**").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/user/api/profile").hasAnyRole("USER", "ADMIN")
                .requestMatchers("/api/upload/**").hasAnyRole("USER", "ADMIN")
                // 其他请求需要认证
                .anyRequest().authenticated()
            )
            // 配置表单登录
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/api/auth/login")
                .successHandler(loginSuccessHandler)
                .failureHandler(loginFailureHandler)
                .permitAll()
            )
            // 配置登出
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessUrl("/graph/home.html")
                .permitAll()
            );

        return http.build();
    }
}
