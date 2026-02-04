package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

/**
 * 搜索历史实体类 - 对应数据库 search_history 表
 */
@Entity
@Table(name = "search_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "query_text", length = 255)
    private String queryText;

    @Column(name = "search_type", length = 20)
    private String searchType; // graph, forum

    @Column(name = "search_time")
    private LocalDateTime searchTime;
}
