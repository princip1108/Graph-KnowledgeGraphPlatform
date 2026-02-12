package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.entity.UserFeedback;
import com.sdu.kgplatform.security.CustomUserDetails;
import com.sdu.kgplatform.service.FeedbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 用户反馈 API 控制器
 */
@RestController
@RequestMapping("/api/feedback")
public class FeedbackController {

    private final FeedbackService feedbackService;

    public FeedbackController(FeedbackService feedbackService) {
        this.feedbackService = feedbackService;
    }

    /**
     * 提交用户反馈
     * POST /api/feedback
     */
    @PostMapping
    public ResponseEntity<?> submit(@RequestBody Map<String, String> payload) {
        String feedbackType = payload.get("feedbackType");
        String subject = payload.get("subject");
        String content = payload.get("content");
        String email = payload.get("email");

        if (subject == null || subject.trim().isEmpty()
                || content == null || content.trim().isEmpty()
                || email == null || email.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "请填写所有必填字段"));
        }

        // 尝试获取当前登录用户ID（未登录也可提交）
        Integer userId = getCurrentUserId();

        UserFeedback feedback = feedbackService.submitFeedback(userId, feedbackType, subject.trim(), content.trim(), email.trim());
        return ResponseEntity.ok(Map.of("success", true, "feedbackId", feedback.getFeedbackId()));
    }

    private Integer getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof CustomUserDetails) {
            return ((CustomUserDetails) auth.getPrincipal()).getUserId();
        }
        return null;
    }
}
