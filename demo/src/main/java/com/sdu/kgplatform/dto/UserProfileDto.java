package com.sdu.kgplatform.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户资料 DTO - 用于展示用户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto {
    private Integer userId;
    private String userName;
    private String email;
    private String phone;
    private String avatar;
    private String gender;
    private LocalDate birthday;
    private String institution;
    private String bio;
    private String role;
    private String status;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private LocalDateTime createdAt;
    private LocalDateTime lastLoginAt;
}
