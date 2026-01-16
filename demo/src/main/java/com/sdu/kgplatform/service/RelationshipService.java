package com.sdu.kgplatform.service;

import com.sdu.kgplatform.dto.RelationshipDto;
import com.sdu.kgplatform.entity.NodeEntity;
import com.sdu.kgplatform.entity.RelationshipEntity;
import com.sdu.kgplatform.repository.KnowledgeGraphRepository;
import com.sdu.kgplatform.repository.NodeRepository;
import com.sdu.kgplatform.repository.RelationshipRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 关系服务层
 */
@Service
public class RelationshipService {

    private static final Logger log = LoggerFactory.getLogger(RelationshipService.class);

    private final RelationshipRepository relationshipRepository;
    private final NodeRepository nodeRepository;
    private final KnowledgeGraphRepository graphRepository;
    private final Neo4jClient neo4jClient;
    private final Driver neo4jDriver;

    public RelationshipService(RelationshipRepository relationshipRepository,
                               NodeRepository nodeRepository,
                               KnowledgeGraphRepository graphRepository,
                               Neo4jClient neo4jClient,
                               Driver neo4jDriver) {
        this.relationshipRepository = relationshipRepository;
        this.nodeRepository = nodeRepository;
        this.graphRepository = graphRepository;
        this.neo4jClient = neo4jClient;
        this.neo4jDriver = neo4jDriver;
    }

    // ==================== 创建关系 ====================

    /**
     * 创建关系 - 使用 Neo4j Driver 直接创建并立即提交
     */
    public RelationshipDto createRelationship(Integer graphId, RelationshipDto dto) {
        validateGraphExists(graphId);

        String type = dto.getType() != null ? dto.getType() : "关联";
        String sourceNodeId = dto.getSourceNodeId();
        String targetNodeId = dto.getTargetNodeId();
        
        log.debug("Creating relation via Neo4j Driver: {} -> {} [{}]", sourceNodeId, targetNodeId, type);

        // 使用 Neo4j Driver 直接执行写操作，确保立即提交
        String cypher = "MATCH (a:Entity {nodeId: $sourceNodeId}), (b:Entity {nodeId: $targetNodeId}) " +
                       "CREATE (a)-[r:RELATES_TO {type: $type, graphId: $graphId}]->(b) " +
                       "RETURN elementId(r) as relationId, a.name as sourceName, b.name as targetName";
        
        String relationId = null;
        String sourceName = null;
        String targetName = null;
        
        try (Session session = neo4jDriver.session()) {
            Result result = session.run(cypher, 
                    Map.of("sourceNodeId", sourceNodeId, 
                           "targetNodeId", targetNodeId, 
                           "type", type, 
                           "graphId", graphId));
            
            if (result.hasNext()) {
                var record = result.next();
                relationId = record.get("relationId").asString();
                sourceName = record.get("sourceName").asString();
                targetName = record.get("targetName").asString();
                log.debug("Created relation with Neo4j elementId: {}", relationId);
            } else {
                log.warn("No result returned - nodes may not exist");
            }
            
            // 验证关系数量
            Result countResult = session.run("MATCH ()-[r:RELATES_TO]->() RETURN count(r) as cnt");
            if (countResult.hasNext()) {
                long count = countResult.next().get("cnt").asLong();
                log.debug("Total relations after creation: {}", count);
            }
        }

        // 更新图谱关系数量（使用 JPA 事务）
        updateGraphRelationCount(graphId);

        return RelationshipDto.builder()
                .relationId(relationId)
                .sourceNodeId(sourceNodeId)
                .targetNodeId(targetNodeId)
                .sourceNodeName(sourceName)
                .targetNodeName(targetName)
                .type(type)
                .build();
    }

    /**
     * 批量创建关系
     */
    @Transactional("neo4jTransactionManager")
    public List<RelationshipDto> createRelationships(Integer graphId, List<RelationshipDto> dtos) {
        return dtos.stream()
                .map(dto -> createRelationship(graphId, dto))
                .collect(Collectors.toList());
    }

    // ==================== 查询关系 ====================

