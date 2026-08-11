package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.domain.dto.ConversationDto;
import com.sdu.kgplatform.entity.Message;
import com.sdu.kgplatform.security.CustomUserDetails;
import com.sdu.kgplatform.service.MessageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 获取会话列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations() {
        Integer userId = getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        List<ConversationDto> conversations = messageService.getConversations(userId);
        return ResponseEntity.ok(Map.of("success", true, "conversations", conversations));
    }

    /**
     * 获取与某人的聊天记录
     * GET /api/messages/history?otherUserId=123&page=0
     */
    @GetMapping("/history")
    public ResponseEntity<?> getHistory(
            @RequestParam Integer otherUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Integer userId = getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
        Page<Message> history = messageService.getHistory(userId, otherUserId, pageable);

        // 自动标记已读
        messageService.markAsRead(userId, otherUserId);

        return ResponseEntity.ok(
                Map.of("success", true, "messages", history.getContent(), "totalElements", history.getTotalElements()));
    }

    /**
     * 发送私信
     * POST /api/messages
     */
    @PostMapping
    public ResponseEntity<?> sendMessage(@RequestBody Map<String, Object> payload) {
        Integer userId = getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        Integer receiverId = asInteger(payload.get("receiverId"));
        String content = asString(payload.get("content"));

        if (receiverId == null || content == null || content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "参数错误"));
        }

        try {
            Message msg = messageService.sendMessage(userId, receiverId, content);
            return ResponseEntity.ok(Map.of("success", true, "message", msg));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", ex.getMessage()));
        }
    }

    /**
     * 获取未读数
     */
    @GetMapping("/unread-count")
    public ResponseEntity<?> getUnreadCount() {
        Integer userId = getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        long count = messageService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("success", true, "count", count));
    }

    /**
     * 删除私信
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteMessage(@PathVariable Integer id) {
        Integer userId = getCurrentUserId();
        if (userId == null)
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));

        messageService.deleteMessage(userId, id);
        return ResponseEntity.ok(Map.of("success", true));
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
