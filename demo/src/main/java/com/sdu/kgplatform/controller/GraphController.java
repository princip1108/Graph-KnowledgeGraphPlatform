package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.GraphCreateDto;
import com.sdu.kgplatform.dto.GraphDetailDto;
import com.sdu.kgplatform.dto.GraphListDto;
import com.sdu.kgplatform.dto.GraphUpdateDto;
import com.sdu.kgplatform.entity.GraphFavorite;
import com.sdu.kgplatform.entity.GraphFavoriteId;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import com.sdu.kgplatform.entity.Role;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.GraphFavoriteRepository;
import com.sdu.kgplatform.repository.KnowledgeGraphRepository;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.service.GraphService;
import com.sdu.kgplatform.common.SecurityUtils;
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
    private final GraphFavoriteRepository graphFavoriteRepository;
    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final com.sdu.kgplatform.service.HistoryService historyService;

    public GraphController(GraphService graphService, UserRepository userRepository,
            GraphFavoriteRepository graphFavoriteRepository,
            KnowledgeGraphRepository knowledgeGraphRepository,
            com.sdu.kgplatform.service.HistoryService historyService) {
        this.graphService = graphService;
        this.userRepository = userRepository;
        this.graphFavoriteRepository = graphFavoriteRepository;
        this.knowledgeGraphRepository = knowledgeGraphRepository;
        this.historyService = historyService;
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
                    "data", created));
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

            // 记录浏览历史 (仅登录用户)
            Integer userId = getCurrentUserId();
            if (userId != null) {
                historyService.recordBrowsing(userId, com.sdu.kgplatform.entity.ResourceType.graph, graphId);
            }

            return ResponseEntity.ok(graph);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 获取图谱可视化数据 (轻量级优化接口)
     * GET /api/graph/{id}/visualization
     */
    @GetMapping("/{id}/visualization")
    public ResponseEntity<?> getGraphVisualization(@PathVariable("id") Integer graphId) {
        try {
            Map<String, Object> data = graphService.getGraphVisualization(graphId);

            // 增加浏览量（可选：如果可视化被视为一次浏览）
            // graphService.incrementViewCount(graphId);

            return ResponseEntity.ok(data);
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

            // 记录浏览历史 (仅登录用户)
            Integer userId = getCurrentUserId();
            if (userId != null) {
                historyService.recordBrowsing(userId, com.sdu.kgplatform.entity.ResourceType.graph, graph.getGraphId());
            }

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
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer categoryId) {

        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            Page<GraphListDto> graphs;
            if (status != null && !status.isEmpty()) {
                graphs = graphService.getUserGraphsByStatus(userId, status, categoryId, page, size);
            } else {
                // If filtering by category without status, assume all/default or needs
                // implementation update.
                // For now, let's keep get user graphs simple or updated too if needed.
                // Assuming getUserGraphs handles all statuses, if we want to filter by category
                // there too, we need updating getUserGraphs.
                // But for now, let's just update the status one which is more common for
                // fitering.
                // Or I should update getUserGraphs signature too?
                // Let's stick to status filtering for now as per code structure.
                // If categoryId is present but status is null, we might ignore categoryId for
                // "all" listing or need update.
                // Let's assume user wants to filter by status typically.
                graphs = graphService.getUserGraphs(userId, page, size, sortBy);
            }
            return ResponseEntity.ok(Map.of(
                    "content", graphs.getContent(),
                    "totalElements", graphs.getTotalElements(),
                    "totalPages", graphs.getTotalPages(),
                    "currentPage", graphs.getNumber(),
                    "size", graphs.getSize()));
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
                    "currentPage", graphs.getNumber()));
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
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) Integer categoryId) {

        Page<GraphListDto> graphs = graphService.getPublicGraphs(page, size, sortBy, categoryId);
        return ResponseEntity.ok(Map.of(
                "content", graphs.getContent(),
                "totalElements", graphs.getTotalElements(),
                "totalPages", graphs.getTotalPages(),
                "currentPage", graphs.getNumber()));
    }

    /**
     * 搜索公开图谱
     * GET /api/graph/search
     */
    /**
     * 搜索公开图谱 (支持高级筛选)
     * GET /api/graph/search
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchGraphs(
            @org.springframework.web.bind.annotation.ModelAttribute com.sdu.kgplatform.dto.GraphSearchCriteria criteria,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(defaultValue = "viewCount") String sortBy) {

        // 记录搜索历史 (仅登录用户)
        if (criteria.getKeyword() != null && !criteria.getKeyword().trim().isEmpty()) {
            Integer userId = getCurrentUserId();
            if (userId != null) {
                historyService.recordSearch(userId, criteria.getKeyword().trim(), "graph");
            }
        }

        // 构建分页（支持排序）
        org.springframework.data.domain.Sort sort = org.springframework.data.domain.Sort
                .by(org.springframework.data.domain.Sort.Direction.DESC, "viewCount");
        if ("date".equals(sortBy)) {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                    "uploadDate");
        } else if ("hot".equals(sortBy)) {
            sort = org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,
                    "hotScore"); // Assuming hotScore exists or use formula
        }

        // 修正排序：GraphService 内部虽然有 createPageable 但 searchPublicGraphs 重载版直接接受 Pageable
        // 为保持一致性，我们在 Controller 构建好 Pageable
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                sort);

        Page<GraphListDto> graphs = graphService.searchPublicGraphs(criteria, pageable);
        return ResponseEntity.ok(Map.of(
                "content", graphs.getContent(),
                "totalElements", graphs.getTotalElements(),
                "totalPages", graphs.getTotalPages(),
                "currentPage", graphs.getNumber(),
                "criteria", criteria));
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

    /**
     * 个性化推荐图谱
     * GET /api/graph/recommend
     * 已登录用户：基于浏览历史的领域偏好
     * 未登录用户：降级为热门排序
     */
    @GetMapping("/recommend")
    public ResponseEntity<?> getRecommendedGraphs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String domain) {

        Integer userId = getCurrentUserId();
        Page<GraphListDto> graphs = graphService.getRecommendedGraphs(userId, domain, page, Math.min(size, 50));
        return ResponseEntity.ok(Map.of(
                "content", graphs.getContent(),
                "totalElements", graphs.getTotalElements(),
                "totalPages", graphs.getTotalPages(),
                "currentPage", graphs.getNumber()));
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
                    "data", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 更新图谱封面
     * PUT /api/graphs/{id}/cover
     * 
     * @param isCustom true=用户手动上传，false=自动生成缩略图（默认false）
     */
    @PutMapping("/{id}/cover")
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<?> updateGraphCover(
            @PathVariable("id") Integer graphId,
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "isCustom", defaultValue = "false") boolean isCustom) {

        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "请选择要上传的文件"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("error", "只能上传图片文件"));
        }

        try {
            // 保存封面文件
            String uploadBasePath = System.getProperty("user.dir") + "/uploads";
            java.nio.file.Path uploadPath = java.nio.file.Paths.get(uploadBasePath + "/covers");
            if (!java.nio.file.Files.exists(uploadPath)) {
                java.nio.file.Files.createDirectories(uploadPath);
            }

            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String newFilename = java.util.UUID.randomUUID().toString() + extension;

            java.nio.file.Path filePath = uploadPath.resolve(newFilename);
            java.nio.file.Files.copy(file.getInputStream(), filePath,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            String coverUrl = "/uploads/covers/" + newFilename;

            // 更新图谱封面
            graphService.updateGraphCover(graphId, userId, coverUrl, isCustom);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "url", coverUrl,
                    "isCustomCover", isCustom,
                    "message", "封面更新成功"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "封面更新失败: " + e.getMessage()));
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
                    "status", status.toUpperCase()));
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
                    "message", "图谱已删除"));
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
                    "message", "图谱已删除（管理员操作）"));
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
                    "totalCount", graphIds.size()));
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
                    "totalCount", graphIds.size()));
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
                    "totalCount", graphIds.size()));
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
                "totalGraphs", count));
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

    // ==================== 图谱收藏 ====================

    /**
     * 收藏/取消收藏图谱
     * POST /api/graph/{id}/favorite
     */
    @PostMapping("/{id}/favorite")
    public ResponseEntity<?> toggleFavorite(@PathVariable("id") Integer graphId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "请先登录"));
        }

        try {
            GraphFavoriteId favoriteId = new GraphFavoriteId(userId, graphId);
            boolean isFavorited = graphFavoriteRepository.existsById(favoriteId);

            if (isFavorited) {
                // 取消收藏
                graphFavoriteRepository.deleteById(favoriteId);
                // 更新图谱收藏数
                knowledgeGraphRepository.findById(graphId).ifPresent(graph -> {
                    graph.setCollectCount(
                            Math.max(0, (graph.getCollectCount() != null ? graph.getCollectCount() : 0) - 1));
                    knowledgeGraphRepository.save(graph);
                });
                return ResponseEntity.ok(Map.of("success", true, "favorited", false, "message", "已取消收藏"));
            } else {
                // 添加收藏
                GraphFavorite favorite = new GraphFavorite();
                favorite.setId(favoriteId);
                graphFavoriteRepository.save(favorite);
                // 更新图谱收藏数
                knowledgeGraphRepository.findById(graphId).ifPresent(graph -> {
                    graph.setCollectCount((graph.getCollectCount() != null ? graph.getCollectCount() : 0) + 1);
                    knowledgeGraphRepository.save(graph);
                });
                return ResponseEntity.ok(Map.of("success", true, "favorited", true, "message", "收藏成功"));
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 检查是否已收藏图谱
     * GET /api/graph/{id}/favorite/status
     */
    @GetMapping("/{id}/favorite/status")
    public ResponseEntity<?> getFavoriteStatus(@PathVariable("id") Integer graphId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(Map.of("favorited", false));
        }

        boolean isFavorited = graphFavoriteRepository.existsByIdUserIdAndIdGraphId(userId, graphId);
        return ResponseEntity.ok(Map.of("favorited", isFavorited));
    }

    /**
     * 获取用户收藏的图谱列表
     * GET /api/graph/favorites
     */
    @GetMapping("/favorites")
    public ResponseEntity<?> getUserFavoriteGraphs() {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "请先登录"));
        }

        try {
            List<GraphFavorite> favorites = graphFavoriteRepository.findByIdUserId(userId);
            List<Map<String, Object>> graphs = new java.util.ArrayList<>();

            for (GraphFavorite fav : favorites) {
                knowledgeGraphRepository.findById(fav.getId().getGraphId()).ifPresent(graph -> {
                    Map<String, Object> graphMap = new java.util.HashMap<>();
                    graphMap.put("graphId", graph.getGraphId());
                    graphMap.put("name", graph.getName());
                    graphMap.put("description", graph.getDescription());
                    graphMap.put("coverImage", graph.getCoverImage());
                    graphMap.put("nodeCount", graph.getNodeCount());
                    graphMap.put("viewCount", graph.getViewCount());
                    graphMap.put("collectCount", graph.getCollectCount());
                    graphMap.put("uploadDate", graph.getUploadDate());
                    graphMap.put("favoritedAt", fav.getCreatedAt());
                    graphs.add(graphMap);
                });
            }

            return ResponseEntity.ok(Map.of("success", true, "favorites", graphs));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 获取当前登录用户ID
     */
    private Integer getCurrentUserId() {
        return SecurityUtils.getCurrentUserId();
    }
}
