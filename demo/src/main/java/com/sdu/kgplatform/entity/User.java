package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor

public class User {
    @Id //主键
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer userId;

    @Column(name = "user_name",length = 100)
    private String userName;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender")
    private Gender gender;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "created_at", updatable = false) 
    private LocalDateTime createdAt;

    @Column(name = "update_at")
    private LocalDateTime updateAt;

    @Column(name = "email", length = 100)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "password_hash", length = 300)
    private String passwordHash;

    @Column(name = "institution", length = 100)
    private String institution;

    @Column(name = "email_verified")
    private Boolean emailVerified;

    @Column(name = "phone_verified")
    private Boolean phoneVerified;

    @Lob // 对应数据库的 LOB（Large Object）类型，如 TEXT 或 BLOB
    @Column(name = "profile")
    private String profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_status", length = 20)
    private UserStatus userStatus;

    @Column(name = "avatar", length = 200)
    private String avatar;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "nickname", length = 200)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20)
    private Role role;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 创建时自动设置时间
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updateAt = LocalDateTime.now();
    }

    /**
     * 更新时自动设置时间
     */
    @PreUpdate
    protected void onUpdate() {
        this.updateAt = LocalDateTime.now();
    }
}
