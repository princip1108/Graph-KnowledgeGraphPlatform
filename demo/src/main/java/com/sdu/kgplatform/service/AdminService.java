package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.*;
import com.sdu.kgplatform.repository.*;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员服务 - 处理管理员相关业务逻辑
 */
@Service
public class AdminService {

    private final UserRepository userRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final BrowsingHistoryRepository browsingHistoryRepository;
    private final OauthAccountRepository oauthAccountRepository;
    private final UserFollowRepository userFollowRepository;
    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final GraphService graphService;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostFavoriteRepository postFavoriteRepository;

    public AdminService(UserRepository userRepository,
                        SearchHistoryRepository searchHistoryRepository,
                        BrowsingHistoryRepository browsingHistoryRepository,
                        OauthAccountRepository oauthAccountRepository,
                        UserFollowRepository userFollowRepository,
                        KnowledgeGraphRepository knowledgeGraphRepository,
                        @Lazy GraphService graphService,
                        PostRepository postRepository,
                        CommentRepository commentRepository,
                        PostLikeRepository postLikeRepository,
                        PostFavoriteRepository postFavoriteRepository) {
        this.userRepository = userRepository;
        this.searchHistoryRepository = searchHistoryRepository;
        this.browsingHistoryRepository = browsingHistoryRepository;
        this.oauthAccountRepository = oauthAccountRepository;
        this.userFollowRepository = userFollowRepository;
        this.knowledgeGraphRepository = knowledgeGraphRepository;
        this.graphService = graphService;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.postFavoriteRepository = postFavoriteRepository;
    }

    /**
     * 获取所有用户
     */
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    /**
     * 获取用户统计信息
     */
    public Map<String, Object> getUserStats() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalUsers = userRepository.count();
        long adminCount = userRepository.countByRole(Role.ADMIN);
        long userCount = userRepository.countByRole(Role.USER);
        long bannedCount = userRepository.countByUserStatus(UserStatus.BANNED);
        
        stats.put("totalUsers", totalUsers);
        stats.put("adminCount", adminCount);
        stats.put("userCount", userCount);
        stats.put("bannedCount", bannedCount);
        
        return stats;
    }

    /**
     * 搜索用户（支持关键词 + 角色/状态筛选 + 分页）
     */
    public Page<User> searchUsers(String keyword, String filter, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        boolean hasKeyword = keyword != null && !keyword.trim().isEmpty();
        String kw = hasKeyword ? keyword.trim() : "";

        if ("ADMIN".equals(filter)) {
            return hasKeyword ? userRepository.searchUsersByRole(kw, Role.ADMIN, pageable)
                             : userRepository.findByRole(Role.ADMIN, pageable);
        } else if ("USER".equals(filter)) {
            return hasKeyword ? userRepository.searchUsersByRole(kw, Role.USER, pageable)
                             : userRepository.findByRole(Role.USER, pageable);
        } else if ("BANNED".equals(filter)) {
            return hasKeyword ? userRepository.searchUsersByStatus(kw, UserStatus.BANNED, pageable)
                             : userRepository.findByUserStatus(UserStatus.BANNED, pageable);
        } else {
            return hasKeyword ? userRepository.searchUsers(kw, pageable)
                             : userRepository.findAll(pageable);
        }
    }

    /**
     * 封禁用户（支持时长，hours=0 表示永久封禁）
     */
    @Transactional
    public void banUser(Integer userId, int hours) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        if (user.getRole() == Role.ADMIN) {
            throw new IllegalArgumentException("不能封禁管理员");
        }
        user.setUserStatus(UserStatus.BANNED);
        if (hours > 0) {
            user.setBannedUntil(LocalDateTime.now().plusHours(hours));
        } else {
            user.setBannedUntil(null); // null 表示永久封禁
        }
        userRepository.save(user);
    }

    /**
     * 解封用户
     */
    @Transactional
    public void unbanUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        user.setUserStatus(UserStatus.OFFLINE);
        user.setBannedUntil(null);
        userRepository.save(user);
    }

    /**
     * 更新用户角色（管理员不能降级自己）
     */
    @Transactional
    public void updateUserRole(Integer userId, Role role, Integer operatorId) {
        if (userId.equals(operatorId)) {
            throw new IllegalArgumentException("不能修改自己的角色");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        user.setRole(role);
        userRepository.save(user);
    }

    /**
     * 更新用户状态
     */
    @Transactional
    public void updateUserStatus(Integer userId, String status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));
        try {
            UserStatus userStatus = UserStatus.valueOf(status.toUpperCase());
            user.setUserStatus(userStatus);
            userRepository.save(user);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("无效的状态: " + status);
        }
    }

    /**
     * 注销用户（软删除 + 匿名化 + 级联清理）
     */
    @Transactional
    public void deleteUser(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在: " + userId));

        // ===== 第一阶段：清理私密数据 =====
        searchHistoryRepository.deleteByUserId(userId);
        browsingHistoryRepository.deleteByUserId(userId);
        oauthAccountRepository.deleteByUserId(userId);

        // 删除用户关注关系（双向）
        userFollowRepository.deleteByIdFollowerId(userId);
        userFollowRepository.deleteByIdFollowedId(userId);

        // ===== 第二阶段：清理草稿内容 =====

        // 删除草稿图谱（含 Neo4j 节点）
        List<KnowledgeGraph> draftGraphs = knowledgeGraphRepository
                .findByUploaderIdAndStatus(userId, GraphStatus.DRAFT, Pageable.unpaged())
                .getContent();
        for (KnowledgeGraph g : draftGraphs) {
            try {
                graphService.adminDeleteGraph(g.getGraphId());
            } catch (Exception e) {
                // 单个图谱删除失败不影响整体流程
            }
        }

        // 删除草稿帖子及其关联数据
        List<Post> draftPosts = postRepository.findByAuthorIdAndPostStatus(userId, PostStatus.草稿);
        for (Post p : draftPosts) {
            commentRepository.deleteByPostId(p.getPostId());
            browsingHistoryRepository.deleteByPostId(p.getPostId());
        }
        postRepository.deleteAll(draftPosts);

        // ===== 第三阶段：软删除用户 + 匿名化 =====
        user.setUserStatus(UserStatus.DELETED);
        user.setUserName("已注销用户");
        user.setEmail("deactivated_" + userId + "@deleted.local");
        user.setPhone(null);
        user.setAvatar(null);
        user.setBio(null);
        user.setProfile(null);
        user.setNickname(null);
        user.setInstitution(null);
        user.setPasswordHash("DEACTIVATED");
        user.setGithubId(null);
        user.setOauthProvider(null);
        userRepository.save(user);
    }
}
