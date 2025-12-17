package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.PostLike;
import com.sdu.kgplatform.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 帖子点赞 Repository
 */
@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {

    /**
     * 检查用户是否已点赞
     */
    boolean existsById(PostLikeId id);

    /**
     * 统计帖子的点赞数
     */
    @Query("SELECT COUNT(pl) FROM PostLike pl WHERE pl.id.postId = :postId")
    long countByPostId(@Param("postId") Integer postId);

    /**
     * 查询用户点赞的所有帖子ID
     */
    @Query("SELECT pl.id.postId FROM PostLike pl WHERE pl.id.userId = :userId")
    List<Integer> findPostIdsByUserId(@Param("userId") Integer userId);

    /**
     * 删除帖子的所有点赞
     */
    @Query("DELETE FROM PostLike pl WHERE pl.id.postId = :postId")
    void deleteByPostId(@Param("postId") Integer postId);
}
