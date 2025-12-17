package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.entity.Comment;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.service.CommentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 评论 Controller
 */
@RestController
@RequestMapping("/api")
public class CommentController {

    @Autowired
    private CommentService commentService;

    @Autowired
    private UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        String username = auth.getName();
        return userRepository.findByEmail(username)
                .or(() -> userRepository.findByPhone(username))
                .orElse(null);
    }

    /**
     * 获取帖子的评论列表
     * GET /api/posts/{postId}/comments
     */
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<Map<String, Object>> getComments(@PathVariable Integer postId) {
        Map<String, Object> response = new HashMap<>();

        try {
            List<Map<String, Object>> comments = commentService.getCommentsByPostId(postId);
            long totalCount = commentService.countByPostId(postId);

            response.put("success", true);
            response.put("comments", comments);
            response.put("totalCount", totalCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 发表评论
     * POST /api/posts/{postId}/comments
     */
    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<Map<String, Object>> createComment(
            @PathVariable Integer postId,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> response = new HashMap<>();

        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }

            String text = (String) request.get("text");
            Integer parentCommentId = null;
            if (request.get("parentCommentId") != null) {
                parentCommentId = (Integer) request.get("parentCommentId");
            }

            if (text == null || text.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "评论内容不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            Comment comment = commentService.createComment(
                    postId,
                    user.getUserId(),
                    text.trim(),
                    parentCommentId
            );

            response.put("success", true);
            response.put("comment", comment);
            response.put("message", "评论成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除评论
     * DELETE /api/comments/{commentId}
     */
    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<Map<String, Object>> deleteComment(
            @PathVariable Integer commentId) {

        Map<String, Object> response = new HashMap<>();

        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }

            commentService.deleteComment(commentId, user.getUserId());

            response.put("success", true);
            response.put("message", "删除成功");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(403).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
