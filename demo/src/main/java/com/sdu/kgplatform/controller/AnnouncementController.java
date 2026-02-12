package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.common.SecurityUtils;
import com.sdu.kgplatform.entity.Announcement;
import com.sdu.kgplatform.entity.AnnouncementType;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.AnnouncementRepository;
import com.sdu.kgplatform.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 论坛公告控制器
 */
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {

    private final AnnouncementRepository announcementRepository;
    private final UserRepository userRepository;

    public AnnouncementController(AnnouncementRepository announcementRepository,
                                  UserRepository userRepository) {
        this.announcementRepository = announcementRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        return SecurityUtils.getCurrentUser(userRepository);
    }

    /**
     * 获取活跃公告列表（所有用户可访问）
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAnnouncements() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Announcement> announcements = announcementRepository.findTop5ByIsActiveTrueOrderByIsPinnedDescCreatedAtDesc();
            response.put("success", true);
            response.put("announcements", announcements);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 发布公告（仅管理员）
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createAnnouncement(@RequestBody Map<String, String> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            if (!SecurityUtils.isAdmin()) {
                response.put("success", false);
                response.put("error", "权限不足，仅管理员可发布公告");
                return ResponseEntity.status(403).body(response);
            }

            String title = request.get("title");
            String content = request.get("content");
            String typeStr = request.get("type");

            if (title == null || title.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "公告标题不能为空");
                return ResponseEntity.badRequest().body(response);
            }

            Announcement announcement = new Announcement();
            announcement.setTitle(title.trim());
            announcement.setContent(content != null ? content.trim() : "");
            announcement.setAuthorId(user.getUserId());

            if (typeStr != null && !typeStr.isEmpty()) {
                try {
                    announcement.setType(AnnouncementType.valueOf(typeStr));
                } catch (IllegalArgumentException e) {
                    announcement.setType(AnnouncementType.INFO);
                }
            }

            announcementRepository.save(announcement);

            response.put("success", true);
            response.put("message", "公告发布成功");
            response.put("announcement", announcement);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 删除公告（仅管理员）
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteAnnouncement(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            if (!SecurityUtils.isAdmin()) {
                response.put("success", false);
                response.put("error", "权限不足");
                return ResponseEntity.status(403).body(response);
            }

            if (!announcementRepository.existsById(id)) {
                response.put("success", false);
                response.put("error", "公告不存在");
                return ResponseEntity.status(404).body(response);
            }

            announcementRepository.deleteById(id);
            response.put("success", true);
            response.put("message", "公告已删除");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 停用公告（仅管理员）
     */
    @PostMapping("/{id}/toggle")
    public ResponseEntity<Map<String, Object>> toggleAnnouncement(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            if (!SecurityUtils.isAdmin()) {
                response.put("success", false);
                response.put("error", "权限不足");
                return ResponseEntity.status(403).body(response);
            }

            Announcement announcement = announcementRepository.findById(id).orElse(null);
            if (announcement == null) {
                response.put("success", false);
                response.put("error", "公告不存在");
                return ResponseEntity.status(404).body(response);
            }

            announcement.setIsActive(!Boolean.TRUE.equals(announcement.getIsActive()));
            announcementRepository.save(announcement);

            response.put("success", true);
            response.put("active", announcement.getIsActive());
            response.put("message", announcement.getIsActive() ? "公告已启用" : "公告已停用");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 置顶/取消置顶公告（仅管理员）
     */
    @PostMapping("/{id}/pin")
    public ResponseEntity<Map<String, Object>> togglePinAnnouncement(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            if (!SecurityUtils.isAdmin()) {
                response.put("success", false);
                response.put("error", "权限不足");
                return ResponseEntity.status(403).body(response);
            }

            Announcement announcement = announcementRepository.findById(id).orElse(null);
            if (announcement == null) {
                response.put("success", false);
                response.put("error", "公告不存在");
                return ResponseEntity.status(404).body(response);
            }

            announcement.setIsPinned(!Boolean.TRUE.equals(announcement.getIsPinned()));
            announcementRepository.save(announcement);

            response.put("success", true);
            response.put("pinned", announcement.getIsPinned());
            response.put("message", Boolean.TRUE.equals(announcement.getIsPinned()) ? "公告已置顶" : "公告已取消置顶");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
