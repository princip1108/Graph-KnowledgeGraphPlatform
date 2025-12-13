package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用户标签关联实体类 - 对应数据库 user_tag 表
 * 使用复合主键 (user_id, tag_id)
 */
@Entity
@Table(name = "user_tag")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserTag {

    @EmbeddedId
    private UserTagId id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
