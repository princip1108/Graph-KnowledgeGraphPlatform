package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.RelationshipEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

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
    @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) WHERE a.graphId = $graphId RETURN r, a, b")
    List<RelationshipEntity> findByGraphId(Integer graphId);

    /**
     * 根据图谱 ID 查找所有关系（包含源节点和目标节点的完整信息）
     * 通过源节点的 graphId 筛选
     */
    @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) WHERE a.graphId = $graphId " +
           "RETURN id(r) as relationId, r.type as type, a.graphId as graphId, " +
           "a.nodeId as sourceNodeId, a.name as sourceNodeName, " +
           "b.nodeId as targetNodeId, b.name as targetNodeName")
    List<Map<String, Object>> findRelationsWithNodesByGraphId(Integer graphId);

    /**
     * 根据关系类型查找
     */
    @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) WHERE a.graphId = $graphId AND r.type = $type RETURN r, a, b")
    List<RelationshipEntity> findByGraphIdAndType(Integer graphId, String type);

    /**
     * 根据关系类型查找（包含源节点和目标节点的完整信息）
     */
    @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) WHERE a.graphId = $graphId AND r.type = $type " +
           "RETURN id(r) as relationId, r.type as type, a.graphId as graphId, " +
           "a.nodeId as sourceNodeId, a.name as sourceNodeName, " +
           "b.nodeId as targetNodeId, b.name as targetNodeName")
    List<Map<String, Object>> findRelationsWithNodesByGraphIdAndType(Integer graphId, String type);

    /**
     * 统计图谱中的关系数量（通过节点的graphId筛选）
     */
    @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) WHERE a.graphId = $graphId RETURN count(r)")
    long countByGraphId(Integer graphId);

    /**
     * 删除图谱中的所有关系（通过节点的graphId筛选）
     */
    @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) WHERE a.graphId = $graphId DELETE r")
    void deleteByGraphId(Integer graphId);

    /**
     * 查找两个节点之间的关系
     */
    @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) " +
           "WHERE a.nodeId = $sourceNodeId AND b.nodeId = $targetNodeId RETURN r")
    List<RelationshipEntity> findBetweenNodes(String sourceNodeId, String targetNodeId);

    /**
     * 获取图谱中所有关系类型
     */
    @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) WHERE r.graphId = $graphId RETURN DISTINCT r.type")
    List<String> findDistinctTypesByGraphId(Integer graphId);

    /**
     * 统计各关系类型的数量
     */
    @Query("MATCH (a:Entity)-[r:RELATES_TO]->(b:Entity) WHERE a.graphId = $graphId " +
           "RETURN r.type as type, count(r) as count")
    List<Object[]> countByTypeForGraph(Integer graphId);

    /**
     * 统计所有关系数量
     */
    @Query("MATCH ()-[r:RELATES_TO]->() RETURN count(r)")
    Long countAllRelations();
    
    /**
     * 统计所有节点数量
     */
    @Query("MATCH (n:Entity) RETURN count(n)")
    Long countAllNodes();

    /**
     * 使用原生 Cypher 创建关系
     */
    @Query("MATCH (a:Entity {nodeId: $sourceNodeId}), (b:Entity {nodeId: $targetNodeId}) " +
           "CREATE (a)-[r:RELATES_TO {type: $type, graphId: $graphId}]->(b) " +
           "RETURN id(r)")
    Long createRelationCypher(String sourceNodeId, String targetNodeId, String type, Integer graphId);
}
