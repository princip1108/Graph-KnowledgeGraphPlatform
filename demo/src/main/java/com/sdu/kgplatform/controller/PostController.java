package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.entity.Post;
import com.sdu.kgplatform.entity.PostStatus;
import com.sdu.kgplatform.security.CustomOAuth2User;
import com.sdu.kgplatform.security.CustomUserDetails;
import com.sdu.kgplatform.service.CommentService;
import com.sdu.kgplatform.service.PostService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/posts")
public class PostController {

    private static final int MAX_BATCH_SIZE = 200;

    private final PostService postService;
    private final CommentService commentService;

    public PostController(PostService postService, CommentService commentService) {
        this.postService = postService;
        this.commentService = commentService;
    }

    @GetMapping
    public ResponseEntity<?> listPosts(@RequestParam(defaultValue = "") String keyword,
                                       @RequestParam(defaultValue = "latest") String sort,
                                       @RequestParam(defaultValue = "") String category,
                                       @RequestParam(defaultValue = "") String domain,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "10") int size) {
        String categoryFilter = !category.isBlank() ? category : domain;
        var posts = postService.listPosts(keyword, sort, categoryFilter, page, size, getCurrentUserId());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "posts", posts.getContent(),
                "hasNext", posts.hasNext(),
                "totalElements", posts.getTotalElements()));
    }

    @GetMapping("/pinned")
    public ResponseEntity<?> pinnedPosts() {
        return ResponseEntity.ok(Map.of("success", true, "posts", postService.listPinnedPosts(getCurrentUserId())));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> stats() {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("success", true);
        payload.putAll(postService.getForumStats());
        return ResponseEntity.ok(payload);
    }

    @GetMapping("/{postId}")
    public ResponseEntity<?> getPost(@PathVariable Integer postId) {
        try {
            return ResponseEntity.ok(postService.getPostDetail(postId, getCurrentUserId(), isAdmin()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PostMapping
    public ResponseEntity<?> createPost(@RequestBody Map<String, Object> body) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }
        try {
            return ResponseEntity.ok(postService.createPost(userId, body));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PutMapping("/{postId}")
    public ResponseEntity<?> updatePost(@PathVariable Integer postId, @RequestBody Map<String, Object> body) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }
        try {
            return ResponseEntity.ok(postService.updatePost(postId, userId, body, isAdmin()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @DeleteMapping("/{postId}")
    public ResponseEntity<?> deletePost(@PathVariable Integer postId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }
        try {
            postService.deletePost(postId, userId, isAdmin());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<Page<Post>> postsByUser(@PathVariable Integer userId,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String keyword) {
        return ResponseEntity.ok(postService.getPostsByUser(userId, page, size, status, keyword, getCurrentUserId(), isAdmin()));
    }

    @GetMapping("/related")
    public ResponseEntity<?> relatedPosts(@RequestParam Integer graphId,
                                          @RequestParam(defaultValue = "8") int size) {
        return ResponseEntity.ok(Map.of(
                "success", true,
                "posts", postService.getRelatedPosts(graphId, size, getCurrentUserId())));
    }

    @PostMapping("/{postId}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Integer postId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }
        try {
            return ResponseEntity.ok(postService.toggleLike(postId, userId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PostMapping("/{postId}/favorite")
    public ResponseEntity<?> toggleFavorite(@PathVariable Integer postId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }
        try {
            return ResponseEntity.ok(postService.toggleFavorite(postId, userId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @GetMapping("/{postId}/favorite/status")
    public ResponseEntity<?> favoriteStatus(@PathVariable Integer postId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.ok(Map.of("success", true, "favorited", false));
        }
        try {
            return ResponseEntity.ok(postService.getFavoriteStatus(postId, userId, isAdmin()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @GetMapping("/{postId}/comments")
    public ResponseEntity<?> listComments(@PathVariable Integer postId) {
        try {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "comments", commentService.getCommentsByPostId(postId, getCurrentUserId(), isAdmin())));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PostMapping("/{postId}/comments")
    public ResponseEntity<?> createComment(@PathVariable Integer postId, @RequestBody Map<String, Object> body) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }
        String content = asString(body.containsKey("content") ? body.get("content") : body.get("text"));
        Integer parentId = asInteger(body.get("parentCommentId"));
        try {
            var comment = commentService.createComment(postId, userId, content, parentId);
            return ResponseEntity.ok(Map.of("success", true, "comment", comment));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PostMapping("/{postId}/pin")
    public ResponseEntity<?> togglePin(@PathVariable Integer postId) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }
        try {
            return ResponseEntity.ok(postService.togglePin(postId, userId, isAdmin()));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    @PostMapping("/batch/online")
    public ResponseEntity<?> batchOnline(@RequestBody Map<String, List<Integer>> body) {
        return batchUpdate(body.get("postIds"), PostStatus.已发布);
    }

    @PostMapping("/batch/offline")
    public ResponseEntity<?> batchOffline(@RequestBody Map<String, List<Integer>> body) {
        return batchUpdate(body.get("postIds"), PostStatus.已下架);
    }

    @PostMapping("/batch/delete")
    public ResponseEntity<?> batchDelete(@RequestBody Map<String, List<Integer>> body) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }
        List<Integer> postIds = body.getOrDefault("postIds", List.of());
        if (postIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "未提供帖子ID"));
        }
        if (postIds.size() > MAX_BATCH_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "单次最多处理 " + MAX_BATCH_SIZE + " 个帖子"));
        }
        int count = postService.batchDelete(postIds, userId, isAdmin());
        return ResponseEntity.ok(Map.of("success", true, "message", "成功删除 " + count + " 个帖子"));
    }

    private ResponseEntity<?> batchUpdate(List<Integer> postIds, PostStatus status) {
        Integer userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }
        List<Integer> safePostIds = postIds == null ? List.of() : postIds;
        if (safePostIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "未提供帖子ID"));
        }
        if (safePostIds.size() > MAX_BATCH_SIZE) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "单次最多处理 " + MAX_BATCH_SIZE + " 个帖子"));
        }
        int count = postService.batchUpdateStatus(safePostIds, status, userId, isAdmin());
        return ResponseEntity.ok(Map.of("success", true, "message", "成功处理 " + count + " 个帖子"));
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
        if (principal instanceof CustomOAuth2User oauth2User) {
            return oauth2User.getUserId();
        }
        return null;
    }

    private boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