    /**
     * 获取图谱的所有关系（包含源节点和目标节点信息）
     */
    @Transactional(value = "neo4jTransactionManager", readOnly = true)
    public List<RelationshipDto> getRelationshipsByGraphId(Integer graphId) {
        log.debug("Fetching relations for graphId: {}", graphId);
        
        // 调试：统计数量
        Long totalNodes = relationshipRepository.countAllNodes();
        Long totalRelations = relationshipRepository.countAllRelations();
        log.debug("Total nodes in Neo4j: {}", totalNodes);
        log.debug("Total relations in Neo4j: {}", totalRelations);
        
        // 调试：查看节点的实际属性
        String debugCypher = "MATCH (n:Entity) WHERE n.graphId = $graphId RETURN n.nodeId as nodeId, n.name as name LIMIT 3";
        Collection<String> sampleNodes = neo4jClient.query(debugCypher)
                .bind(graphId).to("graphId")
                .fetchAs(String.class)
                .mappedBy((ts, record) -> "nodeId=" + record.get("nodeId") + ", name=" + record.get("name"))
                .all();
        log.debug("Sample nodes in Neo4j: {}", sampleNodes);
        
        // 使用 Neo4jClient 查询关系
        String cypher = "MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) WHERE a.graphId = $graphId " +
                       "RETURN elementId(r) as relationId, r.type as type, " +
                       "a.nodeId as sourceNodeId, a.name as sourceNodeName, " +
                       "b.nodeId as targetNodeId, b.name as targetNodeName";
        
        Collection<RelationshipDto> relations = neo4jClient.query(cypher)
                .bind(graphId).to("graphId")
                .fetchAs(RelationshipDto.class)
                .mappedBy((typeSystem, record) -> RelationshipDto.builder()
                        .relationId(record.get("relationId").asString())
                        .type(record.get("type").isNull() ? null : record.get("type").asString())
                        .sourceNodeId(record.get("sourceNodeId").isNull() ? null : record.get("sourceNodeId").asString())
                        .sourceNodeName(record.get("sourceNodeName").isNull() ? null : record.get("sourceNodeName").asString())
                        .targetNodeId(record.get("targetNodeId").isNull() ? null : record.get("targetNodeId").asString())
                        .targetNodeName(record.get("targetNodeName").isNull() ? null : record.get("targetNodeName").asString())
                        .build())
                .all();
        
        log.debug("Found {} relations for graphId {}", relations.size(), graphId);
        return relations.stream().collect(Collectors.toList());
    }

    /**
     * 根据类型获取关系
     */
    @Transactional(value = "neo4jTransactionManager", readOnly = true)
    public List<RelationshipDto> getRelationshipsByType(Integer graphId, String type) {
        String cypher = "MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                       "WHERE a.graphId = $graphId AND r.type = $type " +
                       "RETURN elementId(r) as relationId, r.type as type, " +
                       "a.nodeId as sourceNodeId, a.name as sourceNodeName, " +
                       "b.nodeId as targetNodeId, b.name as targetNodeName";
        
        Collection<RelationshipDto> relations = neo4jClient.query(cypher)
                .bind(graphId).to("graphId")
                .bind(type).to("type")
                .fetchAs(RelationshipDto.class)
                .mappedBy((typeSystem, record) -> RelationshipDto.builder()
                        .relationId(record.get("relationId").asString())
                        .type(record.get("type").isNull() ? null : record.get("type").asString())
                        .sourceNodeId(record.get("sourceNodeId").isNull() ? null : record.get("sourceNodeId").asString())
                        .sourceNodeName(record.get("sourceNodeName").isNull() ? null : record.get("sourceNodeName").asString())
                        .targetNodeId(record.get("targetNodeId").isNull() ? null : record.get("targetNodeId").asString())
                        .targetNodeName(record.get("targetNodeName").isNull() ? null : record.get("targetNodeName").asString())
                        .build())
                .all();
        
        return relations.stream().collect(Collectors.toList());
    }

