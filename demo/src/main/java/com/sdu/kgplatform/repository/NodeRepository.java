package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.NodeEntity;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Neo4j 节点仓库接口
 */
@Repository
public interface NodeRepository extends Neo4jRepository<NodeEntity, Long> {

    /**
     * 根据业务 ID (nodeId) 查找节点
     */
    Optional<NodeEntity> findByNodeId(String nodeId);

    /**
     * 根据图谱 ID 查找所有节点
     */
    List<NodeEntity> findByGraphId(Integer graphId);

    /**
     * 根据名称查找节点
     */
    Optional<NodeEntity> findByName(String name);

    /**
     * 根据图谱 ID 和名称查找节点
     */
    Optional<NodeEntity> findByGraphIdAndName(Integer graphId, String name);

    /**
     * 根据类型查找节点
     */
    List<NodeEntity> findByType(String type);

    /**
     * 根据图谱 ID 和类型查找节点
     */
    List<NodeEntity> findByGraphIdAndType(Integer graphId, String type);

    /**
     * 根据图谱 ID 删除所有节点
     */
    void deleteByGraphId(Integer graphId);

    /**
     * 统计图谱中的节点数量
     */
    long countByGraphId(Integer graphId);

    /**
     * 根据名称模糊查询节点
     */
    @Query("MATCH (n:Entity) WHERE n.graphId = $graphId AND n.name CONTAINS $keyword RETURN n")
    List<NodeEntity> findByGraphIdAndNameContaining(Integer graphId, String keyword);

    /**
     * 查找某节点的所有相邻节点（出边方向）
     */
    @Query("MATCH (n:Entity)-[:RELATES_TO]->(m:Entity) WHERE n.nodeId = $nodeId RETURN m")
    List<NodeEntity> findOutgoingNeighbors(String nodeId);

    /**
     * 查找某节点的所有相邻节点（入边方向）
     */
    @Query("MATCH (n:Entity)<-[:RELATES_TO]-(m:Entity) WHERE n.nodeId = $nodeId RETURN m")
    List<NodeEntity> findIncomingNeighbors(String nodeId);

    /**
     * 查找两个节点之间的最短路径
     */
    @Query("MATCH path = shortestPath((a:Entity)-[:RELATES_TO*]-(b:Entity)) " +
           "WHERE a.nodeId = $startNodeId AND b.nodeId = $endNodeId RETURN path")
    List<NodeEntity> findShortestPath(String startNodeId, String endNodeId);
}
