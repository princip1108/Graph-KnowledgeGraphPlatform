package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.Post;
import com.sdu.kgplatform.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 帖子 Repository
 */
@Repository
public interface PostRepository extends JpaRepository<Post, Integer> {

    /**
     * 根据作者ID查询帖子
     */
    List<Post> findByAuthorId(Integer authorId);

    /**
     * 根据状态查询帖子（分页）
     */
    Page<Post> findByPostStatus(PostStatus status, Pageable pageable);

    /**
     * 查询已发布的帖子（分页）
     */
    Page<Post> findByPostStatusOrderByUploadTimeDesc(PostStatus status, Pageable pageable);

    /**
     * 根据标题模糊搜索（已发布的帖子）
     */
    @Query("SELECT p FROM Post p WHERE p.postStatus = :status AND " +
           "(LOWER(p.postTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.postAbstract) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Post> searchByKeyword(@Param("status") PostStatus status, 
                                @Param("keyword") String keyword, 
                                Pageable pageable);

    /**
     * 按点赞数排序查询（已发布的帖子）
     */
    Page<Post> findByPostStatusOrderByLikeCountDesc(PostStatus status, Pageable pageable);

    /**
     * 按时间范围查询（已发布的帖子）
     */
    @Query("SELECT p FROM Post p WHERE p.postStatus = :status AND " +
           "p.uploadTime >= :startTime AND p.uploadTime <= :endTime " +
           "ORDER BY p.uploadTime DESC")
    Page<Post> findByDateRange(@Param("status") PostStatus status,
                                @Param("startTime") LocalDateTime startTime,
                                @Param("endTime") LocalDateTime endTime,
                                Pageable pageable);

    /**
     * 统计已发布帖子总数
     */
    long countByPostStatus(PostStatus status);

    /**
     * 统计今日新帖数
     */
    @Query("SELECT COUNT(p) FROM Post p WHERE p.postStatus = :status AND p.uploadTime >= :startOfDay")
    long countTodayPosts(@Param("status") PostStatus status, @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * 查询用户的帖子（分页）
     */
    Page<Post> findByAuthorIdOrderByUploadTimeDesc(Integer authorId, Pageable pageable);

    /**
     * 查询置顶帖子
     */
    List<Post> findByPostStatusAndIsPinnedOrderByUploadTimeDesc(PostStatus status, Boolean isPinned);

    /**
     * 查询用户草稿帖子
     */
    List<Post> findByAuthorIdAndPostStatus(Integer authorId, PostStatus status);
}
