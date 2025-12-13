package com.sdu.kgplatform.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 图谱标签关联实体类 - 对应数据库 graph_tags 表
 * 使用复合主键 (graph_id, tag_id)
 */
@Entity
@Table(name = "graph_tags")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphTag {

    @EmbeddedId
    private GraphTagId id;
}
