package com.sdu.kgplatform.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GraphImportBatchWriter {

    private static final int BATCH_SIZE = 1000;

    private static final String CREATE_NODES_CYPHER = """
            UNWIND $nodes AS node
            CREATE (n:Entity {
                nodeId: node.nodeId,
                graphId: $graphId,
                name: node.name,
                type: node.type,
                outDegree: 0,
                inDegree: 0,
                totalDegree: 0
            })
            SET n += node.properties
            RETURN count(*) AS created
            """;

    private static final String CREATE_RELATIONSHIPS_CYPHER = """
            UNWIND $relationships AS relation
            MATCH (source:Entity {nodeId: relation.sourceNodeId, graphId: $graphId})
            MATCH (target:Entity {nodeId: relation.targetNodeId, graphId: $graphId})
            CREATE (source)-[r:RELATES_TO {type: relation.type, graphId: $graphId}]->(target)
            RETURN count(r) AS created
            """;

    private final Driver neo4jDriver;

    public BatchWriteResult writeGraph(Integer graphId,
                                       List<ImportedNode> nodes,
                                       List<ImportedRelationship> relationships) {
        if (graphId == null) {
            throw new IllegalArgumentException("图谱ID不能为空");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("导入节点不能为空");
        }

        List<ImportedRelationship> safeRelationships = relationships == null ? List.of() : relationships;

        try (Session session = neo4jDriver.session();
             Transaction transaction = session.beginTransaction()) {
            long createdNodes = createNodes(transaction, graphId, nodes);
            long createdRelationships = createRelationships(transaction, graphId, safeRelationships);
            transaction.commit();
            return new BatchWriteResult(toIntExact(createdNodes, "节点数量"),
                    toIntExact(createdRelationships, "关系数量"));
        } catch (RuntimeException e) {
            log.error("Neo4j 批量导入失败 graphId={}: {}", graphId, e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("Neo4j 批量导入失败 graphId={}: {}", graphId, e.getMessage(), e);
            throw new RuntimeException("Neo4j 批量导入失败: " + e.getMessage(), e);
        }
    }

    private long createNodes(Transaction transaction, Integer graphId, List<ImportedNode> nodes) {
        long created = 0;
        for (int start = 0; start < nodes.size(); start += BATCH_SIZE) {
            List<ImportedNode> batch = nodes.subList(start, Math.min(start + BATCH_SIZE, nodes.size()));
            long batchCreated = runCountQuery(transaction, CREATE_NODES_CYPHER,
                    Map.of("graphId", graphId, "nodes", toNodeParams(batch)));
            verifyCreatedCount("节点", batch.size(), batchCreated);
            created += batchCreated;
        }
        return created;
    }

    private long createRelationships(Transaction transaction,
                                     Integer graphId,
                                     List<ImportedRelationship> relationships) {
        long created = 0;
        for (int start = 0; start < relationships.size(); start += BATCH_SIZE) {
            List<ImportedRelationship> batch = relationships.subList(start,
                    Math.min(start + BATCH_SIZE, relationships.size()));
            long batchCreated = runCountQuery(transaction, CREATE_RELATIONSHIPS_CYPHER,
                    Map.of("graphId", graphId, "relationships", toRelationshipParams(batch)));
            verifyCreatedCount("关系", batch.size(), batchCreated);
            created += batchCreated;
        }
        return created;
    }

    private long runCountQuery(Transaction transaction, String cypher, Map<String, Object> params) {
        Record record = transaction.run(cypher, params).single();
        return record.get("created").asLong();
    }

    private void verifyCreatedCount(String label, int expected, long actual) {
        if (actual != expected) {
            throw new IllegalStateException(label + "批量写入数量不一致，预期 " + expected + "，实际 " + actual);
        }
    }

    private List<Map<String, Object>> toNodeParams(List<ImportedNode> nodes) {
        List<Map<String, Object>> params = new ArrayList<>(nodes.size());
        for (ImportedNode node : nodes) {
            Map<String, Object> map = new HashMap<>();
            map.put("nodeId", node.nodeId());
            map.put("name", node.name());
            map.put("type", node.type());
            Map<String, Object> properties = new HashMap<>();
            if (node.description() != null) {
                properties.put("description", node.description());
            }
            map.put("properties", properties);
            params.add(map);
        }
        return params;
    }

    private List<Map<String, Object>> toRelationshipParams(List<ImportedRelationship> relationships) {
        List<Map<String, Object>> params = new ArrayList<>(relationships.size());
        for (ImportedRelationship relationship : relationships) {
            Map<String, Object> map = new HashMap<>();
            map.put("sourceNodeId", relationship.sourceNodeId());
            map.put("targetNodeId", relationship.targetNodeId());
            map.put("type", relationship.type());
            params.add(map);
        }
        return params;
    }

    private int toIntExact(long value, String label) {
        if (value > Integer.MAX_VALUE) {
            throw new IllegalStateException(label + "超过系统支持上限: " + value);
        }
        return (int) value;
    }

    public record ImportedNode(String nodeId, String name, String type, String description) {
    }

    public record ImportedRelationship(String sourceNodeId, String targetNodeId, String type) {
    }

    public record BatchWriteResult(int nodeCount, int relationCount) {
    }
}
