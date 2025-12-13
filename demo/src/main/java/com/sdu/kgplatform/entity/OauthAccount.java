package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * OAuth账户实体类 - 对应数据库 oauth_accounts 表
 */
@Entity
@Table(name = "oauth_accounts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OauthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform_name")
    private PlatformName platformName;

    @Column(name = "platform_uid", length = 128)
    private String platformUid;

    @Column(name = "access_token", length = 1024)
    private byte[] accessToken;

    @Column(name = "refresh_token", length = 1024)
    private byte[] refreshToken;

    @Column(name = "scope", length = 512)
    private String scope;

    @Column(name = "platform_nickname", length = 100)
    private String platformNickname;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
