package com.sdu.kgplatform.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

public class CustomUserDetails extends User {

    private final Integer userId;
    private final String avatar;

    public CustomUserDetails(Integer userId, String username, String password, String avatar,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.avatar = avatar;
    }

    public Integer getUserId() {
        return userId;
    }

    public String getAvatar() {
        return avatar;
    }
}
