package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 角色权限关联实体类 - 对应数据库 role_perms 表
 * 使用复合主键 (role_id, perm_id)
 * perm = permission (权限)
 */
@Entity
@Table(name = "role_perms")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePerm {

    @EmbeddedId
    private RolePermId id;
}
