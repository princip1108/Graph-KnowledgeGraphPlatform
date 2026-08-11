package com.sdu.kgplatform.config;

import com.sdu.kgplatform.security.CustomOAuth2UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final String sessionCookieName;

    public SecurityConfig(LoginSuccessHandler loginSuccessHandler,
                          LoginFailureHandler loginFailureHandler,
                          OAuth2LoginSuccessHandler oAuth2LoginSuccessHandler,
                          CustomOAuth2UserService customOAuth2UserService,
                          @Value("${server.servlet.session.cookie.name:JSESSIONID}") String sessionCookieName) {
        this.loginSuccessHandler = loginSuccessHandler;
        this.loginFailureHandler = loginFailureHandler;
        this.oAuth2LoginSuccessHandler = oAuth2LoginSuccessHandler;
        this.customOAuth2UserService = customOAuth2UserService;
        this.sessionCookieName = sessionCookieName;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Current frontend uses plain fetch POSTs without CSRF tokens.
                .csrf(AbstractHttpConfigurer::disable)
                .addFilterBefore(new CookieSameOriginFilter(sessionCookieName), UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests((authorize) -> authorize
                        .requestMatchers(
                                "/",
                                "/app",
                                "/login",
                                "/register",
                                "/error",
                                "/user/login_register.html",
                                "/user/password_reset.html",
                                "/user/registration_success.html",
                                "/api/auth/**",
                                "/graph/home.html",
                                "/graph/home_content.html",
                                "/graph/graph_list.html",
                                "/graph/graph_detail.html",
                                "/community/forum_list.html",
                                "/community/post_detail.html",
                                "/community/feedback.html",
                                "/feedback",
                                "/documentation",
                                "/about",
                                "/privacy",
                                "/terms",
                                "/pages/**",
                                "/css/**",
                                "/js/**",
                                "/assets/**",
                                "/libs/**",
                                "/uploads/**",
                                "/oauth2/**")
                        .permitAll()
                        .requestMatchers(
                                "/community/post_edit.html",
                                "/user/profile",
                                "/user/profile.html",
                                "/user/api/profile",
                                "/user/api/user/favorites",
                                "/user/api/deactivate",
                                "/user/api/change-password",
                                "/user/api/logout",
                                "/api/user/favorites",
                                "/api/graph/my",
                                "/api/graph/favorites",
                                "/api/upload/**",
                                "/api/messages/**",
                                "/api/history/**")
                        .authenticated()
                        .requestMatchers(HttpMethod.GET,
                                "/user/api/check-auth",
                                "/user/api/users/**",
                                "/api/users/**",
                                "/api/categories",
                                "/api/category/graph",
                                "/api/graph/public",
                                "/api/graph/recommended",
                                "/api/graph/search",
                                "/api/graph/share/**",
                                "/api/graph/*",
                                "/api/graph/*/visualization",
                                "/api/graph/*/can-edit",
                                "/api/graph/*/nodes/*",
                                "/api/graph/*/nodes/*/neighbors",
                                "/api/graph/*/relations",
                                "/api/posts",
                                "/api/posts/pinned",
                                "/api/posts/stats",
                                "/api/posts/related",
                                "/api/posts/user/**",
                                "/api/posts/*",
                                "/api/posts/*/favorite/status",
                                "/api/posts/*/comments",
                                "/api/download/**")
                        .permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/feedback").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/announcements").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/announcements").hasRole("ADMIN")
                        .requestMatchers("/api/announcements/**").hasRole("ADMIN")
                        .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .formLogin((formLogin) -> formLogin
                        .loginPage("/login")
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler(loginSuccessHandler)
                        .failureHandler(loginFailureHandler)
                        .permitAll())
                .logout((logout) -> logout
                        .logoutUrl("/user/api/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID")
                        .logoutSuccessHandler((request, response, authentication) -> {
                            response.setStatus(HttpStatus.OK.value());
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"success\": true}");
                        })
                        .permitAll())
                .oauth2Login((oauth2) -> oauth2
                        .loginPage("/login")
                        .userInfoEndpoint((userInfo) -> userInfo.userService(customOAuth2UserService))
                        .successHandler(oAuth2LoginSuccessHandler))
                .exceptionHandling((exceptions) -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            String uri = request.getRequestURI();
                            if (uri.startsWith("/api/") || uri.startsWith("/user/api/")) {
                                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"error\": \"未登录\"}");
                                return;
                            }
                            response.sendRedirect("/login");
                        }))
                .anonymous(Customizer.withDefaults());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
