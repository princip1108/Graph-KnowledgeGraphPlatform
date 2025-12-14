package com.sdu.kgplatform.entity;

import org.springframework.data.neo4j.core.schema.GeneratedValue;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;
import org.springframework.data.neo4j.core.support.UUIDStringGenerator;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.HashSet;
import java.util.Set;

/**
 * 知识图谱节点实体类 - 对应 Neo4j 中的 'Entity' 节点标签
 */
@Node("Entity") // Neo4j 的节点标签，替代 @Entity 和 @Table
@Data
@NoArgsConstructor
@AllArgsConstructor
@lombok.EqualsAndHashCode(exclude = {"outgoingRelations", "incomingRelations"})
@lombok.ToString(exclude = {"outgoingRelations", "incomingRelations"})
public class NodeEntity {

    // 1. ID 字段：使用 Neo4j 内部 ID
    @Id
    @GeneratedValue
    private Long id;
    
    // 2. 业务 ID：UUID 作为节点属性存储，用于业务逻辑
    private String nodeId;

    // 3. 关系字段：图谱元数据 ID
    // 这是一个属性，用于将 Neo4j 节点关联回 MySQL 中的 Graph 实体
    private Integer graphId;
    private String name;
    private String type;
    private String description;  // 节点简介/描述
    private Integer outDegree;
    private Integer inDegree;
    private Integer totalDegree;

    /**
     * 出边关系 - 当前节点指向其他节点
     */
    @Relationship(type = "RELATES_TO", direction = Relationship.Direction.OUTGOING)
    private Set<RelationshipEntity> outgoingRelations = new HashSet<>();

    /**
     * 入边关系 - 其他节点指向当前节点
     */
    @Relationship(type = "RELATES_TO", direction = Relationship.Direction.INCOMING)
    private Set<RelationshipEntity> incomingRelations = new HashSet<>();
}