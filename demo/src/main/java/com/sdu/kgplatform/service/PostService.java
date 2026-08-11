package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.*;
import com.sdu.kgplatform.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PostService {

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentRepository commentRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostFavoriteRepository postFavoriteRepository;
    private final PostTagRepository postTagRepository;
    private final TagRepository tagRepository;
    private final KnowledgeGraphRepository knowledgeGraphRepository;
    private final CategoryRepository categoryRepository;
    private final BrowsingHistoryRepository browsingHistoryRepository;
    private final HistoryService historyService;

    public PostService(PostRepository postRepository,
                       UserRepository userRepository,
                       CommentRepository commentRepository,
                       PostLikeRepository postLikeRepository,
                       PostFavoriteRepository postFavoriteRepository,
                       PostTagRepository postTagRepository,
                       TagRepository tagRepository,
                       KnowledgeGraphRepository knowledgeGraphRepository,
                       CategoryRepository categoryRepository,
                       BrowsingHistoryRepository browsingHistoryRepository,
                       HistoryService historyService) {
        this.postRepository = postRepository;
        this.userRepository = userRepository;
        this.commentRepository = commentRepository;
        this.postLikeRepository = postLikeRepository;
        this.postFavoriteRepository = postFavoriteRepository;
        this.postTagRepository = postTagRepository;
        this.tagRepository = tagRepository;
        this.knowledgeGraphRepository = knowledgeGraphRepository;
        this.categoryRepository = categoryRepository;
        this.browsingHistoryRepository = browsingHistoryRepository;
        this.historyService = historyService;
    }

    public Page<Map<String, Object>> listPosts(String keyword, String sort, String category, int page, int size, Integer viewerUserId) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), resolveSort(sort));
        String normalizedKeyword = keyword == null ? "" : keyword.trim();
        String normalizedCategory = category == null ? "" : category.trim();
        Page<Post> posts;
        if (normalizedCategory.isBlank()) {
            posts = normalizedKeyword.isBlank()
                    ? postRepository.findByPostStatus(PostStatus.已发布, pageable)
                    : postRepository.searchByKeyword(PostStatus.已发布, normalizedKeyword, pageable);
        } else {
            posts = normalizedKeyword.isBlank()
                    ? postRepository.findByPostStatusAndCategory(PostStatus.已发布, normalizedCategory, pageable)
                    : postRepository.searchByKeywordAndCategory(PostStatus.已发布, normalizedKeyword, normalizedCategory, pageable);
        }

        Map<Integer, User> authors = loadAuthors(posts.getContent());
        Map<Integer, Long> commentCounts = loadCommentCounts(posts.getContent());
        Set<Integer> likedPostIds = loadLikedPostIds(posts.getContent(), viewerUserId);
        return posts.map(post -> toPostCard(post, authors, commentCounts, likedPostIds));
    }

    public List<Map<String, Object>> listPinnedPosts(Integer viewerUserId) {
        List<Post> posts = postRepository.findByPostStatusAndIsPinnedOrderByUploadTimeDesc(PostStatus.已发布, Boolean.TRUE);
        Map<Integer, User> authors = loadAuthors(posts);
        Map<Integer, Long> commentCounts = loadCommentCounts(posts);
        Set<Integer> likedPostIds = loadLikedPostIds(posts, viewerUserId);
        return posts
                .stream()
                .map(post -> toPostCard(post, authors, commentCounts, likedPostIds))
                .toList();
    }

    public Map<String, Object> getForumStats() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        return Map.of(
                "totalPosts", postRepository.countByPostStatus(PostStatus.已发布),
                "totalComments", commentRepository.count(),
                "todayPosts", postRepository.countTodayPosts(PostStatus.已发布, startOfDay));
    }

    public Map<String, Object> getPostDetail(Integer postId, Integer viewerUserId) {
        return getPostDetail(postId, viewerUserId, false);
    }

    public Map<String, Object> getPostDetail(Integer postId, Integer viewerUserId, boolean admin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (!canViewPost(post, viewerUserId, admin)) {
            throw new IllegalArgumentException("无权访问该帖子");
        }

        boolean shouldCountView = viewerUserId == null
                || historyService.recordBrowsingIfStale(viewerUserId, ResourceType.post, postId);
        if (shouldCountView) {
            post.setViewCount((post.getViewCount() == null ? 0 : post.getViewCount()) + 1);
            postRepository.save(post);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("post", post);
        result.put("authorName", getAuthorName(post.getAuthorId()));
        result.put("authorAvatar", getAuthorAvatar(post.getAuthorId()));
        result.put("commentCount", commentRepository.countByPostId(postId));
        result.put("liked", viewerUserId != null && postLikeRepository.existsById(new PostLikeId(viewerUserId, postId)));
        result.put("tags", getTagsForPost(postId));
        result.put("graphName", getGraphName(post.getGraphId()));
        result.put("categoryName", getCategoryName(post.getCategory(), post.getCategoryId()));
        return result;
    }

    public Map<String, Object> getPostSummaryWithoutViewIncrement(Integer postId, Integer viewerUserId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (!canViewPost(post, viewerUserId, false)) {
            throw new IllegalArgumentException("无权访问该帖子");
        }

        Map<Integer, User> authors = loadAuthors(List.of(post));
        Map<Integer, Long> commentCounts = loadCommentCounts(List.of(post));
        Set<Integer> likedPostIds = loadLikedPostIds(List.of(post), viewerUserId);
        return toPostCard(post, authors, commentCounts, likedPostIds);
    }

    public Page<Map<String, Object>> getFavoritePostSummaries(Integer userId, int page, int size) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
        Page<Post> posts = postFavoriteRepository.findVisibleFavoritePosts(
                userId,
                PostStatus.已发布,
                pageable);
        Map<Integer, User> authors = loadAuthors(posts.getContent());
        Map<Integer, Long> commentCounts = loadCommentCounts(posts.getContent());
        Set<Integer> likedPostIds = loadLikedPostIds(posts.getContent(), userId);
        return posts.map(post -> toPostCard(post, authors, commentCounts, likedPostIds));
    }

    @Transactional
    public Map<String, Object> createPost(Integer authorId, Map<String, Object> body) {
        Post post = new Post();
        post.setAuthorId(authorId);
        applyPostRequest(post, body);
        post.setPostStatus(body.containsKey("status") ? parsePostStatus(String.valueOf(body.get("status"))) : PostStatus.已发布);
        post.setUploadTime(LocalDateTime.now());
        post.setLikeCount(0);
        post.setViewCount(0);
        post.setFavoriteCount(0);
        post.setIsPinned(Boolean.FALSE);

        Post saved = postRepository.save(post);
        syncTags(saved.getPostId(), extractTags(body));
        return Map.of("success", true, "post", saved);
    }

    @Transactional
    public Map<String, Object> updatePost(Integer postId, Integer userId, Map<String, Object> body, boolean admin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (!admin && !Objects.equals(post.getAuthorId(), userId)) {
            throw new IllegalArgumentException("无权编辑该帖子");
        }

        applyPostRequest(post, body);
        if (body.containsKey("status")) {
            post.setPostStatus(parsePostStatus(String.valueOf(body.get("status"))));
        }

        Post saved = postRepository.save(post);
        if (body.containsKey("tags")) {
            syncTags(saved.getPostId(), extractTags(body));
        }
        return Map.of("success", true, "post", saved);
    }

    @Transactional
    public void deletePost(Integer postId, Integer userId, boolean admin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (!admin && !Objects.equals(post.getAuthorId(), userId)) {
            throw new IllegalArgumentException("无权删除该帖子");
        }

        commentRepository.deleteByPostId(postId);
        postLikeRepository.deleteByPostId(postId);
        postTagRepository.deleteByPostId(postId);
        browsingHistoryRepository.deleteByPostId(postId);
        postRepository.delete(post);
    }

    @Transactional
    public Map<String, Object> toggleLike(Integer postId, Integer userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (post.getPostStatus() != PostStatus.已发布) {
            throw new IllegalArgumentException("该帖子当前不可点赞");
        }

        PostLikeId id = new PostLikeId(userId, postId);
        boolean liked = !postLikeRepository.existsById(id);
        if (liked) {
            postLikeRepository.save(new PostLike(id, LocalDateTime.now()));
        } else {
            postLikeRepository.deleteById(id);
        }
        long count = postLikeRepository.countByPostId(postId);
        post.setLikeCount((int) count);
        postRepository.save(post);
        return Map.of("success", true, "liked", liked, "likeCount", count);
    }

    @Transactional
    public Map<String, Object> toggleFavorite(Integer postId, Integer userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (post.getPostStatus() != PostStatus.已发布) {
            throw new IllegalArgumentException("该帖子当前不可收藏");
        }

        PostFavoriteId id = new PostFavoriteId(userId, postId);
        boolean favorited = !postFavoriteRepository.existsById(id);
        if (favorited) {
            postFavoriteRepository.save(new PostFavorite(id, LocalDateTime.now()));
        } else {
            postFavoriteRepository.deleteById(id);
        }
        long count = postFavoriteRepository.countByIdPostId(postId);
        post.setFavoriteCount((int) count);
        postRepository.save(post);
        return Map.of("success", true, "favorited", favorited, "favoriteCount", count);
    }

    public Map<String, Object> getFavoriteStatus(Integer postId, Integer userId) {
        return getFavoriteStatus(postId, userId, false);
    }

    public Map<String, Object> getFavoriteStatus(Integer postId, Integer userId, boolean admin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (!canViewPost(post, userId, admin)) {
            throw new IllegalArgumentException("无权访问该帖子");
        }
        return Map.of(
                "success", true,
                "favorited", postFavoriteRepository.existsById(new PostFavoriteId(userId, postId)));
    }

    @Transactional
    public Map<String, Object> togglePin(Integer postId, Integer userId, boolean admin) {
        if (!admin) {
            throw new IllegalArgumentException("无权置顶帖子");
        }
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        post.setIsPinned(!Boolean.TRUE.equals(post.getIsPinned()));
        postRepository.save(post);
        return Map.of("success", true, "pinned", Boolean.TRUE.equals(post.getIsPinned()));
    }

    public Page<Post> getPostsByUser(Integer userId, int page, int size) {
        return getPostsByUser(userId, page, size, null, false);
    }

    public Page<Post> getPostsByUser(Integer userId, int page, int size, Integer viewerUserId, boolean admin) {
        return getPostsByUser(userId, page, size, null, null, viewerUserId, admin);
    }

    public Page<Post> getPostsByUser(Integer userId, int page, int size, String status, String keyword,
            Integer viewerUserId, boolean admin) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size), Sort.by(Sort.Direction.DESC, "uploadTime"));
        String normalizedStatus = status == null ? "" : status.trim();
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase();

        if (normalizedStatus.isBlank() && normalizedKeyword.isBlank()) {
            if (admin || Objects.equals(userId, viewerUserId)) {
                return postRepository.findByAuthorIdOrderByUploadTimeDesc(userId, pageable);
            }
            return postRepository.findByAuthorIdAndPostStatusOrderByUploadTimeDesc(userId, PostStatus.已发布, pageable);
        }

        org.springframework.data.jpa.domain.Specification<Post> spec = (root, query, criteriaBuilder) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("authorId"), userId));

            if (admin || Objects.equals(userId, viewerUserId)) {
                if (!normalizedStatus.isBlank() && !"all".equalsIgnoreCase(normalizedStatus)) {
                    predicates.add(criteriaBuilder.equal(root.get("postStatus"), parsePostStatus(normalizedStatus)));
                }
            } else {
                predicates.add(criteriaBuilder.equal(root.get("postStatus"), PostStatus.已发布));
            }

            if (!normalizedKeyword.isBlank()) {
                String pattern = "%" + normalizedKeyword + "%";
                predicates.add(criteriaBuilder.or(
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("postTitle")), pattern),
                        criteriaBuilder.like(criteriaBuilder.lower(root.get("postAbstract")), pattern)));
            }

            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        return postRepository.findAll(spec, pageable);
    }

    public List<Map<String, Object>> getRelatedPosts(Integer graphId, int size, Integer viewerUserId) {
        List<Post> posts = postRepository.findByGraphIdAndPostStatusOrderByUploadTimeDesc(
                graphId,
                PostStatus.已发布,
                PageRequest.of(0, normalizeSize(size)));
        Map<Integer, User> authors = loadAuthors(posts);
        Map<Integer, Long> commentCounts = loadCommentCounts(posts);
        Set<Integer> likedPostIds = loadLikedPostIds(posts, viewerUserId);
        return posts
                .stream()
                .map(post -> toPostCard(post, authors, commentCounts, likedPostIds))
                .toList();
    }

    @Transactional
    public int batchUpdateStatus(List<Integer> postIds, PostStatus status, Integer userId, boolean admin) {
        int success = 0;
        for (Integer postId : postIds) {
            Optional<Post> postOpt = postRepository.findById(postId);
            if (postOpt.isEmpty()) {
                continue;
            }
            Post post = postOpt.get();
            if (!admin && !Objects.equals(post.getAuthorId(), userId)) {
                continue;
            }
            post.setPostStatus(status);
            postRepository.save(post);
            success++;
        }
        return success;
    }

    @Transactional
    public int batchDelete(List<Integer> postIds, Integer userId, boolean admin) {
        int success = 0;
        for (Integer postId : postIds) {
            try {
                deletePost(postId, userId, admin);
                success++;
            } catch (Exception ignored) {
            }
        }
        return success;
    }

    public List<Tag> getTagsForPost(Integer postId) {
        List<Integer> tagIds = postTagRepository.findTagIdsByPostId(postId);
        if (tagIds.isEmpty()) {
            return List.of();
        }
        return tagRepository.findAllById(tagIds);
    }

    private Map<String, Object> toPostCard(Post post, Integer viewerUserId) {
        Map<Integer, User> authors = loadAuthors(List.of(post));
        Map<Integer, Long> commentCounts = loadCommentCounts(List.of(post));
        Set<Integer> likedPostIds = loadLikedPostIds(List.of(post), viewerUserId);
        return toPostCard(post, authors, commentCounts, likedPostIds);
    }

    private Map<String, Object> toPostCard(
            Post post,
            Map<Integer, User> authors,
            Map<Integer, Long> commentCounts,
            Set<Integer> likedPostIds) {
        User author = authors.get(post.getAuthorId());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("postId", post.getPostId());
        map.put("postTitle", post.getPostTitle());
        map.put("postAbstract", post.getPostAbstract());
        map.put("postStatus", post.getPostStatus() != null ? post.getPostStatus().name() : null);
        map.put("uploadTime", post.getUploadTime() != null ? post.getUploadTime().toString() : null);
        map.put("createdAt", post.getUploadTime() != null ? post.getUploadTime().toString() : null);
        map.put("authorId", post.getAuthorId());
        map.put("authorName", author != null ? author.getUserName() : "匿名");
        map.put("authorAvatar", author != null ? author.getAvatar() : null);
        map.put("likeCount", post.getLikeCount() != null ? post.getLikeCount() : 0);
        map.put("commentCount", commentCounts.getOrDefault(post.getPostId(), 0L));
        map.put("liked", likedPostIds.contains(post.getPostId()));
        map.put("isPinned", Boolean.TRUE.equals(post.getIsPinned()));
        return map;
    }

    private Map<Integer, User> loadAuthors(List<Post> posts) {
        List<Integer> authorIds = posts.stream()
                .map(Post::getAuthorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (authorIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, User> authors = new HashMap<>();
        userRepository.findAllById(authorIds)
                .forEach(user -> authors.put(user.getUserId(), user));
        return authors;
    }

    private Map<Integer, Long> loadCommentCounts(List<Post> posts) {
        List<Integer> postIds = postIds(posts);
        if (postIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Long> counts = new HashMap<>();
        commentRepository.countByPostIds(postIds)
                .forEach(row -> counts.put((Integer) row[0], (Long) row[1]));
        return counts;
    }

    private Set<Integer> loadLikedPostIds(List<Post> posts, Integer viewerUserId) {
        if (viewerUserId == null) {
            return Set.of();
        }
        List<Integer> postIds = postIds(posts);
        if (postIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(postLikeRepository.findLikedPostIds(viewerUserId, postIds));
    }

    private List<Integer> postIds(List<Post> posts) {
        return posts.stream()
                .map(Post::getPostId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    private boolean canViewPost(Post post, Integer viewerUserId, boolean admin) {
        if (post.getPostStatus() == PostStatus.已发布) {
            return true;
        }
        return admin || (viewerUserId != null && Objects.equals(post.getAuthorId(), viewerUserId));
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

    private Sort resolveSort(String sort) {
        if ("popular".equalsIgnoreCase(sort) || "likes".equalsIgnoreCase(sort) || "hot".equalsIgnoreCase(sort)) {
            return Sort.by(Sort.Direction.DESC, "likeCount").and(Sort.by(Sort.Direction.DESC, "uploadTime"));
        }
        return Sort.by(Sort.Direction.DESC, "uploadTime");
    }

    private void applyPostRequest(Post post, Map<String, Object> body) {
        String title = asString(body.get("title"));
        String content = asString(body.get("content"));
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("帖子标题不能为空");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("帖子正文不能为空");
        }
        post.setPostTitle(title.trim());
        post.setPostText(content);
        post.setPostAbstract(asString(body.get("abstract")));
        post.setGraphId(asInteger(body.get("graphId")));
        post.setCategory(asString(body.get("category")));
        post.setCategoryId(resolveCategoryId(post.getCategory()));
    }

    private Integer resolveCategoryId(String categoryCode) {
        if (categoryCode == null || categoryCode.isBlank()) {
            return null;
        }
        return categoryRepository.findByTypeOrderByPriorityDesc("POST").stream()
                .filter(category -> categoryCode.equalsIgnoreCase(category.getName()) || categoryCode.equalsIgnoreCase(category.getType()))
                .map(Category::getCategoryId)
                .findFirst()
                .orElse(null);
    }

    private void syncTags(Integer postId, List<String> tags) {
        postTagRepository.deleteByPostId(postId);
        if (tags.isEmpty()) {
            return;
        }
        for (String tagName : tags) {
            Tag tag = tagRepository.findByTagName(tagName)
                    .orElseGet(() -> tagRepository.save(new Tag(null, tagName, LocalDateTime.now(), 0)));
            tag.setUsageCount((tag.getUsageCount() == null ? 0 : tag.getUsageCount()) + 1);
            tagRepository.save(tag);
            postTagRepository.save(new PostTag(new PostTagId(postId, tag.getTagId())));
        }
    }

    private List<String> extractTags(Map<String, Object> body) {
        Object tagsObj = body.get("tags");
        if (!(tagsObj instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(String::valueOf)
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
    }

    private String getAuthorName(Integer authorId) {
        return userRepository.findById(authorId).map(User::getUserName).orElse("匿名");
    }

    private String getAuthorAvatar(Integer authorId) {
        return userRepository.findById(authorId).map(User::getAvatar).orElse(null);
    }

    private String getGraphName(Integer graphId) {
        if (graphId == null) {
            return null;
        }
        return knowledgeGraphRepository.findById(graphId).map(KnowledgeGraph::getName).orElse(null);
    }

    private String getCategoryName(String categoryCode, Integer categoryId) {
        if (categoryId != null) {
            return categoryRepository.findById(categoryId).map(Category::getName).orElse(categoryCode);
        }
        return categoryCode;
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
        return Integer.valueOf(text);
    }

    private String asString(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private PostStatus parsePostStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return PostStatus.已发布;
        }
        return switch (raw.trim().toUpperCase()) {
            case "DRAFT", "草稿", "鑽夌" -> PostStatus.草稿;
            case "PUBLISHED", "已发布", "宸插彂甯?" -> PostStatus.已发布;
            case "PRIVATE", "仅自己可见", "浠呰嚜宸卞彲瑙?" -> PostStatus.仅自己可见;
            case "OFFLINE", "已下架", "宸蹭笅鏋?" -> PostStatus.已下架;
            default -> PostStatus.已发布;
        };
    }

}
