package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.Comment;
import com.sdu.kgplatform.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 评论 Repository
 */
@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {

    /**
     * 根据帖子ID查询评论（按时间排序）
     */
    List<Comment> findByPostIdOrderByCommentTimeAsc(Integer postId);

    /**
     * 根据帖子ID查询顶级评论（parentCommentId为null的）
     */
    List<Comment> findByPostIdAndParentCommentIdIsNullOrderByCommentTimeAsc(Integer postId);

    /**
     * 根据父评论ID查询回复
     */
    List<Comment> findByParentCommentIdOrderByCommentTimeAsc(Integer parentCommentId);

    /**
     * 根据帖子ID查询评论（分页）
     */
    Page<Comment> findByPostIdOrderByCommentTimeDesc(Integer postId, Pageable pageable);

    /**
     * 统计帖子的评论数
     */
    long countByPostId(Integer postId);

    /**
     * 统计所有评论总数
     */
    @Query("SELECT COUNT(c) FROM Comment c")
    long countAllComments();

    /**
     * 统计已发布帖子的评论总数
     */
    @Query("SELECT COUNT(c) FROM Comment c WHERE c.postId IN (SELECT p.postId FROM Post p WHERE p.postStatus = :status)")
    long countByPostStatus(@Param("status") PostStatus status);

    /**
     * 根据用户ID查询评论
     */
    List<Comment> findByUserIdOrderByCommentTimeDesc(Integer userId);

    /**
     * 删除帖子的所有评论
     */
    void deleteByPostId(Integer postId);
}