    /**
     * 获取两个节点之间的关系
     */
    public List<RelationshipDto> getRelationshipsBetweenNodes(String sourceNodeId, String targetNodeId) {
        List<RelationshipEntity> relations = relationshipRepository.findBetweenNodes(sourceNodeId, targetNodeId);
        return relations.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * 获取图谱中所有关系类型
     */
    public List<String> getRelationshipTypes(Integer graphId) {
        return relationshipRepository.findDistinctTypesByGraphId(graphId);
    }

    /**
     * 获取关系类型统计
     */
    public Map<String, Long> getRelationshipTypeStats(Integer graphId) {
        List<Object[]> results = relationshipRepository.countByTypeForGraph(graphId);
        return results.stream()
                .collect(Collectors.toMap(
                        arr -> (String) arr[0],
                        arr -> (Long) arr[1]
                ));
    }

    // ==================== 删除关系 ====================

    /**
     * 删除关系
     */
    @Transactional("neo4jTransactionManager")
    public void deleteRelationship(Long relationId) {
        RelationshipEntity relation = relationshipRepository.findById(relationId)
                .orElseThrow(() -> new IllegalArgumentException("关系不存在: " + relationId));
        Integer graphId = relation.getGraphId();
        relationshipRepository.delete(relation);
        updateGraphRelationCount(graphId);
    }

    /**
     * 删除图谱的所有关系
     */
    @Transactional("neo4jTransactionManager")
    public void deleteRelationshipsByGraphId(Integer graphId) {
        relationshipRepository.deleteByGraphId(graphId);
        updateGraphRelationCount(graphId);
    }

    /**
     * 根据 Neo4j elementId 删除关系
     */
    @Transactional("neo4jTransactionManager")
    public void deleteRelationshipByElementId(String elementId) {
        String cypher = "MATCH ()-[r]->() WHERE elementId(r) = $elementId " +
                       "WITH r, r.graphId as graphId " +
                       "DELETE r " +
                       "RETURN graphId";
        
        try (Session session = neo4jDriver.session()) {
            Result result = session.run(cypher, Map.of("elementId", elementId));
            if (result.hasNext()) {
                Object graphIdObj = result.next().get("graphId").asObject();
                if (graphIdObj instanceof Long) {
                    updateGraphRelationCount(((Long) graphIdObj).intValue());
                } else if (graphIdObj instanceof Integer) {
                    updateGraphRelationCount((Integer) graphIdObj);
                }
            }
        }
    }

    // ==================== 统计方法 ====================

    /**
     * 统计图谱关系数量
     */
    public long countRelationshipsByGraphId(Integer graphId) {
        return relationshipRepository.countByGraphId(graphId);
    }

    // ==================== 私有辅助方法 ====================

    private void validateGraphExists(Integer graphId) {
        if (!graphRepository.existsById(graphId)) {
            throw new IllegalArgumentException("图谱不存在: " + graphId);
        }
    }

    private void updateGraphRelationCount(Integer graphId) {
        graphRepository.findById(graphId).ifPresent(graph -> {
            long count = relationshipRepository.countByGraphId(graphId);
            graph.setRelationCount((int) count);
            graph.setLastModified(LocalDateTime.now());
            graphRepository.save(graph);
        });
    }

    private RelationshipDto convertToDto(RelationshipEntity relation) {
        return RelationshipDto.builder()
                .relationId(relation.getId() != null ? relation.getId().toString() : null)
                .targetNodeId(relation.getTargetNode() != null ? relation.getTargetNode().getNodeId() : null)
                .targetNodeName(relation.getTargetNode() != null ? relation.getTargetNode().getName() : null)
                .type(relation.getType())
                .build();
    }

    /**
     * 将查询结果 Map 转换为 DTO（包含源节点和目标节点信息）
     */
    private RelationshipDto convertMapToDto(Map<String, Object> map) {
        return RelationshipDto.builder()
                .relationId(map.get("relationId") != null ? map.get("relationId").toString() : null)
                .sourceNodeId((String) map.get("sourceNodeId"))
                .sourceNodeName((String) map.get("sourceNodeName"))
                .targetNodeId((String) map.get("targetNodeId"))
                .targetNodeName((String) map.get("targetNodeName"))
                .type((String) map.get("type"))
                .build();
    }
}
