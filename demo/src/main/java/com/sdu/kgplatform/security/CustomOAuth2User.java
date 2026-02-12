package com.sdu.kgplatform.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import java.util.Collection;
import java.util.Map;

/**
 * 自定义 OAuth2User，携带系统内部 userId 和 avatar
 * 与 CustomUserDetails 保持一致的信息
 */
public class CustomOAuth2User extends DefaultOAuth2User {

    private final Integer userId;
    private final String avatar;

    public CustomOAuth2User(Integer userId, String username, String avatar,
                            Collection<? extends GrantedAuthority> authorities,
                            Map<String, Object> attributes) {
        super(authorities, attributes, "id");
        this.userId = userId;
        this.avatar = avatar;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getAvatar() {
        return avatar;
    }

    @Override
    public String getName() {
        return String.valueOf(userId);
    }
}
