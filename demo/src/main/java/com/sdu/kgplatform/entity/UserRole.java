package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 用户角色关联实体类 - 对应数据库 user_roles 表
 * 使用复合主键 (user_id, role_id)
 */
@Entity
@Table(name = "user_roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRole {

    @EmbeddedId
    private UserRoleId id;
}
