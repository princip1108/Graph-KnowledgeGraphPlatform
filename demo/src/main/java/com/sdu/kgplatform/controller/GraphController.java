package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.GraphBatchDeleteResult;
import com.sdu.kgplatform.dto.GraphCreateDto;
import com.sdu.kgplatform.dto.GraphDetailDto;
import com.sdu.kgplatform.dto.GraphListDto;
import com.sdu.kgplatform.dto.GraphSearchCriteria;
import com.sdu.kgplatform.dto.GraphUpdateDto;
import com.sdu.kgplatform.entity.GraphFavorite;
import com.sdu.kgplatform.entity.GraphFavoriteId;
import com.sdu.kgplatform.entity.ResourceType;
import com.sdu.kgplatform.repository.GraphFavoriteRepository;
import com.sdu.kgplatform.security.CustomOAuth2User;
import com.sdu.kgplatform.security.CustomUserDetails;
import com.sdu.kgplatform.service.FileStorageService;
import com.sdu.kgplatform.service.FileValidationService;
import com.sdu.kgplatform.service.GraphService;
import com.sdu.kgplatform.service.HistoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/graph")
public class GraphController {

    private static final int MAX_BATCH_SIZE = 200;

    private final GraphService graphService;
    private final GraphFavoriteRepository graphFavoriteRepository;
    private final HistoryService historyService;
    private final FileStorageService fileStorageService;
    private final FileValidationService fileValidationService;

    public GraphController(GraphService graphService,
                           GraphFavoriteRepository graphFavoriteRepository,
                           HistoryService historyService,
                           FileStorageService fileStorageService,
                           FileValidationService fileValidationService) {
        this.graphService = graphService;
        this.graphFavoriteRepository = graphFavoriteRepository;
        this.historyService = historyService;
        this.fileStorageService = fileStorageService;
        this.fileValidationService = fileValidationService;
    }

