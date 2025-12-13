package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 浏览历史实体类 - 对应数据库 browsing_history 表
 */
@Entity
@Table(name = "browsing_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BrowsingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type")
    private ResourceType resourceType;

    @Column(name = "post_id")
    private Integer postId;

    @Column(name = "graph_id")
    private Integer graphId;

    @Column(name = "view_time")
    private LocalDateTime viewTime;
}
