package com.sdu.kgplatform.common;

import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.security.CustomOAuth2User;
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
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        if (auth.getPrincipal() instanceof CustomUserDetails cud) {
            return cud.getUserId();
        }
        if (auth.getPrincipal() instanceof CustomOAuth2User oau) {
            return oau.getUserId();
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
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }

        // 优先从 principal 直接取 userId（最可靠）
        if (auth.getPrincipal() instanceof CustomUserDetails cud) {
            return userRepository.findById(cud.getUserId()).orElse(null);
        }
        if (auth.getPrincipal() instanceof CustomOAuth2User oau) {
            return userRepository.findById(oau.getUserId()).orElse(null);
        }

        // 兜底：按 username 查找（email → phone → userName）
        String username = getCurrentUsername();
        if (username == null) {
            return null;
        }
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhone(username))
                .or(() -> userRepository.findByUserName(username))
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
