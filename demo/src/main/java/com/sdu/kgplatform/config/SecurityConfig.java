package com.sdu.kgplatform.config;

import com.sdu.kgplatform.security.CustomOAuth2UserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import jakarta.servlet.http.HttpServletResponse;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true) // 启用方法级别权限注解
public class SecurityConfig {

        private final LoginSuccessHandler loginSuccessHandler;
        private final LoginFailureHandler loginFailureHandler;
        private final CustomOAuth2UserService customOAuth2UserService;
        private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;

        public SecurityConfig(@Lazy LoginSuccessHandler loginSuccessHandler,
                        @Lazy LoginFailureHandler loginFailureHandler,
                        @Lazy CustomOAuth2UserService customOAuth2UserService,
                        @Lazy OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler) {
                this.loginSuccessHandler = loginSuccessHandler;
                this.loginFailureHandler = loginFailureHandler;
                this.customOAuth2UserService = customOAuth2UserService;
                this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
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
                                // 显式配置 SecurityContext 使用 HttpSession 保存（Spring Security 7.x 默认不自动保存）
                                .securityContext(sc -> sc
                                                .securityContextRepository(new HttpSessionSecurityContextRepository()))
                                // 允许 iframe 嵌入（页面使用 srcdoc iframe 结构）
                                .headers(headers -> headers
                                                .frameOptions(frameOptions -> frameOptions.sameOrigin()))
                                // 配置请求授权规则
                                .authorizeHttpRequests(auth -> auth
                                                // 公开访问的路径
                                                .requestMatchers(
                                                                "/",
                                                                "/favicon.ico", // Favicon
                                                                "/login", // 登录页
                                                                "/register", // 注册接口
                                                                "/login/oauth2/**", // OAuth2 回调
                                                                "/oauth2/**", // OAuth2 授权
                                                                "/api/auth/**", // 认证相关接口
                                                                "/user/api/check-auth", // 检查登录状态
                                                                "/api/public/**", // 公开接口
                                                                "/assets/**", // 静态资源
                                                                "/css/**", // CSS 静态资源
                                                                "/js/**", // JS 静态资源
                                                                "/uploads/**", // 上传文件目录
                                                                "/templates/**", // 模板文件
                                                                "/*.html", // HTML页面
                                                                "/graph/**", // 图谱相关页面
                                                                "/user/**", // 用户相关页面
                                                                "/community/**", // 社区相关页面
                                                                "/pages/**", // 其他页面
                                                                "/admin/**" // 管理页面（后续可改为需要认证）
                                                ).permitAll()
                                                // 管理员专属接口
                                                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                                                .requestMatchers("/api/graph/admin/**").hasRole("ADMIN")
                                                // 游客可浏览的只读 GET 接口
                                                .requestMatchers(HttpMethod.GET, "/api/categories").permitAll()
                                                .requestMatchers(HttpMethod.GET, "/api/category/**").permitAll()
                                                // 图谱浏览（只读）
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/graph/public", "/api/graph/search",
                                                                "/api/graph/popular", "/api/graph/recommend",
                                                                "/api/graph/share/**", "/api/graph/user/**",
                                                                "/api/graph/*/visualization",
                                                                "/api/graph/*/can-edit",
                                                                "/api/graph/*/favorite/status")
                                                .permitAll()
                                                // 图谱详情（排除 /api/graph/my 等特殊路径，单独匹配数字ID）
                                                .requestMatchers(HttpMethod.GET, "/api/graph/{id:\\d+}").permitAll()
                                                // 图谱节点与关系（只读）
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/graph/*/nodes", "/api/graph/*/nodes/**",
                                                                "/api/graph/*/relations", "/api/graph/*/relations/**",
                                                                "/api/graph/*/node-types",
                                                                "/api/graph/*/relation-types",
                                                                "/api/graph/*/relation-stats")
                                                .permitAll()
                                                // 帖子浏览（只读）
                                                .requestMatchers(HttpMethod.GET,
                                                                "/api/posts", "/api/posts/stats",
                                                                "/api/posts/pinned", "/api/posts/hot",
                                                                "/api/posts/related",
                                                                "/api/posts/*/comments",
                                                                "/api/posts/*/favorite/status",
                                                                "/api/tags/hot")
                                                .permitAll()
                                                // 帖子详情
                                                .requestMatchers(HttpMethod.GET, "/api/posts/{id:\\d+}").permitAll()
                                                // 用户关注状态（只读）
                                                .requestMatchers(HttpMethod.GET, "/api/users/*/follow/status").permitAll()
                                                // 公告与反馈
                                                .requestMatchers("/api/announcements").permitAll()
                                                .requestMatchers("/api/feedback").permitAll()
                                                // 用户和管理员可访问（需要登录）
                                                .requestMatchers("/api/graph/my", "/api/graph/my/**")
                                                .hasAnyRole("USER", "ADMIN")
                                                .requestMatchers("/user/api/profile").hasAnyRole("USER", "ADMIN")
                                                .requestMatchers("/user/api/deactivate").hasAnyRole("USER", "ADMIN")
                                                .requestMatchers("/api/upload/**").hasAnyRole("USER", "ADMIN")
                                                // 其他请求需要认证
                                                .anyRequest().authenticated())
                                // 未认证请求处理：API 返回 JSON 401，页面重定向到登录页
                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        if (request.getRequestURI().startsWith("/api/")) {
                                                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                                                response.setContentType("application/json;charset=UTF-8");
                                                                response.getWriter().write(
                                                                                "{\"success\":false,\"error\":\"请先登录\",\"needLogin\":true}");
                                                        } else {
                                                                response.sendRedirect("/login");
                                                        }
                                                }))
                                // Session 管理：禁用 session fixation（fetch 请求下 changeSessionId 无法正确传递新 cookie）
                                .sessionManagement(session -> session
                                                .sessionFixation(fixation -> fixation.none()))
                                // 配置表单登录
                                .formLogin(form -> form
                                                .loginPage("/login")
                                                .loginProcessingUrl("/api/auth/login")
                                                .successHandler(loginSuccessHandler)
                                                .failureHandler(loginFailureHandler)
                                                .permitAll())
                                // 配置 GitHub OAuth2 登录
                                .oauth2Login(oauth2 -> oauth2
                                                .loginPage("/login")
                                                .userInfoEndpoint(userInfo -> userInfo
                                                                .userService(customOAuth2UserService))
                                                .successHandler(oAuth2LoginSuccessHandler))
                                // 配置登出
                                .logout(logout -> logout
                                                .logoutUrl("/api/auth/logout")
                                                .logoutSuccessUrl("/graph/home.html")
                                                .permitAll());

                return http.build();
        }
}
