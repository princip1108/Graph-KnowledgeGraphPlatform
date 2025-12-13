package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.NodeDto;
import com.sdu.kgplatform.dto.RelationshipDto;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.service.GraphService;
import com.sdu.kgplatform.service.NodeService;
import com.sdu.kgplatform.service.RelationshipService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 节点与关系控制器
 */
@RestController
@RequestMapping("/api/graph/{graphId}")
public class NodeController {

    private final NodeService nodeService;
    private final RelationshipService relationshipService;
    private final GraphService graphService;
    private final UserRepository userRepository;

    public NodeController(NodeService nodeService,
                          RelationshipService relationshipService,
                          GraphService graphService,
                          UserRepository userRepository) {
        this.nodeService = nodeService;
        this.relationshipService = relationshipService;
        this.graphService = graphService;
        this.userRepository = userRepository;
    }

    // ==================== 节点接口 ====================

    /**
     * 获取图谱的所有节点
     * GET /api/graph/{graphId}/nodes
     */
    @GetMapping("/nodes")
    public ResponseEntity<?> getNodes(@PathVariable Integer graphId) {
        try {
            List<NodeDto> nodes = nodeService.getNodesByGraphId(graphId);
            return ResponseEntity.ok(Map.of(
                "nodes", nodes,
                "count", nodes.size()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取单个节点
     * GET /api/graph/{graphId}/nodes/{nodeId}
     */
    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<?> getNode(@PathVariable Integer graphId,
                                     @PathVariable String nodeId) {
        try {
            NodeDto node = nodeService.getNodeById(nodeId);
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 搜索节点
     * GET /api/graph/{graphId}/nodes/search?keyword=xxx
     */
    @GetMapping("/nodes/search")
    public ResponseEntity<?> searchNodes(@PathVariable Integer graphId,
                                         @RequestParam String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "搜索关键词不能为空"));
        }
        List<NodeDto> nodes = nodeService.searchNodes(graphId, keyword.trim());
        return ResponseEntity.ok(nodes);
    }

    /**
     * 根据类型获取节点
     * GET /api/graph/{graphId}/nodes/type/{type}
     */
    @GetMapping("/nodes/type/{type}")
    public ResponseEntity<?> getNodesByType(@PathVariable Integer graphId,
                                            @PathVariable String type) {
        List<NodeDto> nodes = nodeService.getNodesByType(graphId, type);
        return ResponseEntity.ok(nodes);
    }

    /**
     * 获取节点类型列表
     * GET /api/graph/{graphId}/node-types
     */
    @GetMapping("/node-types")
    public ResponseEntity<?> getNodeTypes(@PathVariable Integer graphId) {
        List<String> types = nodeService.getNodeTypes(graphId);
        return ResponseEntity.ok(types);
    }

    /**
     * 获取节点的邻居
     * GET /api/graph/{graphId}/nodes/{nodeId}/neighbors
     */
    @GetMapping("/nodes/{nodeId}/neighbors")
    public ResponseEntity<?> getNeighbors(@PathVariable Integer graphId,
                                          @PathVariable String nodeId,
                                          @RequestParam(defaultValue = "all") String direction) {
        try {
            List<NodeDto> neighbors;
            switch (direction.toLowerCase()) {
                case "out" -> neighbors = nodeService.getOutgoingNeighbors(nodeId);
                case "in" -> neighbors = nodeService.getIncomingNeighbors(nodeId);
                default -> {
                    List<NodeDto> outgoing = nodeService.getOutgoingNeighbors(nodeId);
                    List<NodeDto> incoming = nodeService.getIncomingNeighbors(nodeId);
                    outgoing.addAll(incoming);
                    neighbors = outgoing.stream().distinct().toList();
                }
            }
            return ResponseEntity.ok(neighbors);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 创建节点
     * POST /api/graph/{graphId}/nodes
     */
    @PostMapping("/nodes")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> createNode(@PathVariable Integer graphId,
                                        @Valid @RequestBody NodeDto dto) {
        if (!checkGraphOwnership(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此图谱"));
        }

        try {
            NodeDto created = nodeService.createNode(graphId, dto);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "节点创建成功",
                "data", created
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 批量创建节点
     * POST /api/graph/{graphId}/nodes/batch
     */
    @PostMapping("/nodes/batch")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> createNodes(@PathVariable Integer graphId,
                                         @RequestBody List<NodeDto> dtos) {
        if (!checkGraphOwnership(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此图谱"));
        }

        try {
            List<NodeDto> created = nodeService.createNodes(graphId, dtos);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "批量创建成功",
                "count", created.size(),
                "data", created
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 更新节点
     * PUT /api/graph/{graphId}/nodes/{nodeId}
     */
    @PutMapping("/nodes/{nodeId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> updateNode(@PathVariable Integer graphId,
                                        @PathVariable String nodeId,
                                        @Valid @RequestBody NodeDto dto) {
        if (!checkGraphOwnership(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此图谱"));
        }

        try {
            NodeDto updated = nodeService.updateNode(nodeId, dto);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "节点更新成功",
                "data", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 删除节点
     * DELETE /api/graph/{graphId}/nodes/{nodeId}
     */
    @DeleteMapping("/nodes/{nodeId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> deleteNode(@PathVariable Integer graphId,
                                        @PathVariable String nodeId) {
        if (!checkGraphOwnership(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此图谱"));
        }

        try {
            nodeService.deleteNode(nodeId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "节点已删除"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 批量删除节点
     * DELETE /api/graph/{graphId}/nodes/batch
     */
    @DeleteMapping("/nodes/batch")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> deleteNodes(@PathVariable Integer graphId,
                                         @RequestBody List<String> nodeIds) {
        if (!checkGraphOwnership(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此图谱"));
        }

        try {
            int deleted = 0;
            for (String nodeId : nodeIds) {
                try {
                    nodeService.deleteNode(nodeId);
                    deleted++;
                } catch (Exception e) {
                    // 忽略单个节点删除失败
                }
            }
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "已删除 " + deleted + " 个节点",
                "deletedCount", deleted
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 批量删除关系
     * DELETE /api/graph/{graphId}/relations/batch
     */
    @DeleteMapping("/relations/batch")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> deleteRelations(@PathVariable Integer graphId,
                                             @RequestBody List<String> relationIds) {
        if (!checkGraphOwnership(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此图谱"));
        }

        try {
            int deleted = 0;
            for (String relationId : relationIds) {
                try {
                    relationshipService.deleteRelationshipByElementId(relationId);
                    deleted++;
                } catch (Exception e) {
                    // 忽略单个关系删除失败
                }
            }
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "已删除 " + deleted + " 个关系",
                "deletedCount", deleted
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 关系接口 ====================

    /**
     * 获取图谱的所有关系
     * GET /api/graph/{graphId}/relations
     */
    @GetMapping("/relations")
    public ResponseEntity<?> getRelations(@PathVariable Integer graphId) {
        try {
            List<RelationshipDto> relations = relationshipService.getRelationshipsByGraphId(graphId);
            return ResponseEntity.ok(Map.of(
                "relations", relations,
                "count", relations.size()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 根据类型获取关系
     * GET /api/graph/{graphId}/relations/type/{type}
     */
    @GetMapping("/relations/type/{type}")
    public ResponseEntity<?> getRelationsByType(@PathVariable Integer graphId,
                                                @PathVariable String type) {
        List<RelationshipDto> relations = relationshipService.getRelationshipsByType(graphId, type);
        return ResponseEntity.ok(relations);
    }

    /**
     * 获取关系类型列表
     * GET /api/graph/{graphId}/relation-types
     */
    @GetMapping("/relation-types")
    public ResponseEntity<?> getRelationTypes(@PathVariable Integer graphId) {
        List<String> types = relationshipService.getRelationshipTypes(graphId);
        return ResponseEntity.ok(types);
    }

    /**
     * 获取关系类型统计
     * GET /api/graph/{graphId}/relation-stats
     */
    @GetMapping("/relation-stats")
    public ResponseEntity<?> getRelationStats(@PathVariable Integer graphId) {
        Map<String, Long> stats = relationshipService.getRelationshipTypeStats(graphId);
        return ResponseEntity.ok(stats);
    }

    /**
     * 创建关系
     * POST /api/graph/{graphId}/relations
     */
    @PostMapping("/relations")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> createRelation(@PathVariable Integer graphId,
                                            @Valid @RequestBody RelationshipDto dto) {
        if (!checkGraphOwnership(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此图谱"));
        }

        try {
            RelationshipDto created = relationshipService.createRelationship(graphId, dto);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "关系创建成功",
                "data", created
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 批量创建关系
     * POST /api/graph/{graphId}/relations/batch
     */
    @PostMapping("/relations/batch")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> createRelations(@PathVariable Integer graphId,
                                             @RequestBody List<RelationshipDto> dtos) {
        if (!checkGraphOwnership(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此图谱"));
        }

        try {
            List<RelationshipDto> created = relationshipService.createRelationships(graphId, dtos);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "批量创建成功",
                "count", created.size(),
                "data", created
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 删除关系
     * DELETE /api/graph/{graphId}/relations/{relationId}
     */
    @DeleteMapping("/relations/{relationId}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> deleteRelation(@PathVariable Integer graphId,
                                            @PathVariable Long relationId) {
        if (!checkGraphOwnership(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权操作此图谱"));
        }

        try {
            relationshipService.deleteRelationship(relationId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "关系已删除"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 辅助方法 ====================

    private boolean checkGraphOwnership(Integer graphId) {
        Integer userId = getCurrentUserId();
        if (userId == null) return false;
        return graphService.isGraphOwner(graphId, userId);
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }

        String account = auth.getName();
        User user = null;

        if (account.contains("@")) {
            user = userRepository.findByEmail(account).orElse(null);
        }
        if (user == null) {
            user = userRepository.findByPhone(account).orElse(null);
        }

        return user != null ? user.getUserId() : null;
    }
}
