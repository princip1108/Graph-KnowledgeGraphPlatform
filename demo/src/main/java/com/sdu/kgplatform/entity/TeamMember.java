package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 团队成员实体类 - 对应数据库 team_member 表
 */
@Entity
@Table(name = "team_member")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer memberId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "role", length = 50)
    private String role;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "bio", length = 500)
    private String bio;

    @Column(name = "professional_field", length = 100)
    private String professionalField;

    @Lob
    @Column(name = "work_experience")
    private String workExperience;

    @Lob
    @Column(name = "education_background")
    private String educationBackground;
}
