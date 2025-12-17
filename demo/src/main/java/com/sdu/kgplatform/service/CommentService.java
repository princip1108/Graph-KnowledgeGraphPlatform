package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.Comment;
import com.sdu.kgplatform.repository.CommentRepository;
import com.sdu.kgplatform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 评论服务类
 */
@Service
public class CommentService {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 获取帖子的评论列表（树形结构，支持多级嵌套）
     */
    public List<Map<String, Object>> getCommentsByPostId(Integer postId) {
        // 获取顶级评论
        List<Comment> topComments = commentRepository.findByPostIdAndParentCommentIdIsNullOrderByCommentTimeAsc(postId);
        
        List<Map<String, Object>> result = new ArrayList<>();
        for (Comment comment : topComments) {
            Map<String, Object> commentMap = buildCommentMap(comment);
            
            // 递归获取所有回复（展平显示）
            List<Map<String, Object>> allReplies = new ArrayList<>();
            collectRepliesRecursively(comment.getCommentId(), allReplies);
            commentMap.put("replies", allReplies);
            
            result.add(commentMap);
        }
        
        return result;
    }

    /**
     * 递归收集所有嵌套回复
     */
    private void collectRepliesRecursively(Integer parentId, List<Map<String, Object>> allReplies) {
        List<Comment> replies = commentRepository.findByParentCommentIdOrderByCommentTimeAsc(parentId);
        for (Comment reply : replies) {
            allReplies.add(buildCommentMap(reply));
            // 递归获取这条回复的子回复
            collectRepliesRecursively(reply.getCommentId(), allReplies);
        }
    }

    /**
     * 构建评论Map（包含用户信息）
     */
    private Map<String, Object> buildCommentMap(Comment comment) {
        Map<String, Object> map = new HashMap<>();
        map.put("commentId", comment.getCommentId());
        map.put("commentText", comment.getCommentText());
        map.put("commentTime", comment.getCommentTime());
        map.put("userId", comment.getUserId());
        map.put("postId", comment.getPostId());
        map.put("parentCommentId", comment.getParentCommentId());

        // 获取用户信息
        userRepository.findById(comment.getUserId()).ifPresent(user -> {
            map.put("username", user.getUserName());
            map.put("avatar", user.getAvatar());
        });

        return map;
    }

    /**
     * 发表评论
     */
    @Transactional
    public Comment createComment(Integer postId, Integer userId, String text, Integer parentCommentId) {
        Comment comment = new Comment();
        comment.setPostId(postId);
        comment.setUserId(userId);
        comment.setCommentText(text);
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
