package com.sdu.kgplatform.service;

import com.sdu.kgplatform.dto.RelationshipBatchCreateResult;
import com.sdu.kgplatform.dto.RelationshipDto;
import com.sdu.kgplatform.dto.RelationshipPageDto;
import com.sdu.kgplatform.entity.RelationshipEntity;
import com.sdu.kgplatform.repository.KnowledgeGraphRepository;
import com.sdu.kgplatform.repository.RelationshipRepository;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 关系服务层
 */
@Service
public class RelationshipService {

    private static final Logger log = LoggerFactory.getLogger(RelationshipService.class);
    private static final int MAX_RELATION_PAGE_SIZE = 500;
    private static final int MAX_BATCH_CREATE_SIZE = 500;

    private final RelationshipRepository relationshipRepository;
    private final KnowledgeGraphRepository graphRepository;
    private final Neo4jClient neo4jClient;
    private final Driver neo4jDriver;

    public RelationshipService(RelationshipRepository relationshipRepository,
                               KnowledgeGraphRepository graphRepository,
                               Neo4jClient neo4jClient,
                               Driver neo4jDriver) {
        this.relationshipRepository = relationshipRepository;
        this.graphRepository = graphRepository;
        this.neo4jClient = neo4jClient;
        this.neo4jDriver = neo4jDriver;
    }

    // ==================== 创建关系 ====================

    /**
     * 创建关系 - 使用 Neo4j Driver 直接创建并立即提交
     */
    public RelationshipDto createRelationship(Integer graphId, RelationshipDto dto) {
        return createRelationship(graphId, dto, false);
    }

