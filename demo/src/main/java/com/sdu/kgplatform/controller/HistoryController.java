package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.entity.BrowsingHistory;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import com.sdu.kgplatform.entity.Post;
import com.sdu.kgplatform.entity.ResourceType;
import com.sdu.kgplatform.entity.SearchHistory;
import com.sdu.kgplatform.service.HistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private static final Logger log = LoggerFactory.getLogger(HistoryController.class);

    private final HistoryService historyService;
    private final com.sdu.kgplatform.repository.KnowledgeGraphRepository knowledgeGraphRepository;
    private final com.sdu.kgplatform.repository.PostRepository postRepository;

    public HistoryController(HistoryService historyService,
            com.sdu.kgplatform.repository.KnowledgeGraphRepository knowledgeGraphRepository,
            com.sdu.kgplatform.repository.PostRepository postRepository) {
        this.historyService = historyService;
        this.knowledgeGraphRepository = knowledgeGraphRepository;
        this.postRepository = postRepository;
    }

    /**
     * 获取搜索历史
     * GET /api/history/search?type=graph
     */
    @GetMapping("/search")
    public ResponseEntity<?> getSearchHistory(
            @RequestParam(defaultValue = "graph") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
            Page<SearchHistory> history = historyService.getUserSearchHistory(userId, type, pageable);

            return ResponseEntity.ok(Map.of(
                    "content", history.getContent(),
                    "totalElements", history.getTotalElements()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 记录搜索历史
     * POST /api/history/search?type=graph&keyword=xxx
     */
    @PostMapping("/search")
    public ResponseEntity<?> recordSearch(
            @RequestParam(defaultValue = "graph") String type,
            @RequestParam String keyword) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        historyService.recordSearch(userId, keyword, type);
        return ResponseEntity.ok(Map.of("success", true));
    }

    /**
     * 清空搜索历史
     * DELETE /api/history/search?type=graph
     */
    @DeleteMapping("/search")
    public ResponseEntity<?> clearSearchHistory(@RequestParam(defaultValue = "graph") String type) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        historyService.clearSearchHistory(userId, type);
        return ResponseEntity.ok(Map.of("success", true, "message", "已清空搜索历史"));
    }

    /**
     * 删除单条搜索历史
     * DELETE /api/history/search/item?type=graph&keyword=xxx
     */
    @DeleteMapping("/search/item")
    public ResponseEntity<?> deleteSearchHistoryItem(
            @RequestParam(defaultValue = "graph") String type,
            @RequestParam String keyword) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        historyService.deleteSearchHistory(userId, type, keyword);
        return ResponseEntity.ok(Map.of("success", true, "message", "已删除"));
    }

    /**
     * 获取浏览历史
     * GET /api/history/browsing?type=graph
     */
    @GetMapping("/browsing")
    public ResponseEntity<?> getBrowsingHistory(
            @RequestParam(defaultValue = "graph") String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            ResourceType resourceType;
            try {
                // 兼容前端可能传大写或小写
                resourceType = ResourceType.valueOf(type.toLowerCase()); // post or graph
            } catch (IllegalArgumentException e) {
                // 假如前端传了 'GRAPH' 或 'POST'，这里尝试再次转换或默认
                resourceType = ResourceType.valueOf(type.toLowerCase().trim());
            }

            Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
            Page<BrowsingHistory> historyPage = historyService.getUserBrowsingHistory(userId, resourceType, pageable);

            final ResourceType finalResourceType = resourceType;
            Map<Integer, String> resourceNames = loadResourceNames(historyPage.getContent(), finalResourceType);

            // 转换为 DTO 并填充名称
            Page<com.sdu.kgplatform.domain.dto.BrowsingHistoryDto> dtoPage = historyPage.map(history -> {
                com.sdu.kgplatform.domain.dto.BrowsingHistoryDto dto = new com.sdu.kgplatform.domain.dto.BrowsingHistoryDto();
                dto.setId(history.getId());
                dto.setUserId(history.getUserId());
                dto.setResourceType(history.getResourceType());
                dto.setViewTime(history.getViewTime());

                if (finalResourceType == ResourceType.graph) {
                    dto.setResourceId(history.getGraphId());
                    dto.setResourceName(resourceNames.get(history.getGraphId()));
                } else {
                    dto.setResourceId(history.getPostId());
                    dto.setResourceName(resourceNames.get(history.getPostId()));
                }

                if (dto.getResourceName() == null) {
                    dto.setResourceName(finalResourceType == ResourceType.graph ? "未知图谱 (已删除?)" : "未知帖子 (已删除?)");
                }
                return dto;
            });

            return ResponseEntity.ok(Map.of(
                    "content", dtoPage.getContent(),
                    "totalElements", dtoPage.getTotalElements()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的资源类型: " + type));
        } catch (Exception e) {
            log.error("Failed to get browsing history, type={}", type, e);
            return ResponseEntity.internalServerError().body(Map.of("error", "获取历史记录失败"));
        }
    }

    /**
     * 清空浏览历史
     * DELETE /api/history/browsing?type=graph
     */
    @DeleteMapping("/browsing")
    public ResponseEntity<?> clearBrowsingHistory(@RequestParam(defaultValue = "graph") String type) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            ResourceType resourceType = ResourceType.valueOf(type.toLowerCase());
            historyService.clearBrowsingHistory(userId, resourceType);
            return ResponseEntity.ok(Map.of("success", true, "message", "已清空浏览历史"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "无效的资源类型"));
        }
    }

    /**
     * 删除单条浏览历史
     * DELETE /api/history/browsing/{id}
     */
    @DeleteMapping("/browsing/{id}")
    public ResponseEntity<?> deleteBrowsingHistoryItem(@PathVariable Integer id) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        historyService.deleteBrowsingHistory(userId, id);
        return ResponseEntity.ok(Map.of("success", true, "message", "已删除"));
    }

    private Integer getCurrentUserId() {
        return com.sdu.kgplatform.common.SecurityUtils.getCurrentUserId();
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

    private Map<Integer, String> loadResourceNames(List<BrowsingHistory> histories, ResourceType resourceType) {
        if (histories.isEmpty()) {
            return Map.of();
        }

        if (resourceType == ResourceType.graph) {
            List<Integer> graphIds = histories.stream()
                    .map(BrowsingHistory::getGraphId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Integer, String> names = new java.util.HashMap<>();
            knowledgeGraphRepository.findAllById(graphIds).forEach(graph -> {
                if (graph.getName() != null) {
                    names.put(graph.getGraphId(), graph.getName());
                }
            });
            return names;
        }

        List<Integer> postIds = histories.stream()
                .map(BrowsingHistory::getPostId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Integer, String> names = new java.util.HashMap<>();
        postRepository.findAllById(postIds).forEach(post -> {
            if (post.getPostTitle() != null) {
                names.put(post.getPostId(), post.getPostTitle());
            }
        });
        return names;
    }
}
