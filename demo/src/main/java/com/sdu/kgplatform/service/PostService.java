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

        if (!post.getAuthorId().equals(userId)) {
            throw new IllegalArgumentException("无权删除此帖子");
        }

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

        // 总评论数
        long totalComments = commentRepository.countAllComments();
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
}
