package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.GraphDetailDto;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.service.FileStorageService;
import com.sdu.kgplatform.service.GraphImportService;
import com.sdu.kgplatform.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 文件上传控制器
 * 重构后：主要负责 HTTP 请求处理和权限验证，业务逻辑委托给 Service
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final GraphImportService graphImportService;

    /**
     * 上传头像
     */
    @PostMapping("/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isNotAuthenticated(auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            validateImage(file, 2 * 1024 * 1024); // 2MB
            String avatarUrl = fileStorageService.storeFile(file, "avatars");
            User user = findCurrentUser(auth);
            userService.updateUserAvatar(user.getEmail() != null ? user.getEmail() : user.getUserName(), avatarUrl);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "avatarUrl", avatarUrl,
                    "message", "头像上传成功"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    /**
     * 上传帖子图片
     */
    @PostMapping("/image")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isNotAuthenticated(auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            validateImage(file, 5 * 1024 * 1024); // 5MB
            String imageUrl = fileStorageService.storeFile(file, "images");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "url", imageUrl,
                    "message", "图片上传成功"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    /**
     * 上传图谱封面
     */
    @PostMapping("/cover")
    public ResponseEntity<?> uploadCover(@RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isNotAuthenticated(auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            validateImage(file, 5 * 1024 * 1024); // 5MB
            String coverUrl = fileStorageService.storeFile(file, "covers");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "url", coverUrl,
                    "message", "封面上传成功"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    /**
     * 上传图谱文件（JSON）
     */
    @PostMapping("/graph")
    public ResponseEntity<?> uploadGraph(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "status", defaultValue = "DRAFT") String status,
            @RequestParam(value = "domain", defaultValue = "other") String domain,
            @RequestParam(value = "cover", required = false) MultipartFile coverFile) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (isNotAuthenticated(auth)) {
            return ResponseEntity.status(401).body(Map.of("error", "未登录"));
        }

        try {
            User user = findCurrentUser(auth);
            Integer userId = user.getUserId();

            GraphDetailDto graph = graphImportService.importGraph(file, name, description, status, domain, coverFile,
                    userId);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "图谱上传成功",
                    "graphId", graph.getGraphId(),
                    "graphName", graph.getName(),
                    "nodeCount", graph.getNodeCount(),
                    "relationCount", graph.getRelationCount()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "图谱上传失败: " + e.getMessage()));
        }
    }

    private boolean isNotAuthenticated(Authentication auth) {
        return auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal());
    }

    private void validateImage(MultipartFile file, long maxSize) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("只能上传图片文件");
        }
        if (file.getSize() > maxSize) {
            throw new IllegalArgumentException("文件大小均不能超过 " + (maxSize / 1024 / 1024) + "MB");
        }
    }

    private User findCurrentUser(Authentication auth) {
        if (auth.getPrincipal() instanceof com.sdu.kgplatform.security.CustomUserDetails cud) {
            return userRepository.findById(cud.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        }
        if (auth.getPrincipal() instanceof com.sdu.kgplatform.security.CustomOAuth2User oau) {
            return userRepository.findById(oau.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        }
        throw new IllegalArgumentException("无法识别当前用户");
    }
}
