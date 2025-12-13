package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.GraphCreateDto;
import com.sdu.kgplatform.dto.GraphDetailDto;
import com.sdu.kgplatform.dto.GraphListDto;
import com.sdu.kgplatform.dto.GraphUpdateDto;
import com.sdu.kgplatform.entity.Role;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.service.GraphService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 知识图谱控制器
 */
@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private final GraphService graphService;
    private final UserRepository userRepository;

    public GraphController(GraphService graphService, UserRepository userRepository) {
        this.graphService = graphService;
        this.userRepository = userRepository;
    }

    // ==================== 创建图谱 ====================

    /**
     * 创建新图谱
     * POST /api/graph
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> createGraph(@Valid @RequestBody GraphCreateDto dto) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            GraphDetailDto created = graphService.createGraph(userId, dto);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "图谱创建成功",
                "data", created
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 查询图谱 ====================

    /**
     * 获取图谱详情
     * GET /api/graph/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getGraphById(@PathVariable("id") Integer graphId,
                                          @RequestParam(value = "incrementView", defaultValue = "true") boolean incrementView) {
        try {
            GraphDetailDto graph = graphService.getGraphById(graphId);
            
            // 增加浏览量
            if (incrementView) {
                graphService.incrementViewCount(graphId);
            }
            
            return ResponseEntity.ok(graph);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 根据分享链接获取图谱
     * GET /api/graph/share/{shareLink}
     */
    @GetMapping("/share/{shareLink}")
    public ResponseEntity<?> getGraphByShareLink(@PathVariable String shareLink) {
        try {
            GraphDetailDto graph = graphService.getGraphByShareLink(shareLink);
            graphService.incrementViewCount(graph.getGraphId());
            return ResponseEntity.ok(graph);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取当前用户的图谱列表
     * GET /api/graph/my
     */
    @GetMapping("/my")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> getMyGraphs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String status) {
        
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            Page<GraphListDto> graphs;
            if (status != null && !status.isEmpty()) {
                graphs = graphService.getUserGraphsByStatus(userId, status, page, size);
            } else {
                graphs = graphService.getUserGraphs(userId, page, size, sortBy);
            }
            return ResponseEntity.ok(Map.of(
                "content", graphs.getContent(),
                "totalElements", graphs.getTotalElements(),
                "totalPages", graphs.getTotalPages(),
                "currentPage", graphs.getNumber(),
                "size", graphs.getSize()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取指定用户的公开图谱
     * GET /api/graph/user/{userId}
     */
    @GetMapping("/user/{userId}")
    public ResponseEntity<?> getUserPublicGraphs(
            @PathVariable Integer userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        try {
            Page<GraphListDto> graphs = graphService.getUserGraphsByStatus(userId, "PUBLISHED", page, size);
            return ResponseEntity.ok(Map.of(
                "content", graphs.getContent(),
                "totalElements", graphs.getTotalElements(),
                "totalPages", graphs.getTotalPages(),
                "currentPage", graphs.getNumber()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取公开图谱列表（广场）
     * GET /api/graph/public
     */
    @GetMapping("/public")
    public ResponseEntity<?> getPublicGraphs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String sortBy) {
        
        Page<GraphListDto> graphs = graphService.getPublicGraphs(page, size, sortBy);
        return ResponseEntity.ok(Map.of(
            "content", graphs.getContent(),
            "totalElements", graphs.getTotalElements(),
            "totalPages", graphs.getTotalPages(),
            "currentPage", graphs.getNumber()
        ));
    }

    /**
     * 搜索公开图谱
     * GET /api/graph/search
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchGraphs(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        
        if (keyword == null || keyword.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "搜索关键词不能为空"));
        }

        Page<GraphListDto> graphs = graphService.searchPublicGraphs(keyword.trim(), page, size);
        return ResponseEntity.ok(Map.of(
            "content", graphs.getContent(),
            "totalElements", graphs.getTotalElements(),
            "totalPages", graphs.getTotalPages(),
            "currentPage", graphs.getNumber(),
            "keyword", keyword
        ));
    }

    /**
     * 获取热门图谱
     * GET /api/graph/popular
     */
    @GetMapping("/popular")
    public ResponseEntity<?> getPopularGraphs(@RequestParam(defaultValue = "10") int limit) {
        List<GraphListDto> graphs = graphService.getPopularGraphs(Math.min(limit, 50));
        return ResponseEntity.ok(graphs);
    }

    // ==================== 更新图谱 ====================

    /**
     * 更新图谱信息
     * PUT /api/graph/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> updateGraph(
            @PathVariable("id") Integer graphId,
            @Valid @RequestBody GraphUpdateDto dto) {
        
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            GraphDetailDto updated = graphService.updateGraph(graphId, userId, dto);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "图谱更新成功",
                "data", updated
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 更新图谱状态（发布/取消发布/设为私有）
     * PATCH /api/graph/{id}/status
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> updateGraphStatus(
            @PathVariable("id") Integer graphId,
            @RequestBody Map<String, String> request) {
        
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        String status = request.get("status");
        if (status == null || status.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "状态不能为空"));
        }

        try {
            graphService.updateGraphStatus(graphId, userId, status);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "状态更新成功",
                "status", status.toUpperCase()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 删除图谱 ====================

    /**
     * 删除图谱
     * DELETE /api/graph/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> deleteGraph(@PathVariable("id") Integer graphId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            graphService.deleteGraph(graphId, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "图谱已删除"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 管理员删除图谱
     * DELETE /api/graph/admin/{id}
     */
    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminDeleteGraph(@PathVariable("id") Integer graphId) {
        try {
            graphService.adminDeleteGraph(graphId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "图谱已删除（管理员操作）"
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 批量操作接口 ====================

    /**
     * 批量上线图谱
     * POST /api/graph/batch/publish
     */
    @PostMapping("/batch/publish")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> batchPublish(@RequestBody Map<String, List<Integer>> request) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        List<Integer> graphIds = request.get("graphIds");
        if (graphIds == null || graphIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择要上线的图谱"));
        }

        try {
            int successCount = graphService.batchUpdateStatus(graphIds, userId, "PUBLISHED");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "成功上线 " + successCount + " 个图谱",
                "successCount", successCount,
                "totalCount", graphIds.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 批量下线图谱（设为草稿状态）
     * POST /api/graph/batch/offline
     */
    @PostMapping("/batch/offline")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> batchOffline(@RequestBody Map<String, List<Integer>> request) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        List<Integer> graphIds = request.get("graphIds");
        if (graphIds == null || graphIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择要下线的图谱"));
        }

        try {
            int successCount = graphService.batchUpdateStatus(graphIds, userId, "DRAFT");
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "成功下线 " + successCount + " 个图谱",
                "successCount", successCount,
                "totalCount", graphIds.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 批量删除图谱
     * POST /api/graph/batch/delete
     */
    @PostMapping("/batch/delete")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> batchDelete(@RequestBody Map<String, List<Integer>> request) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        List<Integer> graphIds = request.get("graphIds");
        if (graphIds == null || graphIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择要删除的图谱"));
        }

        try {
            int successCount = graphService.batchDeleteGraphs(graphIds, userId);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "成功删除 " + successCount + " 个图谱",
                "successCount", successCount,
                "totalCount", graphIds.size()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 统计接口 ====================

    /**
     * 获取当前用户的图谱统计
     * GET /api/graph/my/stats
     */
    @GetMapping("/my/stats")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> getMyGraphStats() {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        long count = graphService.countUserGraphs(userId);
        return ResponseEntity.ok(Map.of(
            "totalGraphs", count
        ));
    }

    /**
     * 检查当前用户是否可以编辑图谱
     * GET /api/graph/{id}/can-edit
     */
    @GetMapping("/{id}/can-edit")
    public ResponseEntity<?> canEditGraph(@PathVariable("id") Integer graphId) {
        Integer userId = getCurrentUserId();
        
        // 未登录用户没有编辑权限
        if (userId == null) {
            return ResponseEntity.ok(Map.of("canEdit", false));
        }
        
        try {
            // 检查是否是图谱创建者
            boolean isOwner = graphService.isGraphOwner(graphId, userId);
            if (isOwner) {
                return ResponseEntity.ok(Map.of("canEdit", true, "reason", "owner"));
            }
            
            // 检查是否是管理员
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getRole() == Role.ADMIN) {
                return ResponseEntity.ok(Map.of("canEdit", true, "reason", "admin"));
            }
            
            return ResponseEntity.ok(Map.of("canEdit", false));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("canEdit", false));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当前登录用户ID
     */
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
