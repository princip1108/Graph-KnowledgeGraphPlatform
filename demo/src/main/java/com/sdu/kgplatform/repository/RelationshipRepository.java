package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.RelationshipEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Neo4j 关系仓库接口
 */
@Repository
public interface RelationshipRepository extends Neo4jRepository<RelationshipEntity, Long> {

       /**
        * 根据图谱 ID 查找所有关系（通过节点的graphId筛选）
        */
       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId RETURN r, a, b")
       List<RelationshipEntity> findByGraphId(@Param("graphId") Integer graphId);

       /**
        * 根据图谱 ID 查找所有关系（包含源节点和目标节点的完整信息）
        * 通过源节点的 graphId 筛选
        */
       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId " +
                     "RETURN id(r) as relationId, r.type as type, a.graphId as graphId, " +
                     "a.nodeId as sourceNodeId, a.name as sourceNodeName, " +
                     "b.nodeId as targetNodeId, b.name as targetNodeName")
       List<Map<String, Object>> findRelationsWithNodesByGraphId(@Param("graphId") Integer graphId);

       /**
        * 根据关系类型查找
        */
       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId AND r.type = $type RETURN r, a, b")
       List<RelationshipEntity> findByGraphIdAndType(@Param("graphId") Integer graphId,
                                                      @Param("type") String type);

       /**
        * 根据关系类型查找（包含源节点和目标节点的完整信息）
        */
       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId AND r.type = $type " +
                     "RETURN id(r) as relationId, r.type as type, a.graphId as graphId, " +
                     "a.nodeId as sourceNodeId, a.name as sourceNodeName, " +
                     "b.nodeId as targetNodeId, b.name as targetNodeName")
       List<Map<String, Object>> findRelationsWithNodesByGraphIdAndType(@Param("graphId") Integer graphId,
                                                                         @Param("type") String type);

       /**
        * 统计图谱中的关系数量（通过节点的graphId筛选）
        */
       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId RETURN count(r)")
       long countByGraphId(@Param("graphId") Integer graphId);

       /**
        * 删除图谱中的所有关系（通过节点的graphId筛选）
        */
       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId DELETE r")
       void deleteByGraphId(@Param("graphId") Integer graphId);

       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE r.graphId = $graphId OR a.graphId = $graphId OR b.graphId = $graphId " +
                     "WITH collect(DISTINCT r) AS relationships, count(DISTINCT r) AS deleted " +
                     "FOREACH (r IN relationships | DELETE r) RETURN deleted")
       long deleteByGraphIdIncludingDangling(@Param("graphId") Integer graphId);

       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE (r.graphId IS NOT NULL AND NOT (r.graphId IN $validGraphIds)) " +
                     "OR (a.graphId IS NOT NULL AND NOT (a.graphId IN $validGraphIds)) " +
                     "OR (b.graphId IS NOT NULL AND NOT (b.graphId IN $validGraphIds)) " +
                     "WITH collect(DISTINCT r) AS relationships, count(DISTINCT r) AS deleted " +
                     "FOREACH (r IN relationships | DELETE r) RETURN deleted")
       long deleteOrphanRelationships(@Param("validGraphIds") Collection<Integer> validGraphIds);

       /**
        * 查找两个节点之间的关系
        */
       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.nodeId = $sourceNodeId AND b.nodeId = $targetNodeId RETURN r")
       List<RelationshipEntity> findBetweenNodes(@Param("sourceNodeId") String sourceNodeId,
                                                  @Param("targetNodeId") String targetNodeId);

       /**
        * 获取图谱中所有关系类型
        */
       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId RETURN DISTINCT r.type")
       List<String> findDistinctTypesByGraphId(@Param("graphId") Integer graphId);

       /**
        * 统计各关系类型的数量
        */
       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId " +
                     "RETURN r.type as type, count(r) as count")
       List<Object[]> countByTypeForGraph(@Param("graphId") Integer graphId);

       @Query("MATCH (a:Entity {nodeId: $sourceNodeId, graphId: $graphId}), " +
                     "(b:Entity {nodeId: $targetNodeId, graphId: $graphId}) " +
                     "CREATE (a)-[r:RELATES_TO {type: $type, graphId: $graphId}]->(b) " +
                     "RETURN id(r)")
       Long createRelationCypher(@Param("sourceNodeId") String sourceNodeId,
                                  @Param("targetNodeId") String targetNodeId,
                                  @Param("type") String type,
                                  @Param("graphId") Integer graphId);

       /**
        * 获取轻量级关系列表 (可视化专用)
        */
       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId " +
                     "RETURN elementId(r) as relationId, r.type as type, " +
                     "a.nodeId as sourceNodeId, b.nodeId as targetNodeId")
       List<com.sdu.kgplatform.dto.LiteRelationshipDto> findLiteRelationshipsByGraphId(@Param("graphId") Integer graphId);

       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId " +
                     "RETURN elementId(r) as relationId, r.type as type, " +
                     "a.nodeId as sourceNodeId, a.name as sourceNodeName, " +
                     "b.nodeId as targetNodeId, b.name as targetNodeName " +
                     "ORDER BY elementId(r) SKIP $skip LIMIT $limit")
       List<com.sdu.kgplatform.dto.RelationshipDto> findExportRelationshipsByGraphId(
                     @Param("graphId") Integer graphId,
                     @Param("skip") long skip,
                     @Param("limit") long limit);

       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId " +
                     "RETURN elementId(r) as relationId, r.type as type, " +
                     "a.nodeId as sourceNodeId, b.nodeId as targetNodeId LIMIT $limit")
       List<com.sdu.kgplatform.dto.LiteRelationshipDto> findLiteRelationshipsByGraphId(@Param("graphId") Integer graphId,
                                                                                       @Param("limit") long limit);

       @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
                     "WHERE a.graphId = $graphId AND b.graphId = $graphId AND r.graphId = $graphId " +
                     "AND a.nodeId IN $nodeIds AND b.nodeId IN $nodeIds " +
                     "RETURN elementId(r) as relationId, r.type as type, " +
                     "a.nodeId as sourceNodeId, b.nodeId as targetNodeId LIMIT $limit")
       List<com.sdu.kgplatform.dto.LiteRelationshipDto> findLiteRelationshipsByGraphIdAndNodeIds(
                     @Param("graphId") Integer graphId,
                     @Param("nodeIds") List<String> nodeIds,
                     @Param("limit") long limit);
}
