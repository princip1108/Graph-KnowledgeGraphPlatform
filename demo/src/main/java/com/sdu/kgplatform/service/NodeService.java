package com.sdu.kgplatform.service;

import com.sdu.kgplatform.dto.NodeDto;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import com.sdu.kgplatform.entity.NodeEntity;
import com.sdu.kgplatform.repository.KnowledgeGraphRepository;
import com.sdu.kgplatform.repository.NodeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 节点服务层
 */
@Service
public class NodeService {

    private final NodeRepository nodeRepository;
    private final KnowledgeGraphRepository graphRepository;

    public NodeService(NodeRepository nodeRepository, KnowledgeGraphRepository graphRepository) {
        this.nodeRepository = nodeRepository;
        this.graphRepository = graphRepository;
    }

    // ==================== 创建节点 ====================

    /**
     * 创建单个节点
     */
    @Transactional("neo4jTransactionManager")
    public NodeDto createNode(Integer graphId, NodeDto dto) {
        validateGraphExists(graphId);

        // 检查同名节点（带异常处理，防止Neo4j连接问题）
        try {
        if (nodeRepository.findByGraphIdAndName(graphId, dto.getName()).isPresent()) {
            throw new IllegalArgumentException("该图谱中已存在同名节点: " + dto.getName());
            }
        } catch (IllegalArgumentException e) {
            throw e; // 重新抛出业务异常
        } catch (Exception e) {
            // Neo4j 连接问题，记录日志但继续创建节点
            System.err.println("警告: Neo4j 查询失败，跳过重复检查: " + e.getMessage());
        }

        NodeEntity node = new NodeEntity();
        // 生成业务 ID (nodeId)
        node.setNodeId(java.util.UUID.randomUUID().toString());
        node.setGraphId(graphId);
        node.setName(dto.getName());
        node.setType(dto.getType());
        node.setDescription(dto.getDescription());
        node.setImportance(dto.getImportance() != null ? dto.getImportance() : 1);
        node.setOutDegree(0);
        node.setInDegree(0);
        node.setTotalDegree(0);

        try {
            System.out.println("DEBUG: Creating node - nodeId=" + node.getNodeId() + ", name=" + node.getName());
        NodeEntity saved = nodeRepository.save(node);
            System.out.println("DEBUG: Saved node - nodeId=" + saved.getNodeId() + ", id=" + saved.getId() + ", name=" + saved.getName());
        updateGraphNodeCount(graphId);
            NodeDto result = convertToDto(saved);
            System.out.println("DEBUG: Returning DTO - nodeId=" + result.getNodeId());
            return result;
        } catch (Exception e) {
            System.err.println("节点保存到Neo4j失败: " + e.getMessage());
            throw new RuntimeException("Neo4j 连接失败，请确保 Neo4j 数据库正在运行。错误: " + e.getMessage(), e);
        }
    }

    /**
     * 批量创建节点
     */
    @Transactional("neo4jTransactionManager")
    public List<NodeDto> createNodes(Integer graphId, List<NodeDto> dtos) {
        validateGraphExists(graphId);

        List<NodeEntity> nodes = dtos.stream().map(dto -> {
            NodeEntity node = new NodeEntity();
            // 生成业务 ID (nodeId)
            node.setNodeId(java.util.UUID.randomUUID().toString());
            node.setGraphId(graphId);
            node.setName(dto.getName());
            node.setType(dto.getType());
            node.setDescription(dto.getDescription());
            node.setImportance(dto.getImportance() != null ? dto.getImportance() : 1);
            node.setOutDegree(0);
            node.setInDegree(0);
            node.setTotalDegree(0);
            return node;
        }).collect(Collectors.toList());

        List<NodeEntity> saved = nodeRepository.saveAll(nodes);
        updateGraphNodeCount(graphId);
        return saved.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    // ==================== 查询节点 ====================

    /**
     * 获取图谱的所有节点
     */
    public List<NodeDto> getNodesByGraphId(Integer graphId) {
        List<NodeEntity> nodes = nodeRepository.findByGraphId(graphId);
        return nodes.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * 根据ID获取节点
     */
    public NodeDto getNodeById(String nodeId) {
        NodeEntity node = nodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeId));
        return convertToDto(node);
    }

