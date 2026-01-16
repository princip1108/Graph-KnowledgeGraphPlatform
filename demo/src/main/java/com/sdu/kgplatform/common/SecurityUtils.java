package com.sdu.kgplatform.common;

import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.security.CustomUserDetails;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全工具类
 * 提供获取当前登录用户的通用方法
 */
public final class SecurityUtils {

    private SecurityUtils() {
        // 防止实例化
    }

    /**
     * 获取当前登录用户的ID
     * @return 用户ID，未登录返回null
     */
    public static Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && auth.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) auth.getPrincipal()).getUserId();
        }
        return null;
    }

    /**
     * 获取当前登录用户名
     * @return 用户名，未登录返回null
     */
    public static String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() 
                && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    /**
     * 检查是否已登录
     * @return true 已登录，false 未登录
     */
    public static boolean isAuthenticated() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated() 
                && !"anonymousUser".equals(auth.getPrincipal());
    }

    /**
     * 获取当前登录用户实体
     * @param userRepository 用户仓库
     * @return User实体，未登录或用户不存在返回null
     */
    public static User getCurrentUser(UserRepository userRepository) {
        String username = getCurrentUsername();
        if (username == null) {
            return null;
        }
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhone(username))
                .orElse(null);
    }

    /**
     * 检查当前用户是否为管理员
     * @return true 是管理员，false 不是管理员或未登录
     */
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
