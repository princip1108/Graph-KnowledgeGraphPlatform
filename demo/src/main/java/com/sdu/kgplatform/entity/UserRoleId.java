package com.sdu.kgplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 用户角色关联复合主键类
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserRoleId implements Serializable {

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "role_id")
    private Integer roleId;
}
