package com.sdu.kgplatform.config;

import com.sdu.kgplatform.dto.UserProfileDto;
import com.sdu.kgplatform.security.CustomOAuth2User;
import com.sdu.kgplatform.security.CustomUserDetails;
import com.sdu.kgplatform.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private final UserService userService;

    public GlobalModelAttributes(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute("currentUser")
    public UserProfileDto getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            try {
                Object principal = auth.getPrincipal();
                if (principal instanceof CustomOAuth2User oAuth2User) {
                    return userService.getUserProfileById(oAuth2User.getUserId(), false);
                }
                if (principal instanceof CustomUserDetails userDetails) {
                    return userService.getUserProfileById(userDetails.getUserId(), false);
                }
                return userService.getUserProfile(auth.getName());
            } catch (Exception e) {
                // 如果获取用户信息失败，视为未登录或仅返回空对象
                return null;
            }
        }
        return null;
    }
}