    public RelationshipDto createRelationship(Integer graphId, RelationshipDto dto, boolean skipStats) {
        validateGraphExists(graphId);

        String type = dto.getType() != null ? dto.getType() : "关联";
        String sourceNodeId = dto.getSourceNodeId();
        String targetNodeId = dto.getTargetNodeId();
        
        log.debug("Creating relation via Neo4j Driver: {} -> {} [{}]", sourceNodeId, targetNodeId, type);

        // 使用 Neo4j Driver 直接执行写操作，确保立即提交
        if (sourceNodeId == null || sourceNodeId.isBlank()
                || targetNodeId == null || targetNodeId.isBlank()) {
            throw new IllegalArgumentException("源节点和目标节点不能为空");
        }

        String cypher = "MATCH (a:Entity {nodeId: $sourceNodeId, graphId: $graphId}), " +
                       "(b:Entity {nodeId: $targetNodeId, graphId: $graphId}) " +
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
                throw new IllegalArgumentException("源节点或目标节点不存在，或不属于当前图谱");
            }
        }

        // 更新图谱关系数量（使用 JPA 事务）
        if (!skipStats) {
            updateGraphRelationCount(graphId);
        }

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
        RelationshipBatchCreateResult result = createRelationshipsBatch(graphId, dtos);
        if (!result.isAllSucceeded()) {
            throw new IllegalArgumentException("Some relationships failed to create");
        }
        return result.getRelations();
    }

    public RelationshipBatchCreateResult createRelationshipsBatch(Integer graphId, List<RelationshipDto> dtos) {
        validateGraphExists(graphId);
        if (dtos == null || dtos.isEmpty()) {
            throw new IllegalArgumentException("No relationships provided");
        }
        if (dtos.size() > MAX_BATCH_CREATE_SIZE) {
            throw new IllegalArgumentException("Batch size exceeds " + MAX_BATCH_CREATE_SIZE);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        List<Map<String, Object>> failedItems = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            RelationshipDto dto = dtos.get(i);
            String sourceNodeId = dto == null ? null : dto.getSourceNodeId();
            String targetNodeId = dto == null ? null : dto.getTargetNodeId();
            if (sourceNodeId == null || sourceNodeId.isBlank()
                    || targetNodeId == null || targetNodeId.isBlank()) {
                failedItems.add(failedItem(i, sourceNodeId, targetNodeId, "sourceNodeId and targetNodeId are required"));
                continue;
            }

            Map<String, Object> row = new HashMap<>();
            row.put("index", i);
            row.put("sourceNodeId", sourceNodeId.trim());
            row.put("targetNodeId", targetNodeId.trim());
            row.put("type", normalizeType(dto.getType()));
            rows.add(row);
        }

        List<RelationshipDto> created = new ArrayList<>();
        Set<Integer> createdIndexes = new HashSet<>();
        if (!rows.isEmpty()) {
            String cypher = "UNWIND $relations AS row " +
                    "MATCH (a:Entity {nodeId: row.sourceNodeId, graphId: $graphId}) " +
                    "MATCH (b:Entity {nodeId: row.targetNodeId, graphId: $graphId}) " +
                    "CREATE (a)-[r:RELATES_TO {type: row.type, graphId: $graphId}]->(b) " +
                    "RETURN row.index AS index, elementId(r) AS relationId, r.type AS type, " +
                    "a.nodeId AS sourceNodeId, a.name AS sourceNodeName, " +
                    "b.nodeId AS targetNodeId, b.name AS targetNodeName " +
                    "ORDER BY index";
            Map<String, Object> params = new HashMap<>();
            params.put("graphId", graphId);
            params.put("relations", rows);

            try (Session session = neo4jDriver.session()) {
                Result result = session.run(cypher, params);
                while (result.hasNext()) {
                    var record = result.next();
                    int index = record.get("index").asInt();
                    createdIndexes.add(index);
                    created.add(RelationshipDto.builder()
                            .relationId(record.get("relationId").asString())
                            .type(record.get("type").isNull() ? null : record.get("type").asString())
                            .sourceNodeId(record.get("sourceNodeId").isNull() ? null : record.get("sourceNodeId").asString())
                            .sourceNodeName(record.get("sourceNodeName").isNull() ? null : record.get("sourceNodeName").asString())
                            .targetNodeId(record.get("targetNodeId").isNull() ? null : record.get("targetNodeId").asString())
                            .targetNodeName(record.get("targetNodeName").isNull() ? null : record.get("targetNodeName").asString())
                            .build());
                }
            }

            for (Map<String, Object> row : rows) {
                int index = ((Number) row.get("index")).intValue();
                if (!createdIndexes.contains(index)) {
                    failedItems.add(failedItem(index,
                            (String) row.get("sourceNodeId"),
                            (String) row.get("targetNodeId"),
                            "source or target node not found in graph"));
                }
            }
        }

        if (!created.isEmpty()) {
            updateGraphRelationCount(graphId);
        }

        return RelationshipBatchCreateResult.builder()
                .success(!created.isEmpty())
                .allSucceeded(failedItems.isEmpty())
                .requestedCount(dtos.size())
                .count(created.size())
                .successCount(created.size())
                .failedCount(failedItems.size())
                .relations(created)
                .failedItems(failedItems)
                .build();
    }

    // ==================== 查询关系 ====================

    /**
     * 获取图谱的所有关系（包含源节点和目标节点信息）
     */
    @Transactional(value = "neo4jTransactionManager", readOnly = true)
    public List<RelationshipDto> getRelationshipsByGraphId(Integer graphId) {
        log.debug("Fetching relations for graphId: {}", graphId);
        // 使用 Neo4jClient 查询关系
        String cypher = "MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                       "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId " +
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

    @Transactional(value = "neo4jTransactionManager", readOnly = true)
    public List<RelationshipDto> getExportRelationshipsByGraphId(Integer graphId, long skip, long limit) {
        return relationshipRepository.findExportRelationshipsByGraphId(graphId, skip, limit);
    }

    @Transactional(value = "neo4jTransactionManager", readOnly = true)
    public RelationshipPageDto getRelationshipsByGraphId(Integer graphId,
                                                         int page,
                                                         int size,
                                                         String type,
                                                         String source,
                                                         String target) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_RELATION_PAGE_SIZE);
        int skip = safePage * safeSize;

        Map<String, Object> params = new HashMap<>();
        params.put("graphId", graphId);
        StringBuilder where = new StringBuilder("WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId ");
        if (hasText(type)) {
            where.append("AND r.type = $type ");
            params.put("type", type.trim());
        }
        if (hasText(source)) {
            where.append("AND (toLower(coalesce(a.nodeId, '')) CONTAINS $source ")
                    .append("OR toLower(coalesce(a.name, '')) CONTAINS $source) ");
            params.put("source", source.trim().toLowerCase());
        }
        if (hasText(target)) {
            where.append("AND (toLower(coalesce(b.nodeId, '')) CONTAINS $target ")
                    .append("OR toLower(coalesce(b.name, '')) CONTAINS $target) ");
            params.put("target", target.trim().toLowerCase());
        }

        String baseMatch = "MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) ";
        String countCypher = baseMatch + where + "RETURN count(r) AS total";
        String dataCypher = baseMatch + where +
                "RETURN elementId(r) AS relationId, r.type AS type, " +
                "a.nodeId AS sourceNodeId, a.name AS sourceNodeName, " +
                "b.nodeId AS targetNodeId, b.name AS targetNodeName " +
                "ORDER BY relationId SKIP $skip LIMIT $limit";

        long total;
        List<RelationshipDto> relations = new ArrayList<>();
        try (Session session = neo4jDriver.session()) {
            Result countResult = session.run(countCypher, params);
            total = countResult.hasNext() ? countResult.next().get("total").asLong() : 0L;

            Map<String, Object> dataParams = new HashMap<>(params);
            dataParams.put("skip", skip);
            dataParams.put("limit", safeSize);
            Result dataResult = session.run(dataCypher, dataParams);
            while (dataResult.hasNext()) {
                var record = dataResult.next();
                relations.add(RelationshipDto.builder()
                        .relationId(record.get("relationId").asString())
                        .type(record.get("type").isNull() ? null : record.get("type").asString())
                        .sourceNodeId(record.get("sourceNodeId").isNull() ? null : record.get("sourceNodeId").asString())
                        .sourceNodeName(record.get("sourceNodeName").isNull() ? null : record.get("sourceNodeName").asString())
                        .targetNodeId(record.get("targetNodeId").isNull() ? null : record.get("targetNodeId").asString())
                        .targetNodeName(record.get("targetNodeName").isNull() ? null : record.get("targetNodeName").asString())
                        .build());
            }
        }

        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
        boolean hasNext = skip + relations.size() < total;
        return RelationshipPageDto.builder()
                .content(relations)
                .relations(relations)
                .totalElements(total)
                .totalPages(totalPages)
                .page(safePage)
                .size(safeSize)
                .number(safePage)
                .numberOfElements(relations.size())
                .first(safePage == 0)
                .last(!hasNext)
                .hasNext(hasNext)
                .limited(total > relations.size() || size > MAX_RELATION_PAGE_SIZE)
                .build();
    }

    /**
     * 根据类型获取关系
     */
    @Transactional(value = "neo4jTransactionManager", readOnly = true)
    public List<RelationshipDto> getRelationshipsByType(Integer graphId, String type) {
        String cypher = "MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                       "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId AND r.type = $type " +
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
     * 删除图谱的所有关系
     */
    @Transactional("neo4jTransactionManager")
    public void deleteRelationshipsByGraphId(Integer graphId) {
        relationshipRepository.deleteByGraphId(graphId);
        updateGraphRelationCount(graphId);
    }

    @Transactional("neo4jTransactionManager")
    public void deleteRelationshipByElementId(Integer graphId, String elementId) {
        String cypher = "MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                       "WHERE elementId(r) = $elementId " +
                       "AND a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId " +
                       "WITH r DELETE r RETURN count(*) as deleted";

        try (Session session = neo4jDriver.session()) {
            Result result = session.run(cypher, Map.of("elementId", elementId, "graphId", graphId));
            long deleted = result.hasNext() ? result.next().get("deleted").asLong() : 0L;
            if (deleted == 0L) {
                throw new IllegalArgumentException("关系不存在或不属于当前图谱: " + elementId);
            }
            updateGraphRelationCount(graphId);
        }
    }

    @Transactional("neo4jTransactionManager")
    public void deleteRelationshipByRelationId(Integer graphId, String relationId) {
        validateGraphExists(graphId);
        if (relationId == null || relationId.isBlank()) {
            throw new IllegalArgumentException("关系ID不能为空");
        }

        deleteRelationshipByElementId(graphId, relationId);
    }

    // ==================== 统计方法 ====================

    /**
     * 统计图谱关系数量
     */
    public long countRelationshipsByGraphId(Integer graphId) {
        return relationshipRepository.countByGraphId(graphId);
    }

    // ==================== 私有辅助方法 ====================

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String normalizeType(String type) {
        return hasText(type) ? type.trim() : "关联";
    }

    private Map<String, Object> failedItem(int index, String sourceNodeId, String targetNodeId, String error) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", index);
        item.put("sourceNodeId", sourceNodeId);
        item.put("targetNodeId", targetNodeId);
        item.put("error", error);
        return item;
    }

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
