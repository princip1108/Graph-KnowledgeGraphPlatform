package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 角色实体类 - 对应数据库 roles 表
 * 注意：与 Role 枚举不同，此类用于角色管理
 */
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Roles {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer roleId;

    @Column(name = "role_name", length = 50)
    private String roleName;

    @Column(name = "description", length = 255)
    private String description;
}
