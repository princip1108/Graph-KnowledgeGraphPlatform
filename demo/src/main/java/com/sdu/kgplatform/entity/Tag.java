package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 标签实体类 - 对应数据库 tag 表
 */
@Entity
@Table(name = "tag")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer tagId;

    @Column(name = "tag_name", length = 100)
    private String tagName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "usage_count")
    private Integer usageCount;
}
