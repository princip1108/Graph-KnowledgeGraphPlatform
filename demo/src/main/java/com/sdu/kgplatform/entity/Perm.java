package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 权限实体类 - 对应数据库 perms 表
 * perm = permission (权限)
 */
@Entity
@Table(name = "perms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Perm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer permId;

    @Column(name = "perm_name", length = 100)
    private String permName;

    @Column(name = "description", length = 255)
    private String description;
}
