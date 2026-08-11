package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.NodeDto;
import com.sdu.kgplatform.dto.RelationshipBatchCreateResult;
import com.sdu.kgplatform.dto.RelationshipDto;
import com.sdu.kgplatform.security.CustomOAuth2User;
import com.sdu.kgplatform.security.CustomUserDetails;
import com.sdu.kgplatform.service.GraphService;
import com.sdu.kgplatform.service.NodeService;
import com.sdu.kgplatform.service.RelationshipService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph/{graphId}")
public class NodeController {

    private final GraphService graphService;
    private final NodeService nodeService;
    private final RelationshipService relationshipService;

    public NodeController(GraphService graphService,
                          NodeService nodeService,
                          RelationshipService relationshipService) {
        this.graphService = graphService;
        this.nodeService = nodeService;
        this.relationshipService = relationshipService;
    }

    @GetMapping("/nodes")
    public ResponseEntity<?> getNodes(@PathVariable Integer graphId) {
        if (!canView(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权访问该图谱"));
        }
        return ResponseEntity.ok(nodeService.getNodesByGraphId(graphId));
    }

    @PostMapping("/nodes")
    public ResponseEntity<?> createNode(@PathVariable Integer graphId, @Valid @RequestBody NodeDto dto) {
        if (!canEdit(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无编辑权限"));
        }

        try {
            NodeDto created = nodeService.createNode(graphId, dto);
            graphService.updateGraphStats(graphId);
            return ResponseEntity.ok(Map.of("success", true, "node", created, "count", 1));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/nodes/batch")
    public ResponseEntity<?> createNodes(@PathVariable Integer graphId, @RequestBody List<@Valid NodeDto> dtos) {
        if (!canEdit(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无编辑权限"));
        }
        if (dtos == null || dtos.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "未提供节点数据"));
        }

        try {
            List<NodeDto> created = nodeService.createNodes(graphId, dtos);
            graphService.updateGraphStats(graphId);
            return ResponseEntity.ok(Map.of("success", true, "nodes", created, "count", created.size()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/nodes/search")
    public ResponseEntity<?> searchNodes(@PathVariable Integer graphId,
                                         @RequestParam(defaultValue = "") String keyword) {
        if (!canView(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权访问该图谱"));
        }
        return ResponseEntity.ok(nodeService.searchNodes(graphId, keyword));
    }

    @GetMapping("/nodes/{nodeId}")
    public ResponseEntity<?> getNode(@PathVariable Integer graphId, @PathVariable String nodeId) {
        if (!canView(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权访问该图谱"));
        }
        try {
            NodeDto node = nodeService.getNodeById(graphId, nodeId);
            return ResponseEntity.ok(node);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/nodes/{nodeId}")
    public ResponseEntity<?> updateNode(@PathVariable Integer graphId,
                                        @PathVariable String nodeId,
                                        @Valid @RequestBody NodeDto dto) {
        if (!canEdit(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无编辑权限"));
        }

        try {
            NodeDto updated = nodeService.updateNode(graphId, nodeId, dto);
            graphService.updateGraphStats(graphId);
            return ResponseEntity.ok(Map.of("success", true, "node", updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/nodes/{nodeId}")
    public ResponseEntity<?> deleteNode(@PathVariable Integer graphId, @PathVariable String nodeId) {
        if (!canEdit(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无编辑权限"));
        }

        try {
            nodeService.deleteNode(graphId, nodeId);
            graphService.updateGraphStats(graphId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/nodes/batch")
    public ResponseEntity<?> deleteNodes(@PathVariable Integer graphId, @RequestBody List<String> nodeIds) {
        if (!canEdit(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无编辑权限"));
        }
        if (nodeIds == null || nodeIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "未提供节点ID"));
        }

        List<String> successIds = new java.util.ArrayList<>();
        List<Map<String, Object>> failedItems = new java.util.ArrayList<>();
        for (String nodeId : nodeIds) {
            try {
                nodeService.deleteNode(graphId, nodeId);
                successIds.add(nodeId);
            } catch (Exception ex) {
                Map<String, Object> failedItem = new java.util.LinkedHashMap<>();
                failedItem.put("nodeId", nodeId);
                failedItem.put("error", ex.getMessage());
                failedItems.add(failedItem);
            }
        }

        graphService.updateGraphStats(graphId);
        int successCount = successIds.size();
        int failedCount = failedItems.size();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", successCount,
                "successIds", successIds,
                "failedItems", failedItems,
                "failedCount", failedCount,
                "message", failedCount == 0
                        ? "成功删除 " + successCount + " 个节点"
                        : "成功删除 " + successCount + " 个节点，" + failedCount + " 个失败"));
    }

    @GetMapping("/nodes/{nodeId}/neighbors")
    public ResponseEntity<List<NodeDto>> getNeighbors(@PathVariable Integer graphId,
                                                      @PathVariable String nodeId,
                                                      @RequestParam(defaultValue = "both") String direction) {
        if (!canView(graphId)) {
            return ResponseEntity.status(403).build();
        }
        List<NodeDto> result;
        try {
            switch (direction.toLowerCase()) {
                case "out" -> result = nodeService.getOutgoingNeighbors(graphId, nodeId);
                case "in" -> result = nodeService.getIncomingNeighbors(graphId, nodeId);
                default -> {
                    List<NodeDto> outgoing = nodeService.getOutgoingNeighbors(graphId, nodeId);
                    List<NodeDto> incoming = nodeService.getIncomingNeighbors(graphId, nodeId);
                    java.util.LinkedHashMap<String, NodeDto> merged = new java.util.LinkedHashMap<>();
                    outgoing.forEach(node -> merged.put(node.getNodeId(), node));
                    incoming.forEach(node -> merged.put(node.getNodeId(), node));
                    result = List.copyOf(merged.values());
                }
            }
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).build();
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/relations")
    public ResponseEntity<?> getRelationships(@PathVariable Integer graphId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "100") int size,
                                              @RequestParam(required = false) String type,
                                              @RequestParam(required = false) String source,
                                              @RequestParam(required = false) String target) {
        if (!canView(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权访问该图谱"));
        }
        return ResponseEntity.ok(relationshipService.getRelationshipsByGraphId(graphId, page, size, type, source, target));
    }

    @PostMapping("/relations")
    public ResponseEntity<?> createRelationship(@PathVariable Integer graphId,
                                                @Valid @RequestBody RelationshipDto dto) {
        if (!canEdit(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无编辑权限"));
        }

        try {
            RelationshipDto created = relationshipService.createRelationship(graphId, dto);
            graphService.updateGraphStats(graphId);
            return ResponseEntity.ok(Map.of("success", true, "relation", created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/relations/batch")
    public ResponseEntity<?> createRelationships(@PathVariable Integer graphId,
                                                 @RequestBody List<@Valid RelationshipDto> dtos) {
        if (!canEdit(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无编辑权限"));
        }
        if (dtos == null || dtos.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No relationships provided"));
        }

        try {
            RelationshipBatchCreateResult result = relationshipService.createRelationshipsBatch(graphId, dtos);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        } catch (Exception ex) {
            return ResponseEntity.internalServerError().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/relations/{relationId}")
    public ResponseEntity<?> deleteRelationship(@PathVariable Integer graphId, @PathVariable String relationId) {
        if (!canEdit(graphId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无编辑权限"));
        }

        try {
            relationshipService.deleteRelationshipByRelationId(graphId, relationId);
            graphService.updateGraphStats(graphId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private boolean canEdit(Integer graphId) {
        Integer userId = getCurrentUserId();
        return userId != null && graphService.isGraphOwner(graphId, userId);
    }

    private boolean canView(Integer graphId) {
        return graphService.canViewGraph(graphId, getCurrentUserId());
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }

        Object principal = auth.getPrincipal();
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        if (principal instanceof CustomOAuth2User oAuth2User) {
            return oAuth2User.getUserId();
        }
        return null;
    }
}
