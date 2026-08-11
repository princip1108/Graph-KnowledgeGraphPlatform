package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.security.CustomOAuth2User;
import com.sdu.kgplatform.security.CustomUserDetails;
import com.sdu.kgplatform.service.PostService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserApiController {

    private final PostService postService;

    public UserApiController(PostService postService) {
        this.postService = postService;
    }

    @GetMapping("/favorites")
    public ResponseEntity<?> getPostFavorites(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Integer currentUserId = resolveCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }

        Page<Map<String, Object>> favorites = postService.getFavoritePostSummaries(currentUserId, page, size);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "favorites", favorites.getContent(),
                "content", favorites.getContent(),
                "totalElements", favorites.getTotalElements(),
                "totalPages", favorites.getTotalPages(),
                "page", favorites.getNumber(),
                "size", favorites.getSize()));
    }

    private Integer resolveCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof CustomOAuth2User oAuth2User) {
            return oAuth2User.getUserId();
        }
        if (principal instanceof CustomUserDetails userDetails) {
            return userDetails.getUserId();
        }
        return null;
    }
}
