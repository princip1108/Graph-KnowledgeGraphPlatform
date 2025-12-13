package com.sdu.kgplatform.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.io.Serializable;

/**
 * 图谱标签关联复合主键类
 */
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphTagId implements Serializable {

    @Column(name = "graph_id")
    private Integer graphId;

    @Column(name = "tag_id")
    private Integer tagId;
}
