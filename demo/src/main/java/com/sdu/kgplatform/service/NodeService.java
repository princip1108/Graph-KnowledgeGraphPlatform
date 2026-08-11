package com.sdu.kgplatform.service;

import com.sdu.kgplatform.dto.NodeDto;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import com.sdu.kgplatform.entity.NodeEntity;
import com.sdu.kgplatform.repository.KnowledgeGraphRepository;
import com.sdu.kgplatform.repository.NodeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 节点服务层
 */
@Service
public class NodeService {

    private static final Logger log = LoggerFactory.getLogger(NodeService.class);

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
        return createNode(graphId, dto, false);
    }

    @Transactional("neo4jTransactionManager")
    public NodeDto createNode(Integer graphId, NodeDto dto, boolean skipStats) {
        validateGraphExists(graphId);
        String nodeName = normalizeNodeName(dto.getName());

        if (nodeRepository.findByGraphIdAndName(graphId, nodeName).isPresent()) {
            throw new IllegalArgumentException("该图谱中已存在同名节点: " + nodeName);
        }

        NodeEntity node = buildNode(graphId, dto, nodeName);

        try {
            log.debug("Creating node - nodeId={}, name={}", node.getNodeId(), node.getName());
            NodeEntity saved = nodeRepository.save(node);
            log.debug("Saved node - nodeId={}, id={}, name={}", saved.getNodeId(), saved.getId(), saved.getName());
            if (!skipStats) {
                updateGraphNodeCount(graphId);
            }
            NodeDto result = convertToDto(saved);
            log.debug("Returning DTO - nodeId={}", result.getNodeId());
            return result;
        } catch (Exception e) {
            log.error("节点保存到Neo4j失败: {}", e.getMessage());
            throw new RuntimeException("Neo4j 连接失败，请确保 Neo4j 数据库正在运行。错误: " + e.getMessage(), e);
        }
    }

    /**
     * 批量创建节点
     */
    @Transactional("neo4jTransactionManager")
    public List<NodeDto> createNodes(Integer graphId, List<NodeDto> dtos) {
        validateGraphExists(graphId);
        if (dtos == null || dtos.isEmpty()) {
            throw new IllegalArgumentException("未提供节点数据");
        }

        List<String> nodeNames = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();
        for (NodeDto dto : dtos) {
            String nodeName = normalizeNodeName(dto.getName());
            if (!seenNames.add(nodeName)) {
                throw new IllegalArgumentException("批量节点中存在重复名称: " + nodeName);
            }
            nodeNames.add(nodeName);
        }

        List<NodeEntity> existingNodes = nodeRepository.findByGraphIdAndNameIn(graphId, nodeNames);
        if (!existingNodes.isEmpty()) {
            throw new IllegalArgumentException("该图谱中已存在同名节点: " + existingNodes.get(0).getName());
        }

        List<NodeEntity> nodes = new ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            nodes.add(buildNode(graphId, dtos.get(i), nodeNames.get(i)));
        }

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

    @Transactional(value = "neo4jTransactionManager", readOnly = true)
    public List<NodeDto> getExportNodesByGraphId(Integer graphId, long skip, long limit) {
        return nodeRepository.findExportNodesByGraphId(graphId, skip, limit);
    }

    public NodeDto getNodeById(Integer graphId, String nodeId) {
        return convertToDto(requireNodeInGraph(graphId, nodeId));
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

    public List<NodeDto> getOutgoingNeighbors(Integer graphId, String nodeId) {
        requireNodeInGraph(graphId, nodeId);
        List<NodeEntity> neighbors = nodeRepository.findOutgoingNeighbors(nodeId);
        return neighbors.stream()
                .filter(node -> Objects.equals(node.getGraphId(), graphId))
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    public List<NodeDto> getIncomingNeighbors(Integer graphId, String nodeId) {
        requireNodeInGraph(graphId, nodeId);
        List<NodeEntity> neighbors = nodeRepository.findIncomingNeighbors(nodeId);
        return neighbors.stream()
                .filter(node -> Objects.equals(node.getGraphId(), graphId))
                .map(this::convertToDto)
                .collect(Collectors.toList());
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

    @Transactional("neo4jTransactionManager")
    public NodeDto updateNode(Integer graphId, String nodeId, NodeDto dto) {
        NodeEntity node = requireNodeInGraph(graphId, nodeId);

        if (dto.getName() != null && !dto.getName().trim().isEmpty()) {
            if (!node.getName().equals(dto.getName())) {
                nodeRepository.findByGraphIdAndName(graphId, dto.getName())
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

        NodeEntity saved = nodeRepository.save(node);
        return convertToDto(saved);
    }

    // ==================== 删除节点 ====================

    @Transactional("neo4jTransactionManager")
    public void deleteNode(Integer graphId, String nodeId) {
        NodeEntity node = requireNodeInGraph(graphId, nodeId);
        nodeRepository.detachDeleteByGraphIdAndNodeId(graphId, node.getNodeId());
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

    private String normalizeNodeName(String name) {
        String normalized = name == null ? null : name.trim();
        if (normalized == null || normalized.isEmpty()) {
            throw new IllegalArgumentException("节点名称不能为空");
        }
        return normalized;
    }

    private NodeEntity buildNode(Integer graphId, NodeDto dto, String nodeName) {
        NodeEntity node = new NodeEntity();
        node.setNodeId(java.util.UUID.randomUUID().toString());
        node.setGraphId(graphId);
        node.setName(nodeName);
        node.setType(dto.getType());
        node.setDescription(dto.getDescription());
        node.setOutDegree(0);
        node.setInDegree(0);
        node.setTotalDegree(0);
        return node;
    }

    private NodeEntity requireNodeInGraph(Integer graphId, String nodeId) {
        NodeEntity node = nodeRepository.findByNodeId(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("节点不存在: " + nodeId));
        if (!Objects.equals(node.getGraphId(), graphId)) {
            throw new IllegalArgumentException("节点不属于当前图谱");
        }
        return node;
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
                .outDegree(node.getOutDegree())
                .inDegree(node.getInDegree())
                .totalDegree(node.getTotalDegree())
                .build();
    }
}
