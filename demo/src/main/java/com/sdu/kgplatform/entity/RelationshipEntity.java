package com.sdu.kgplatform.entity;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 知识图谱关系实体类 - Neo4j 关系属性类
 * 使用 @RelationshipProperties 定义关系上的属性
 */
@RelationshipProperties
@Data
@NoArgsConstructor
@AllArgsConstructor
@lombok.EqualsAndHashCode(exclude = {"targetNode"})
@lombok.ToString(exclude = {"targetNode"})
public class RelationshipEntity {

    @Id
    @GeneratedValue
    private Long id;

    /**
     * 关系类型（如：属于、包含、关联等）
     */
    private String type;
    private Integer graphId;

    /**
     * 目标节点 - Neo4j 要求关系属性类必须有一个 @TargetNode
     */
    @TargetNode
    private NodeEntity targetNode;
}
