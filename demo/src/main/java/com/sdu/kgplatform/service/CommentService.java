package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.Comment;
import com.sdu.kgplatform.entity.Post;
import com.sdu.kgplatform.entity.PostStatus;
import com.sdu.kgplatform.entity.User;
import com.sdu.kgplatform.repository.CommentRepository;
import com.sdu.kgplatform.repository.PostRepository;
import com.sdu.kgplatform.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 评论服务类
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    public CommentService(CommentRepository commentRepository,
                          UserRepository userRepository,
                          PostRepository postRepository) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
    }

    /**
     * 获取帖子的评论列表（树形结构，支持多级嵌套）
     */
    public List<Map<String, Object>> getCommentsByPostId(Integer postId) {
        return getCommentsByPostId(postId, null, false);
    }

    public List<Map<String, Object>> getCommentsByPostId(Integer postId, Integer viewerUserId, boolean admin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (!canViewPost(post, viewerUserId, admin)) {
            throw new IllegalArgumentException("无权访问该帖子评论");
        }

        List<Comment> comments = commentRepository.findByPostIdOrderByCommentTimeAsc(postId);
        if (comments.isEmpty()) {
            return List.of();
        }

        Map<Integer, User> users = loadUsers(comments);
        Map<Integer, List<Comment>> repliesByParent = new LinkedHashMap<>();
        List<Comment> topComments = new ArrayList<>();
        for (Comment comment : comments) {
            Integer parentId = comment.getParentCommentId();
            if (parentId == null) {
                topComments.add(comment);
            } else {
                repliesByParent.computeIfAbsent(parentId, ignored -> new ArrayList<>()).add(comment);
            }
        }
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Comment comment : topComments) {
            Map<String, Object> commentMap = buildCommentMap(comment, users);
            
            List<Map<String, Object>> allReplies = new ArrayList<>();
            collectRepliesRecursively(comment.getCommentId(), repliesByParent, users, allReplies);
            commentMap.put("replies", allReplies);
            
            result.add(commentMap);
        }
        
        return result;
    }

    /**
     * 递归收集所有嵌套回复
     */
    private void collectRepliesRecursively(
            Integer parentId,
            Map<Integer, List<Comment>> repliesByParent,
            Map<Integer, User> users,
            List<Map<String, Object>> allReplies) {
        List<Comment> replies = repliesByParent.getOrDefault(parentId, List.of());
        for (Comment reply : replies) {
            allReplies.add(buildCommentMap(reply, users));
            collectRepliesRecursively(reply.getCommentId(), repliesByParent, users, allReplies);
        }
    }

    /**
     * 构建评论Map（包含用户信息）
     */
    private Map<String, Object> buildCommentMap(Comment comment, Map<Integer, User> users) {
        Map<String, Object> map = new HashMap<>();
        map.put("commentId", comment.getCommentId());
        map.put("commentText", comment.getCommentText());
        map.put("commentTime", comment.getCommentTime());
        map.put("userId", comment.getUserId());
        map.put("postId", comment.getPostId());
        map.put("parentCommentId", comment.getParentCommentId());

        User user = users.get(comment.getUserId());
        if (user != null) {
            map.put("username", user.getUserName());
            map.put("avatar", user.getAvatar());
        }

        return map;
    }

    private Map<Integer, User> loadUsers(List<Comment> comments) {
        List<Integer> userIds = comments.stream()
                .map(Comment::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }

        Map<Integer, User> users = new HashMap<>();
        userRepository.findAllById(userIds)
                .forEach(user -> users.put(user.getUserId(), user));
        return users;
    }

    private boolean canViewPost(Post post, Integer viewerUserId, boolean admin) {
        if (post.getPostStatus() == PostStatus.已发布) {
            return true;
        }
        return admin || (viewerUserId != null && Objects.equals(post.getAuthorId(), viewerUserId));
    }

    /**
     * 发表评论
     */
    @Transactional
    public Comment createComment(Integer postId, Integer userId, String text, Integer parentCommentId) {
        String commentText = text == null ? null : text.trim();
        if (commentText == null || commentText.isEmpty()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        if (commentText.length() > 2000) {
            throw new IllegalArgumentException("评论内容不能超过2000个字符");
        }

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("帖子不存在"));
        if (post.getPostStatus() != PostStatus.已发布) {
            throw new IllegalArgumentException("该帖子当前不可评论");
        }

        if (parentCommentId != null) {
            Comment parent = commentRepository.findById(parentCommentId)
                    .orElseThrow(() -> new IllegalArgumentException("父评论不存在"));
            if (!java.util.Objects.equals(parent.getPostId(), postId)) {
                throw new IllegalArgumentException("父评论不属于当前帖子");
            }
        }

        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setCommentText(commentText);
        comment.setCommentTime(LocalDateTime.now());
        comment.setParentCommentId(parentCommentId);

        return commentRepository.save(comment);
    }

    /**
     * 删除评论
     */
    @Transactional
    public void deleteComment(Integer commentId, Integer userId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("评论不存在"));

        if (!comment.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权删除此评论");
        }

        // 递归删除所有子评论
        deleteCommentAndReplies(commentId);
    }

    /**
     * 递归删除评论及其所有子评论
     */
    private void deleteCommentAndReplies(Integer commentId) {
        // 先递归删除所有子评论
        List<Comment> replies = commentRepository.findByParentCommentIdOrderByCommentTimeAsc(commentId);
        for (Comment reply : replies) {
            deleteCommentAndReplies(reply.getCommentId());
        }
        // 最后删除当前评论
        commentRepository.deleteById(commentId);
    }

    /**
     * 获取评论详情
     */
    public Optional<Comment> getCommentById(Integer commentId) {
        return commentRepository.findById(commentId);
    }

    /**
     * 统计帖子评论数
     */
    public long countByPostId(Integer postId) {
        return commentRepository.countByPostId(postId);
    }

    /**
     * 获取用户的评论
     */
    public List<Comment> getUserComments(Integer userId) {
        return commentRepository.findByUserIdOrderByCommentTimeDesc(userId);
    }
}
