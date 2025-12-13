package com.sdu.kgplatform.entity;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 用户角色枚举
 */
public enum Role {
    ADMIN("管理员"),
    USER("用户"),
    VISITOR("游客");

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 转换为 Spring Security 权限
     */
    public GrantedAuthority toAuthority() {
        return new SimpleGrantedAuthority("ROLE_" + this.name());
    }
}
