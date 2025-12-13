package com.sdu.kgplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 用户标签关联复合主键类
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTagId implements Serializable {

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "tag_id")
    private Integer tagId;
}
