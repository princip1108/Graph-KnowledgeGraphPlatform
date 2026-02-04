package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 分类实体类 - 对应数据库 category 表
 */
@Entity
@Table(name = "category")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer categoryId;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    /**
     * 分类类型：GRAPH 或 POST
     */
    @Column(name = "type", length = 20, nullable = false)
    private String type;

    @Column(name = "priority")
    private Integer priority = 0;
}
