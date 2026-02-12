package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.*;
import com.sdu.kgplatform.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 帖子服务类
 */
@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private PostLikeRepository postLikeRepository;

    @Autowired
    private TagRepository tagRepository;

    @Autowired
    private PostTagRepository postTagRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private KnowledgeGraphRepository knowledgeGraphRepository;

    @Autowired
    private com.sdu.kgplatform.repository.CategoryRepository categoryRepository;

    @Autowired
    private BrowsingHistoryRepository browsingHistoryRepository;

    /**
     * 获取置顶帖子
     */
    public List<Post> getPinnedPosts() {
        return postRepository.findByPostStatusAndIsPinnedOrderByUploadTimeDesc(PostStatus.已发布, true);
    }

    /**
     * 获取帖子列表（分页）
     */
    public Page<Post> getPostList(int page, int size, String sortBy) {
        return getPostList(page, size, sortBy, null);
    }

    public Page<Post> getPostList(int page, int size, String sortBy, Integer categoryId) {
        Sort sort;
        switch (sortBy) {
            case "popular":
                sort = Sort.by(Sort.Direction.DESC, "likeCount");
                break;
            case "latest":
            default:
                sort = Sort.by(Sort.Direction.DESC, "uploadTime");
                break;
        }
        Pageable pageable = PageRequest.of(page, size, sort);

        if (categoryId != null) {
            return postRepository.findByPostStatusAndCategoryId(PostStatus.已发布, categoryId, pageable);
        } else {
            return postRepository.findByPostStatus(PostStatus.已发布, pageable);
        }
    }

    /**
     * 搜索帖子
     */
    public Page<Post> searchPosts(String keyword, int page, int size) {
        return searchPosts(keyword, page, size, null);
    }

    public Page<Post> searchPosts(String keyword, int page, int size, Integer categoryId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "uploadTime"));
        if (categoryId != null) {
            return postRepository.searchByKeywordAndCategory(PostStatus.已发布, keyword, categoryId, pageable);
        } else {
            return postRepository.searchByKeyword(PostStatus.已发布, keyword, pageable);
        }
    }

    /**
     * 获取帖子详情
     */
    public Optional<Post> getPostById(Integer postId) {
        return postRepository.findById(postId);
    }

    /**
     * 获取帖子详情（包含作者信息、评论数等）
     */
    public Map<String, Object> getPostDetail(Integer postId, Integer currentUserId) {
        Optional<Post> postOpt = postRepository.findById(postId);
        if (postOpt.isEmpty()) {
            return null;
        }

        Post post = postOpt.get();
        Map<String, Object> result = new HashMap<>();
        result.put("post", post);

        // 获取作者信息
        userRepository.findById(post.getAuthorId()).ifPresent(author -> {
            result.put("authorName", author.getUserName());
            result.put("authorAvatar", author.getAvatar());
        });

        // 获取评论数
        long commentCount = commentRepository.countByPostId(postId);
        result.put("commentCount", commentCount);

        // 检查当前用户是否已点赞
        if (currentUserId != null) {
            PostLikeId likeId = new PostLikeId();
            likeId.setUserId(currentUserId);
            likeId.setPostId(postId);
            result.put("liked", postLikeRepository.existsById(likeId));
        } else {
            result.put("liked", false);
        }

        // 获取帖子标签
        List<Integer> tagIds = postTagRepository.findTagIdsByPostId(postId);
        if (!tagIds.isEmpty()) {
            List<Tag> tags = tagRepository.findAllById(tagIds);
            result.put("tags", tags);
        } else {
            result.put("tags", Collections.emptyList());
        }

        if (post.getGraphId() != null) {
            knowledgeGraphRepository.findById(post.getGraphId()).ifPresent(graph -> {
                result.put("graphName", graph.getName());
            });
        }

        // 获取分类名称
        if (post.getCategoryId() != null)

        {
            categoryRepository.findById(post.getCategoryId()).ifPresent(category -> {
                result.put("categoryName", category.getName());
            });
        }

        return result;
    }

    /**
     * 发布帖子
     */
    @Transactional
    public Post createPost(Integer authorId, String title, String postAbstract, String content, List<String> tagNames) {
        return createPost(authorId, title, postAbstract, content, tagNames, null, null);
    }

    /**
     * 发布帖子（支持关联图谱和分类）
     */
    @Transactional
    public Post createPost(Integer authorId, String title, String postAbstract, String content, List<String> tagNames,
            Integer graphId, Integer categoryId) {
        return createPost(authorId, title, postAbstract, content, tagNames, graphId, categoryId, "other");
    }

    /**
     * 发布帖子（支持关联图谱、分类ID和分类代码）
     */
    @Transactional
    public Post createPost(Integer authorId, String title, String postAbstract, String content, List<String> tagNames,
            Integer graphId, Integer categoryId, String category) {
        Post post = new Post();
        post.setAuthorId(authorId);
        post.setPostTitle(title);
        post.setPostAbstract(postAbstract);
        post.setPostText(content);
        post.setPostStatus(PostStatus.已发布);
        post.setLikeCount(0);
        post.setUploadTime(LocalDateTime.now());
        post.setGraphId(graphId);
        post.setCategoryId(categoryId);
        post.setCategory(category != null ? category : "other");

        Post savedPost = postRepository.save(post);

        // 处理标签
        if (tagNames != null && !tagNames.isEmpty()) {
            for (String tagName : tagNames) {
                Tag tag = tagRepository.findByTagName(tagName)
                        .orElseGet(() -> {
                            Tag newTag = new Tag();
                            newTag.setTagName(tagName);
                            newTag.setCreatedAt(LocalDateTime.now());
                            newTag.setUsageCount(0);
                            return tagRepository.save(newTag);
                        });

                // 增加标签使用次数
                tag.setUsageCount(tag.getUsageCount() + 1);
                tagRepository.save(tag);

                // 创建帖子-标签关联
                PostTagId postTagId = new PostTagId();
                postTagId.setPostId(savedPost.getPostId());
                postTagId.setTagId(tag.getTagId());
                PostTag postTag = new PostTag();
                postTag.setId(postTagId);
                postTagRepository.save(postTag);
            }
        }

        return savedPost;
    }

    /**
     * 编辑帖子
     */
    @Transactional
    public Post updatePost(Integer postId, Integer userId, String title, String postAbstract, String content,
            List<String> tagNames) {
        return updatePost(postId, userId, title, postAbstract, content, tagNames, null);
    }

    @Transactional
    public Post updatePost(Integer postId, Integer userId, String title, String postAbstract, String content,
            List<String> tagNames, Integer categoryId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));

        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException("无权编辑此帖子");
        }

        post.setPostTitle(title);
        post.setPostAbstract(postAbstract);
        post.setPostText(content);

        // 更新标签
        if (tagNames != null) {
            // 删除旧标签关联
            postTagRepository.deleteByPostId(postId);

            // 添加新标签
            for (String tagName : tagNames) {
                Tag tag = tagRepository.findByTagName(tagName)
                        .orElseGet(() -> {
                            Tag newTag = new Tag();
                            newTag.setTagName(tagName);
                            newTag.setCreatedAt(LocalDateTime.now());
                            newTag.setUsageCount(0);
                            return tagRepository.save(newTag);
                        });

                // 增加标签使用次数
                tag.setUsageCount(tag.getUsageCount() + 1);
                tagRepository.save(tag);

                // 创建帖子-标签关联
                PostTagId postTagId = new PostTagId();
                postTagId.setPostId(post.getPostId());
                postTagId.setTagId(tag.getTagId());
                PostTag postTag = new PostTag();
                postTag.setId(postTagId);
                postTagRepository.save(postTag);
            }
        }

        return postRepository.save(post);
    }

    /**
     * 删除帖子
     */
    @Transactional
    public void deletePost(Integer postId, Integer userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));

        // 检查权限：作者本人或管理员可删除
        boolean isAuthor = post.getAuthorId().equals(userId);
        boolean isAdmin = false;
        if (!isAuthor) {
            var userOpt = userRepository.findById(userId);
            if (userOpt.isPresent() && userOpt.get().getRole() == Role.ADMIN) {
                isAdmin = true;
            }
        }
        if (!isAuthor && !isAdmin) {
            throw new IllegalArgumentException("无权删除此帖子");
        }

        // 删除浏览历史（避免外键约束错误）
        browsingHistoryRepository.deleteByPostId(postId);

        // 删除相关评论
        commentRepository.deleteByPostId(postId);

        // 删除帖子
        postRepository.delete(post);
    }

    /**
     * 点赞/取消点赞
     */
    @Transactional
    public boolean toggleLike(Integer postId, Integer userId) {
        PostLikeId likeId = new PostLikeId();
        likeId.setUserId(userId);
        likeId.setPostId(postId);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));

        if (postLikeRepository.existsById(likeId)) {
            // 取消点赞
            postLikeRepository.deleteById(likeId);
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            postRepository.save(post);
            return false;
        } else {
            // 点赞
            PostLike like = new PostLike();
            like.setId(likeId);
            like.setLikeTime(LocalDateTime.now());
            postLikeRepository.save(like);
            post.setLikeCount(post.getLikeCount() + 1);
            postRepository.save(post);
            return true;
        }
    }

    /**
     * 获取论坛统计数据
     */
    public Map<String, Object> getForumStats() {
        Map<String, Object> stats = new HashMap<>();

        // 总帖子数
        long totalPosts = postRepository.countByPostStatus(PostStatus.已发布);
        stats.put("totalPosts", totalPosts);

        // 总评论数（仅统计已发布帖子的评论）
        long totalComments = commentRepository.countByPostStatus(PostStatus.已发布);
        stats.put("totalComments", totalComments);

        // 今日新帖
        LocalDateTime startOfDay = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
        long todayPosts = postRepository.countTodayPosts(PostStatus.已发布, startOfDay);
        stats.put("todayPosts", todayPosts);

        return stats;
    }

    /**
     * 获取用户的帖子
     */
    public Page<Post> getUserPosts(Integer userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return postRepository.findByAuthorIdOrderByUploadTimeDesc(userId, pageable);
    }

    /**
     * 获取热门标签
     */
    public List<Tag> getHotTags() {
        return tagRepository.findTop20ByOrderByUsageCountDesc();
    }

    /**
     * 置顶/取消置顶帖子
     */
    @Transactional
    public boolean togglePin(Integer postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));

        boolean newPinStatus = !Boolean.TRUE.equals(post.getIsPinned());
        post.setIsPinned(newPinStatus);
        postRepository.save(post);
        return newPinStatus;
    }

    /**
     * 获取图谱的相关帖子（推荐）
     * P1: 直接关联该图谱的帖子
     * P2: 同领域的帖子（补足）
     * 降级: domain 为 "other" 或 null 时，P2 改为热门帖子
     */
    public List<Map<String, Object>> getRelatedPostsForGraph(Integer graphId, int size) {
        // 获取图谱信息
        var graphOpt = knowledgeGraphRepository.findById(graphId);
        if (graphOpt.isEmpty()) {
            return Collections.emptyList();
        }
        var graph = graphOpt.get();
        String domain = graph.getDomain();

        List<Post> result = new ArrayList<>();
        Set<Integer> addedIds = new HashSet<>();

        // P1: 直接关联帖子
        List<Post> directPosts = postRepository.findByGraphIdAndPostStatusOrderByUploadTimeDesc(graphId, PostStatus.已发布);
        for (Post p : directPosts) {
            if (result.size() >= size) break;
            result.add(p);
            addedIds.add(p.getPostId());
        }

        // P2: 同领域帖子补足
        int remaining = size - result.size();
        if (remaining > 0) {
            Pageable pageable = PageRequest.of(0, remaining + 5); // 多查几条以防重复
            List<Post> domainPosts;
            if (domain == null || "other".equalsIgnoreCase(domain)) {
                // 降级：热门帖子
                domainPosts = postRepository.findByPostStatusOrderByLikeCountDesc(PostStatus.已发布, pageable).getContent();
            } else {
                domainPosts = postRepository.findByCategoryAndPostStatusExcludingGraph(domain, PostStatus.已发布, graphId, pageable);
            }
            for (Post p : domainPosts) {
                if (result.size() >= size) break;
                if (!addedIds.contains(p.getPostId())) {
                    result.add(p);
                    addedIds.add(p.getPostId());
                }
            }
        }

        // 转换为带作者信息的 Map
        List<Map<String, Object>> postList = new ArrayList<>();
        for (Post post : result) {
            Map<String, Object> postMap = new HashMap<>();
            postMap.put("postId", post.getPostId());
            postMap.put("postTitle", post.getPostTitle());
            postMap.put("postAbstract", post.getPostAbstract());
            postMap.put("uploadTime", post.getUploadTime());
            postMap.put("likeCount", post.getLikeCount());
            postMap.put("viewCount", post.getViewCount());
            postMap.put("graphId", post.getGraphId());
            postMap.put("category", post.getCategory());
            postMap.put("isDirectlyRelated", graphId.equals(post.getGraphId()));

            userRepository.findById(post.getAuthorId()).ifPresent(author -> {
                postMap.put("authorName", author.getUserName());
                postMap.put("authorAvatar", author.getAvatar());
            });

            // 获取评论数
            long commentCount = commentRepository.countByPostId(post.getPostId());
            postMap.put("commentCount", commentCount);

            postList.add(postMap);
        }

        return postList;
    }

    /**
     * 增加阅读量
     */
    @Transactional
    public void incrementViewCount(Integer postId) {
        Post post = postRepository.findById(postId).orElse(null);
        if (post != null) {
            post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
            postRepository.save(post);
        }
    }

    /**
     * 获取热门帖子（Hacker News 风格热度算法）
     * hotScore = (views×1 + likes×5 + comments×3 + favorites×2 + 1) / (daysSinceUpload + 2)^1.5
     */
    public List<Map<String, Object>> getHotPosts(int size) {
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        List<Post> recentPosts = postRepository.findByPostStatusAndUploadTimeAfter(PostStatus.已发布, since);

        return recentPosts.stream().map(post -> {
            long views = post.getViewCount() != null ? post.getViewCount() : 0;
            long likes = post.getLikeCount() != null ? post.getLikeCount() : 0;
            long favorites = post.getFavoriteCount() != null ? post.getFavoriteCount() : 0;
            long comments = commentRepository.countByPostId(post.getPostId());

            double interactions = views * 1.0 + likes * 5.0 + comments * 3.0 + favorites * 2.0 + 1;
            double daysSince = java.time.Duration.between(post.getUploadTime(), LocalDateTime.now()).toHours() / 24.0 + 2;
            double hotScore = interactions / Math.pow(daysSince, 1.5);

            Map<String, Object> map = new HashMap<>();
            map.put("postId", post.getPostId());
            map.put("postTitle", post.getPostTitle());
            map.put("viewCount", views);
            map.put("likeCount", likes);
            map.put("commentCount", comments);
            map.put("hotScore", hotScore);
            boolean isNew = post.getUploadTime() != null &&
                    post.getUploadTime().isAfter(LocalDateTime.now().minusHours(24));
            map.put("badge", isNew ? "new" : "hot");
            return map;
        })
        .sorted((a, b) -> Double.compare((double) b.get("hotScore"), (double) a.get("hotScore")))
        .limit(size)
        .collect(java.util.stream.Collectors.toList());
    }
}
