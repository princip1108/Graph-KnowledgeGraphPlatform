package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.common.SecurityUtils;
import com.sdu.kgplatform.entity.*;
import com.sdu.kgplatform.repository.PostFavoriteRepository;
import com.sdu.kgplatform.repository.PostRepository;
import com.sdu.kgplatform.repository.UserFollowRepository;
import com.sdu.kgplatform.repository.UserRepository;
import com.sdu.kgplatform.service.PostService;

import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class PostController {

    private final PostService postService;
    private final UserRepository userRepository;
    private final PostFavoriteRepository postFavoriteRepository;
    private final UserFollowRepository userFollowRepository;
    private final PostRepository postRepository;

    public PostController(PostService postService, 
                         UserRepository userRepository,
                         PostFavoriteRepository postFavoriteRepository,
                         UserFollowRepository userFollowRepository,
                         PostRepository postRepository) {
        this.postService = postService;
        this.userRepository = userRepository;
        this.postFavoriteRepository = postFavoriteRepository;
        this.userFollowRepository = userFollowRepository;
        this.postRepository = postRepository;
    }
    
    private User getCurrentUser() {
        return SecurityUtils.getCurrentUser(userRepository);
    }

    @GetMapping("/posts")
    public ResponseEntity<Map<String, Object>> getPostList(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) String keyword) {
        Map<String, Object> response = new HashMap<>();
        try {
            Page<Post> postPage;
            if (keyword != null && !keyword.trim().isEmpty()) {
                postPage = postService.searchPosts(keyword.trim(), page, size);
            } else {
                postPage = postService.getPostList(page, size, sort);
            }
            
            // 为每个帖子添加作者信息
            List<Map<String, Object>> postsWithAuthor = new ArrayList<>();
            for (Post post : postPage.getContent()) {
                Map<String, Object> postMap = new HashMap<>();
                postMap.put("postId", post.getPostId());
                postMap.put("postTitle", post.getPostTitle());
                postMap.put("postAbstract", post.getPostAbstract());
                postMap.put("postText", post.getPostText());
                postMap.put("uploadTime", post.getUploadTime());
                postMap.put("likeCount", post.getLikeCount());
                postMap.put("authorId", post.getAuthorId());
                postMap.put("isPinned", post.getIsPinned());
                
                // 获取作者信息
                userRepository.findById(post.getAuthorId()).ifPresent(author -> {
                    postMap.put("authorName", author.getUserName());
                    postMap.put("authorAvatar", author.getAvatar());
                });
                
                postsWithAuthor.add(postMap);
            }
            
            response.put("success", true);
            response.put("posts", postsWithAuthor);
            response.put("totalPages", postPage.getTotalPages());
            response.put("totalElements", postPage.getTotalElements());
            response.put("currentPage", page);
            response.put("hasNext", postPage.hasNext());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<Map<String, Object>> getPostDetail(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            Integer currentUserId = null;
            User user = getCurrentUser();
            if (user != null) {
                currentUserId = user.getUserId();
            }
            Map<String, Object> detail = postService.getPostDetail(id, currentUserId);
            if (detail == null) {
                response.put("success", false);
                response.put("error", "帖子不存在");
                return ResponseEntity.status(404).body(response);
            }
            response.put("success", true);
            response.putAll(detail);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/posts")
    public ResponseEntity<Map<String, Object>> createPost(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            String title = (String) request.get("title");
            String postAbstract = (String) request.get("abstract");
            String content = (String) request.get("content");
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) request.get("tags");
            // 获取关联图谱ID
            Integer graphId = null;
            Object graphIdObj = request.get("graphId");
            if (graphIdObj != null) {
                if (graphIdObj instanceof Integer) {
                    graphId = (Integer) graphIdObj;
                } else if (graphIdObj instanceof String && !((String) graphIdObj).isEmpty()) {
                    graphId = Integer.parseInt((String) graphIdObj);
                }
            }
            if (title == null || title.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "标题不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            if (content == null || content.trim().isEmpty()) {
                response.put("success", false);
                response.put("error", "内容不能为空");
                return ResponseEntity.badRequest().body(response);
            }
            Post post = postService.createPost(user.getUserId(), title.trim(), postAbstract != null ? postAbstract.trim() : "", content.trim(), tags, graphId);
            response.put("success", true);
            response.put("post", post);
            response.put("message", "发布成功");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<Map<String, Object>> updatePost(@PathVariable Integer id, @RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            String title = (String) request.get("title");
            String postAbstract = (String) request.get("abstract");
            String content = (String) request.get("content");
            String status = (String) request.get("status");
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) request.get("tags");
            // 获取关联图谱ID
            Integer graphId = null;
            Object graphIdObj = request.get("graphId");
            if (graphIdObj != null) {
                if (graphIdObj instanceof Integer) {
                    graphId = (Integer) graphIdObj;
                } else if (graphIdObj instanceof String && !((String) graphIdObj).isEmpty()) {
                    graphId = Integer.parseInt((String) graphIdObj);
                }
            }
            
            Post post = postService.updatePost(id, user.getUserId(), title, postAbstract, content, tags);
            
            // 处理关联图谱
            post.setGraphId(graphId);
            postRepository.save(post);
            
            // 处理状态修改
            if (status != null && !status.isEmpty()) {
                try {
                    PostStatus newStatus = PostStatus.valueOf(status);
                    post.setPostStatus(newStatus);
                    postRepository.save(post);
                } catch (IllegalArgumentException e) {
                    // 忽略无效的状态值
                }
            }
            
            response.put("success", true);
            response.put("post", post);
            response.put("message", "更新成功");
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

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<Map<String, Object>> deletePost(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            postService.deletePost(id, user.getUserId());
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

    @PostMapping("/posts/{id}/like")
    public ResponseEntity<Map<String, Object>> toggleLike(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            boolean liked = postService.toggleLike(id, user.getUserId());
            response.put("success", true);
            response.put("liked", liked);
            response.put("message", liked ? "点赞成功" : "取消点赞");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(404).body(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/posts/stats")
    public ResponseEntity<Map<String, Object>> getForumStats() {
        Map<String, Object> response = new HashMap<>();
        try {
            Map<String, Object> stats = postService.getForumStats();
            response.put("success", true);
            response.putAll(stats);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/posts/user/{userId}")
    public ResponseEntity<Map<String, Object>> getUserPosts(@PathVariable Integer userId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> response = new HashMap<>();
        try {
            Page<Post> postPage = postService.getUserPosts(userId, page, size);
            response.put("success", true);
            response.put("posts", postPage.getContent());
            response.put("totalPages", postPage.getTotalPages());
            response.put("totalElements", postPage.getTotalElements());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/tags/hot")
    public ResponseEntity<Map<String, Object>> getHotTags() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Tag> tags = postService.getHotTags();
            response.put("success", true);
            response.put("tags", tags);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取置顶帖子
     */
    @GetMapping("/posts/pinned")
    public ResponseEntity<Map<String, Object>> getPinnedPosts() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<Post> posts = postService.getPinnedPosts();
            response.put("success", true);
            response.put("posts", posts);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 收藏/取消收藏帖子
     */
    @PostMapping("/posts/{id}/favorite")
    public ResponseEntity<Map<String, Object>> toggleFavorite(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }

            PostFavoriteId favoriteId = new PostFavoriteId(user.getUserId(), id);
            boolean isFavorited;

            if (postFavoriteRepository.existsById(favoriteId)) {
                postFavoriteRepository.deleteById(favoriteId);
                isFavorited = false;
            } else {
                PostFavorite favorite = new PostFavorite();
                favorite.setId(favoriteId);
                favorite.setFavoriteTime(LocalDateTime.now());
                postFavoriteRepository.save(favorite);
                isFavorited = true;
            }

            response.put("success", true);
            response.put("favorited", isFavorited);
            response.put("message", isFavorited ? "收藏成功" : "取消收藏");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 关注/取消关注用户
     */
    @PostMapping("/users/{id}/follow")
    public ResponseEntity<Map<String, Object>> toggleFollow(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }

            if (user.getUserId().equals(id)) {
                response.put("success", false);
                response.put("error", "不能关注自己");
                return ResponseEntity.badRequest().body(response);
            }

            UserFollowId followId = new UserFollowId(user.getUserId(), id);
            boolean isFollowing;

            if (userFollowRepository.existsById(followId)) {
                userFollowRepository.deleteById(followId);
                isFollowing = false;
            } else {
                UserFollow follow = new UserFollow();
                follow.setId(followId);
                follow.setFollowTime(LocalDateTime.now());
                userFollowRepository.save(follow);
                isFollowing = true;
            }

            response.put("success", true);
            response.put("following", isFollowing);
            response.put("message", isFollowing ? "关注成功" : "取消关注");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 置顶/取消置顶帖子（管理员）
     */
    @PostMapping("/admin/posts/{id}/pin")
    public ResponseEntity<Map<String, Object>> togglePin(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }

            if (user.getRole() != Role.ADMIN) {
                response.put("success", false);
                response.put("error", "权限不足");
                return ResponseEntity.status(403).body(response);
            }

            boolean isPinned = postService.togglePin(id);
            response.put("success", true);
            response.put("pinned", isPinned);
            response.put("message", isPinned ? "置顶成功" : "取消置顶");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 检查收藏状态
     */
    @GetMapping("/posts/{id}/favorite/status")
    public ResponseEntity<Map<String, Object>> checkFavoriteStatus(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            boolean isFavorited = false;
            if (user != null) {
                PostFavoriteId favoriteId = new PostFavoriteId(user.getUserId(), id);
                isFavorited = postFavoriteRepository.existsById(favoriteId);
            }
            response.put("success", true);
            response.put("favorited", isFavorited);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 检查关注状态
     */
    @GetMapping("/users/{id}/follow/status")
    public ResponseEntity<Map<String, Object>> checkFollowStatus(@PathVariable Integer id) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            boolean isFollowing = false;
            if (user != null) {
                UserFollowId followId = new UserFollowId(user.getUserId(), id);
                isFollowing = userFollowRepository.existsById(followId);
            }
            response.put("success", true);
            response.put("following", isFollowing);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取用户收藏的帖子
     */
    @GetMapping("/user/favorites")
    public ResponseEntity<Map<String, Object>> getUserFavorites() {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            List<PostFavorite> favorites = postFavoriteRepository.findByUserId(user.getUserId());
            List<Map<String, Object>> posts = new ArrayList<>();
            for (PostFavorite fav : favorites) {
                postRepository.findById(fav.getId().getPostId()).ifPresent(post -> {
                    Map<String, Object> postMap = new HashMap<>();
                    postMap.put("postId", post.getPostId());
                    postMap.put("postTitle", post.getPostTitle());
                    postMap.put("postAbstract", post.getPostAbstract());
                    postMap.put("uploadTime", post.getUploadTime());
                    postMap.put("likeCount", post.getLikeCount());
                    posts.add(postMap);
                });
            }
            response.put("success", true);
            response.put("favorites", posts);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取用户关注的人
     */
    @GetMapping("/user/following")
    public ResponseEntity<Map<String, Object>> getUserFollowing() {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            List<UserFollow> follows = userFollowRepository.findByFollowerId(user.getUserId());
            List<Map<String, Object>> users = new ArrayList<>();
            for (UserFollow follow : follows) {
                userRepository.findById(follow.getId().getFollowedId()).ifPresent(u -> {
                    Map<String, Object> userMap = new HashMap<>();
                    userMap.put("userId", u.getUserId());
                    userMap.put("userName", u.getUserName());
                    userMap.put("avatar", u.getAvatar());
                    users.add(userMap);
                });
            }
            response.put("success", true);
            response.put("following", users);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 批量上线帖子（草稿/仅自己可见 -> 已发布）
     */
    @PostMapping("/posts/batch/online")
    public ResponseEntity<Map<String, Object>> batchOnlinePosts(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            
            @SuppressWarnings("unchecked")
            List<Integer> postIds = (List<Integer>) request.get("postIds");
            if (postIds == null || postIds.isEmpty()) {
                response.put("success", false);
                response.put("error", "请选择要上线的帖子");
                return ResponseEntity.badRequest().body(response);
            }
            
            int successCount = 0;
            for (Integer postId : postIds) {
                try {
                    Post post = postRepository.findById(postId).orElse(null);
                    if (post != null && post.getAuthorId().equals(user.getUserId())) {
                        post.setPostStatus(PostStatus.已发布);
                        postRepository.save(post);
                        successCount++;
                    }
                } catch (Exception e) {
                    System.err.println("上线帖子失败，postId=" + postId + ": " + e.getMessage());
                }
            }
            
            response.put("success", true);
            response.put("message", "成功上线 " + successCount + " 个帖子");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 批量下线帖子（已发布 -> 仅自己可见）
     */
    @PostMapping("/posts/batch/offline")
    public ResponseEntity<Map<String, Object>> batchOfflinePosts(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            
            @SuppressWarnings("unchecked")
            List<Integer> postIds = (List<Integer>) request.get("postIds");
            if (postIds == null || postIds.isEmpty()) {
                response.put("success", false);
                response.put("error", "请选择要下线的帖子");
                return ResponseEntity.badRequest().body(response);
            }
            
            int successCount = 0;
            for (Integer postId : postIds) {
                try {
                    Post post = postRepository.findById(postId).orElse(null);
                    if (post != null && post.getAuthorId().equals(user.getUserId())) {
                        post.setPostStatus(PostStatus.仅自己可见);
                        postRepository.save(post);
                        successCount++;
                    }
                } catch (Exception e) {
                    System.err.println("下线帖子失败，postId=" + postId + ": " + e.getMessage());
                }
            }
            
            response.put("success", true);
            response.put("message", "成功下线 " + successCount + " 个帖子");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 批量删除帖子
     */
    @PostMapping("/posts/batch/delete")
    public ResponseEntity<Map<String, Object>> batchDeletePosts(@RequestBody Map<String, Object> request) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            
            @SuppressWarnings("unchecked")
            List<Integer> postIds = (List<Integer>) request.get("postIds");
            if (postIds == null || postIds.isEmpty()) {
                response.put("success", false);
                response.put("error", "请选择要删除的帖子");
                return ResponseEntity.badRequest().body(response);
            }
            
            int successCount = 0;
            for (Integer postId : postIds) {
                try {
                    postService.deletePost(postId, user.getUserId());
                    successCount++;
                } catch (Exception e) {
                    // 单个失败不影响其他
                    System.err.println("删除帖子失败，postId=" + postId + ": " + e.getMessage());
                }
            }
            
            response.put("success", true);
            response.put("message", "成功删除 " + successCount + " 个帖子");
            response.put("deletedCount", successCount);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * 获取用户草稿帖子
     */
    @GetMapping("/user/drafts")
    public ResponseEntity<Map<String, Object>> getUserDrafts() {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = getCurrentUser();
            if (user == null) {
                response.put("success", false);
                response.put("error", "请先登录");
                return ResponseEntity.status(401).body(response);
            }
            List<Post> drafts = postRepository.findByAuthorIdAndPostStatus(user.getUserId(), PostStatus.草稿);
            List<Map<String, Object>> posts = new ArrayList<>();
            for (Post post : drafts) {
                Map<String, Object> postMap = new HashMap<>();
                postMap.put("postId", post.getPostId());
                postMap.put("postTitle", post.getPostTitle());
                postMap.put("postAbstract", post.getPostAbstract());
                postMap.put("uploadTime", post.getUploadTime());
                posts.add(postMap);
            }
            response.put("success", true);
            response.put("drafts", posts);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