    @PostMapping
    public ResponseEntity<?> createGraph(@Valid @RequestBody GraphCreateDto dto) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            GraphDetailDto created = graphService.createGraph(userId, dto);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "graphId", created.getGraphId(),
                    "graph", created));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/public")
    public ResponseEntity<Page<GraphListDto>> getPublicGraphs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "viewCount") String sortBy,
            @RequestParam(required = false) Integer categoryId) {
        return ResponseEntity.ok(graphService.getPublicGraphs(page, size, sortBy, categoryId));
    }

    @GetMapping("/recommended")
    public ResponseEntity<Page<GraphListDto>> getRecommendedGraphs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String refineKeyword,
            @RequestParam(defaultValue = "recommended") String sortBy) {
        if (hasText(keyword) || hasText(refineKeyword)) {
            GraphSearchCriteria criteria = new GraphSearchCriteria();
            criteria.setKeyword(keyword);
            criteria.setRefineKeyword(refineKeyword);
            criteria.setDomain(normalizeDomain(domain));

            Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), graphServiceSort(sortBy));
            return ResponseEntity.ok(graphService.searchPublicGraphs(criteria, pageable));
        }

        return ResponseEntity.ok(graphService.getRecommendedGraphs(getCurrentUserId(), domain, page, size));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<GraphListDto>> searchGraphs(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String refineKeyword,
            @RequestParam(required = false) Integer categoryId,
            @RequestParam(required = false) String domain,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "viewCount") String sortBy) {
        GraphSearchCriteria criteria = new GraphSearchCriteria();
        criteria.setKeyword(keyword);
        criteria.setRefineKeyword(refineKeyword);
        criteria.setCategoryId(categoryId);
        criteria.setDomain(normalizeDomain(domain));

        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), graphServiceSort(sortBy));
        return ResponseEntity.ok(graphService.searchPublicGraphs(criteria, pageable));
    }

    @GetMapping("/popular")
    public ResponseEntity<List<GraphListDto>> getPopularGraphs(
            @RequestParam(defaultValue = "10") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(graphService.getPopularGraphs(safeLimit));
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyGraphs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "lastModified") String sortBy,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String domain,
            @RequestParam(required = false) Integer categoryId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        Page<GraphListDto> result = graphService.getUserGraphs(userId, page, size, sortBy, status, keyword, domain,
                categoryId);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "content", result.getContent(),
                "graphs", result.getContent(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", result.getNumber(),
                "size", result.getSize()));
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> getFavoriteGraphs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        Page<GraphListDto> favorites = graphService.getFavoriteGraphs(userId, page, size);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "favorites", favorites.getContent(),
                "content", favorites.getContent(),
                "totalElements", favorites.getTotalElements(),
                "totalPages", favorites.getTotalPages(),
                "page", favorites.getNumber(),
                "size", favorites.getSize()));
    }

    @GetMapping("/share/{shareLink}")
    public ResponseEntity<?> getGraphByShareLink(@PathVariable String shareLink) {
        try {
            GraphDetailDto graph = graphService.getGraphByShareLink(shareLink);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "graphId", graph.getGraphId(),
                    "graph", graph));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{graphId}")
    public ResponseEntity<?> getGraphById(
            @PathVariable Integer graphId,
            @RequestParam(defaultValue = "false") boolean incrementView) {
        try {
            Integer userId = getCurrentUserId();
            if (!graphService.canViewGraph(graphId, userId)) {
                return ResponseEntity.status(403).body(Map.of("error", "无权访问该图谱"));
            }

            GraphDetailDto graph = graphService.getGraphById(graphId);

            if (incrementView) {
                boolean shouldCountView = userId == null
                        || historyService.recordBrowsingIfStale(userId, ResourceType.graph, graphId);
                if (shouldCountView) {
                    graphService.incrementViewCount(graphId);
                    graph.setViewCount((graph.getViewCount() == null ? 0 : graph.getViewCount()) + 1);
                }
            }

            return ResponseEntity.ok(graph);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{graphId}/visualization")
    public ResponseEntity<?> getGraphVisualization(
            @PathVariable Integer graphId,
            @RequestParam(defaultValue = "500") int nodeLimit,
            @RequestParam(defaultValue = "1000") int relationLimit) {
        try {
            if (!graphService.canViewGraph(graphId, getCurrentUserId())) {
                return ResponseEntity.status(403).body(Map.of("error", "无权访问该图谱"));
            }
            return ResponseEntity.ok(graphService.getGraphVisualization(graphId, nodeLimit, relationLimit));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
        }
    }

    @GetMapping("/{graphId}/can-edit")
    public ResponseEntity<Map<String, Object>> canEdit(@PathVariable Integer graphId) {
        Integer userId = getCurrentUserId();
        boolean canEdit = userId != null && graphService.isGraphOwner(graphId, userId);
        return ResponseEntity.ok(Map.of("canEdit", canEdit));
    }

    @PutMapping("/{graphId}")
    public ResponseEntity<?> updateGraph(@PathVariable Integer graphId, @Valid @RequestBody GraphUpdateDto dto) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            GraphDetailDto updated = graphService.updateGraph(graphId, userId, dto);
            return ResponseEntity.ok(Map.of("success", true, "graph", updated));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PutMapping("/{graphId}/cover")
    public ResponseEntity<?> updateGraphCover(
            @PathVariable Integer graphId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "true") boolean isCustom) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        if (!graphService.isGraphOwner(graphId, userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权修改此图谱"));
        }

        String coverUrl = null;
        try {
            fileValidationService.validateImage(file, FileValidationService.IMAGE_MAX_SIZE);
            coverUrl = fileStorageService.storeFile(file, "covers");
            graphService.updateGraphCover(graphId, userId, coverUrl, isCustom);
            return ResponseEntity.ok(Map.of("success", true, "url", coverUrl));
        } catch (Exception ex) {
            if (coverUrl != null) {
                fileStorageService.deleteFile(coverUrl);
            }
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{graphId}")
    public ResponseEntity<?> deleteGraph(@PathVariable Integer graphId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            graphService.deleteGraph(graphId, userId);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    @PostMapping("/{graphId}/favorite")
    public ResponseEntity<?> toggleFavorite(@PathVariable Integer graphId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }
        if (!graphService.canViewGraph(graphId, userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "无权收藏该图谱"));
        }

        GraphFavoriteId id = new GraphFavoriteId(userId, graphId);
        boolean exists = graphFavoriteRepository.existsById(id);

        if (exists) {
            graphFavoriteRepository.deleteById(id);
        } else {
            graphFavoriteRepository.save(new GraphFavorite(id, null));
        }

        long collectCountLong = graphFavoriteRepository.countByIdGraphId(graphId);
        int collectCount = collectCountLong > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) collectCountLong;
        graphService.updateGraphCollectCount(graphId, collectCount);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "favorited", !exists,
                "collectCount", collectCount));
    }

    @PostMapping("/batch/publish")
    public ResponseEntity<?> batchPublish(@RequestBody Map<String, List<Integer>> body) {
        return handleBatchStatusUpdate(body, "PUBLISHED");
    }

    @PostMapping("/batch/offline")
    public ResponseEntity<?> batchOffline(@RequestBody Map<String, List<Integer>> body) {
        return handleBatchStatusUpdate(body, "DRAFT");
    }

    @PostMapping("/batch/delete")
    public ResponseEntity<?> batchDelete(@RequestBody Map<String, List<Integer>> body) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        List<Integer> graphIds = body.get("graphIds");
        if (graphIds == null || graphIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "未提供图谱ID"));
        }
        if (graphIds.size() > MAX_BATCH_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "单次最多处理 " + MAX_BATCH_SIZE + " 个图谱"));
        }

        GraphBatchDeleteResult result = graphService.batchDeleteGraphs(graphIds, userId);
        int successCount = result.getCount();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "allSucceeded", result.isAllSucceeded(),
                "count", successCount,
                "requestedCount", result.getRequestedCount(),
                "successIds", result.getSuccessIds(),
                "failedItems", result.getFailedItems(),
                "message", result.isAllSucceeded()
                        ? "成功删除 " + successCount + " 个图谱"
                        : "成功删除 " + successCount + " 个图谱，失败 " + result.getFailedItems().size() + " 个"));
    }

    private ResponseEntity<?> handleBatchStatusUpdate(Map<String, List<Integer>> body, String status) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        List<Integer> graphIds = body.get("graphIds");
        if (graphIds == null || graphIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "未提供图谱ID"));
        }
        if (graphIds.size() > MAX_BATCH_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("error", "单次最多处理 " + MAX_BATCH_SIZE + " 个图谱"));
        }

        int successCount = graphService.batchUpdateStatus(graphIds, userId, status);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "count", successCount,
                "message", "成功处理 " + successCount + " 个图谱"));
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

    private org.springframework.data.domain.Sort graphServiceSort(String sortBy) {
        if (sortBy == null || sortBy.isBlank()) {
            return org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "viewCount");
        }

        return switch (sortBy) {
            case "name" -> org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC, "name");
            case "date" -> org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "uploadDate");
            case "hot", "recommended" -> org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "hotScore");
            case "collectCount", "collects" -> org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "collectCount");
            default -> org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "viewCount");
        };
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeDomain(String domain) {
        return domain == null || domain.isBlank() || "all".equals(domain) ? null : domain;
    }

    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return 20;
        }
        return Math.min(size, 100);
    }
}
