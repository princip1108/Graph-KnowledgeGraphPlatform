package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.common.SecurityUtils;
import com.sdu.kgplatform.dto.GraphOrphanCleanupResult;
import com.sdu.kgplatform.entity.Post;
import com.sdu.kgplatform.entity.Role;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.PostRepository;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.service.AdminService;
import com.sdu.kgplatform.service.GraphService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 管理员控制器 - 仅管理员可访问
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final GraphService graphService;

    public AdminController(AdminService adminService,
                           PostRepository postRepository,
                           UserRepository userRepository,
                           GraphService graphService) {
        this.adminService = adminService;
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.graphService = graphService;
    }

    /**
     * 获取所有帖子（管理员论坛管理用）
     */
    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> getAllPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        Map<String, Object> response = new HashMap<>();
        try {
            Page<Post> postPage = postRepository.findAll(
                    PageRequest.of(normalizePage(page), normalizeSize(size), Sort.by(Sort.Direction.DESC, "uploadTime")));

            List<Integer> authorIds = postPage.getContent().stream()
                    .map(Post::getAuthorId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
            Map<Integer, String> authorNames = new HashMap<>();
            userRepository.findAllById(authorIds).forEach(author -> {
                if (author.getUserName() != null) {
                    authorNames.put(author.getUserId(), author.getUserName());
                }
            });

            List<Map<String, Object>> postsWithAuthor = new ArrayList<>();
            for (Post post : postPage.getContent()) {
                Map<String, Object> postMap = new HashMap<>();
                postMap.put("postId", post.getPostId());
                postMap.put("postTitle", post.getPostTitle());
                postMap.put("postAbstract", post.getPostAbstract());
                postMap.put("postStatus", post.getPostStatus() != null ? post.getPostStatus().name() : null);
                postMap.put("uploadTime", post.getUploadTime());
                postMap.put("likeCount", post.getLikeCount());
                postMap.put("isPinned", post.getIsPinned());
                postMap.put("authorId", post.getAuthorId());
                postMap.put("category", post.getCategory());
                postMap.put("authorName", authorNames.getOrDefault(post.getAuthorId(), "未知用户"));

                postsWithAuthor.add(postMap);
            }

            response.put("success", true);
            response.put("posts", postsWithAuthor);
            response.put("totalElements", postPage.getTotalElements());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取用户列表（分页，避免一次性暴露全部用户实体）
     */
    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<User> userPage = adminService.searchUsers("", "ALL", page, size);
        List<Map<String, Object>> userList = userPage.getContent().stream()
                .map(this::toAdminUserMap)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("users", userList);
        result.put("totalElements", userPage.getTotalElements());
        result.put("totalPages", userPage.getTotalPages());
        result.put("currentPage", normalizePage(page));
        result.put("size", userPage.getSize());
        return ResponseEntity.ok(result);
    }

    /**
     * 搜索用户（支持关键词 + 角色/状态筛选 + 分页）
     */
    @GetMapping("/users/search")
    public ResponseEntity<Map<String, Object>> searchUsers(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "ALL") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            Page<User> userPage = adminService.searchUsers(keyword, filter, page, size);
            List<Map<String, Object>> userList = userPage.getContent().stream()
                    .map(this::toAdminUserMap)
                    .collect(Collectors.toList());

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("users", userList);
            result.put("totalElements", userPage.getTotalElements());
            result.put("totalPages", userPage.getTotalPages());
            result.put("currentPage", normalizePage(page));
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /**
     * 获取用户统计信息
     */
    @GetMapping("/stats/users")
    public ResponseEntity<Map<String, Object>> getUserStats() {
        return ResponseEntity.ok(adminService.getUserStats());
    }

    /**
     * 修改用户角色
     */
    @PutMapping("/users/{userId}/role")
    public ResponseEntity<?> updateUserRole(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> request) {
        String roleName = request.get("role");
        try {
            Role role = Role.valueOf(roleName.toUpperCase());
            Integer operatorId = SecurityUtils.getCurrentUserId();
            adminService.updateUserRole(userId, role, operatorId);
            return ResponseEntity.ok(Map.of("success", true, "message", "角色更新成功"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 封禁用户（支持时长）
     */
    @PutMapping("/users/{userId}/ban")
    public ResponseEntity<?> banUser(
            @PathVariable Integer userId,
            @RequestBody Map<String, Object> request) {
        try {
            int hours = 0;
            Object hoursObj = request.get("hours");
            if (hoursObj instanceof Number) {
                hours = ((Number) hoursObj).intValue();
            }
            adminService.banUser(userId, hours);
            String msg = hours > 0 ? "用户已封禁" + hours + "小时" : "用户已永久封禁";
            return ResponseEntity.ok(Map.of("success", true, "message", msg));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 解封用户
     */
    @PutMapping("/users/{userId}/unban")
    public ResponseEntity<?> unbanUser(@PathVariable Integer userId) {
        try {
            adminService.unbanUser(userId);
            return ResponseEntity.ok(Map.of("success", true, "message", "用户已解封"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 禁用/启用用户
     */
    @PutMapping("/users/{userId}/status")
    public ResponseEntity<?> updateUserStatus(
            @PathVariable Integer userId,
            @RequestBody Map<String, String> request) {
        String status = request.get("status");
        adminService.updateUserStatus(userId, status);
        return ResponseEntity.ok(Map.of("success", true, "message", "状态更新成功"));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(@PathVariable Integer userId) {
        adminService.deleteUser(userId);
        return ResponseEntity.ok(Map.of("success", true, "message", "用户已删除"));
    }

    @PostMapping("/graphs/cleanup-neo4j-orphans")
    public ResponseEntity<?> cleanupNeo4jOrphans() {
        GraphOrphanCleanupResult result = graphService.cleanupNeo4jOrphans();
        return ResponseEntity.ok(Map.of(
                "success", result.isSuccess(),
                "validGraphCount", result.getValidGraphCount(),
                "deletedNodeCount", result.getDeletedNodeCount(),
                "deletedRelationshipCount", result.getDeletedRelationshipCount(),
                "warnings", result.getWarnings()));
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

    private Map<String, Object> toAdminUserMap(User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("userName", user.getUserName());
        data.put("email", user.getEmail());
        data.put("avatar", user.getAvatar());
        data.put("role", user.getRole() != null ? user.getRole().name() : null);
        data.put("userStatus", user.getUserStatus() != null ? user.getUserStatus().name() : null);
        data.put("bannedUntil", user.getBannedUntil());
        data.put("createdAt", user.getCreatedAt());
        data.put("lastLoginAt", user.getLastLoginAt());
        return data;
    }
}
