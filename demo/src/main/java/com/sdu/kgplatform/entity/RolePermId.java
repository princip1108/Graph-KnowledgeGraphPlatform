package com.sdu.kgplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 角色权限关联复合主键类
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RolePermId implements Serializable {

    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "perm_id")
    private Integer permId;
}
