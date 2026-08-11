package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.dto.UserProfileDto;
import com.sdu.kgplatform.entity.UserFollow;
import com.sdu.kgplatform.entity.UserFollowId;
import com.sdu.kgplatform.repository.UserFollowRepository;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.security.CustomOAuth2User;
import com.sdu.kgplatform.security.CustomUserDetails;
import com.sdu.kgplatform.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class PublicUserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final UserFollowRepository userFollowRepository;

    public PublicUserController(UserService userService,
                                UserRepository userRepository,
                                UserFollowRepository userFollowRepository) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.userFollowRepository = userFollowRepository;
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUserProfileById(@PathVariable Integer id) {
        try {
            UserProfileDto profile = userService.getUserProfileById(id, true);
            if ("已注销".equals(profile.getStatus())) {
                Map<String, Object> deletedUser = new HashMap<>();
                deletedUser.put("userId", profile.getUserId());
                deletedUser.put("userName", "已注销用户");
                deletedUser.put("bio", "该用户已注销账号");
                deletedUser.put("deleted", true);
                return ResponseEntity.ok(deletedUser);
            }
            return ResponseEntity.ok(profile);
        } catch (Exception e) {
            return ResponseEntity.status(404).body(Map.of("error", "用户不存在"));
        }
    }

    @GetMapping("/{id}/follow/status")
    public ResponseEntity<?> followStatus(@PathVariable Integer id) {
        Integer currentUserId = resolveCurrentUserId();
        boolean following = currentUserId != null && userFollowRepository.existsById(new UserFollowId(currentUserId, id));
        return ResponseEntity.ok(Map.of("success", true, "following", following));
    }

    @PostMapping("/{id}/follow")
    public ResponseEntity<?> toggleFollow(@PathVariable Integer id) {
        Integer currentUserId = resolveCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "error", "未登录"));
        }
        if (currentUserId.equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "不能关注自己"));
        }
        if (userRepository.findById(id).isEmpty()) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "用户不存在"));
        }

        UserFollowId followId = new UserFollowId(currentUserId, id);
        boolean following = !userFollowRepository.existsById(followId);
        if (following) {
            userFollowRepository.save(new UserFollow(followId, LocalDateTime.now()));
        } else {
            userFollowRepository.deleteById(followId);
        }
        return ResponseEntity.ok(Map.of("success", true, "following", following));
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