    /**
     * 根据名称查找节点
     */
    public NodeDto getNodeByName(Integer graphId, String name) {
        NodeEntity node = nodeRepository.findByGraphIdAndName(graphId, name)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + name));
        return convertToDto(node);
    }

    /**
     * 根据类型查找节点
     */
    public List<NodeDto> getNodesByType(Integer graphId, String type) {
        List<NodeEntity> nodes = nodeRepository.findByGraphIdAndType(graphId, type);
        return nodes.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * 模糊搜索节点
     */
    public List<NodeDto> searchNodes(Integer graphId, String keyword) {
        List<NodeEntity> nodes = nodeRepository.findByGraphIdAndNameContaining(graphId, keyword);
        return nodes.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * 获取节点的出边邻居
     */
    public List<NodeDto> getOutgoingNeighbors(String nodeId) {
        List<NodeEntity> neighbors = nodeRepository.findOutgoingNeighbors(nodeId);
        return neighbors.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * 获取节点的入边邻居
     */
    public List<NodeDto> getIncomingNeighbors(String nodeId) {
        List<NodeEntity> neighbors = nodeRepository.findIncomingNeighbors(nodeId);
        return neighbors.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    /**
     * 获取图谱中所有节点类型
     */
    public List<String> getNodeTypes(Integer graphId) {
        List<NodeEntity> nodes = nodeRepository.findByGraphId(graphId);
        return nodes.stream()
                .map(NodeEntity::getType)
                .filter(type -> type != null && !type.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    // ==================== 更新节点 ====================

    /**
     * 更新节点
     */
    @Transactional("neo4jTransactionManager")
    public NodeDto updateNode(String nodeId, NodeDto dto) {
        NodeEntity node = nodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeId));

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            // 检查新名称是否与其他节点冲突
            if (!node.getName().equals(dto.getName())) {
                nodeRepository.findByGraphIdAndName(node.getGraphId(), dto.getName())
                        .ifPresent(existing -> {
                            throw new IllegalArgumentException("该图谱中已存在同名节点: " + dto.getName());
                        });
            }
            node.setName(dto.getName().trim());
        }

        if (dto.getType() != null) {
            node.setType(dto.getType());
        }

        if (dto.getDescription() != null) {
            node.setDescription(dto.getDescription());
        }

        if (dto.getImportance() != null) {
            node.setImportance(dto.getImportance());
        }

        NodeEntity saved = nodeRepository.save(node);
        return convertToDto(saved);
    }

    // ==================== 删除节点 ====================

    /**
     * 删除单个节点
     */
    @Transactional("neo4jTransactionManager")
    public void deleteNode(String nodeId) {
        NodeEntity node = nodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeId));
        Integer graphId = node.getGraphId();
        nodeRepository.delete(node);
        updateGraphNodeCount(graphId);
    }

    /**
     * 删除图谱的所有节点
     */
    @Transactional("neo4jTransactionManager")
    public void deleteNodesByGraphId(Integer graphId) {
        nodeRepository.deleteByGraphId(graphId);
        updateGraphNodeCount(graphId);
    }

    // ==================== 统计方法 ====================

    /**
     * 统计图谱节点数量
     */
    public long countNodesByGraphId(Integer graphId) {
        return nodeRepository.countByGraphId(graphId);
    }

    // ==================== 私有辅助方法 ====================

    private void validateGraphExists(Integer graphId) {
        if (!graphRepository.existsById(graphId)) {
            throw new IllegalArgumentException("图谱不存在: " + graphId);
        }
    }

    private void updateGraphNodeCount(Integer graphId) {
        graphRepository.findById(graphId).ifPresent(graph -> {
            long count = nodeRepository.countByGraphId(graphId);
            graph.setNodeCount((int) count);
            graph.setLastModified(LocalDateTime.now());
            graphRepository.save(graph);
        });
    }

    private NodeDto convertToDto(NodeEntity node) {
        return NodeDto.builder()
                .nodeId(node.getNodeId())
                .name(node.getName())
                .type(node.getType())
                .description(node.getDescription())
                .importance(node.getImportance())
                .outDegree(node.getOutDegree())
                .inDegree(node.getInDegree())
                .totalDegree(node.getTotalDegree())
                .build();
    }
}
