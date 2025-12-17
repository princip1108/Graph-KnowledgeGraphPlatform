package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.PostTag;
import com.sdu.kgplatform.entity.PostTagId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 帖子标签关联 Repository
 */
@Repository
public interface PostTagRepository extends JpaRepository<PostTag, PostTagId> {

    /**
     * 根据帖子ID查询所有标签ID
     */
    @Query("SELECT pt.id.tagId FROM PostTag pt WHERE pt.id.postId = :postId")
    List<Integer> findTagIdsByPostId(@Param("postId") Integer postId);

    /**
     * 根据标签ID查询所有帖子ID
     */
    @Query("SELECT pt.id.postId FROM PostTag pt WHERE pt.id.tagId = :tagId")
    List<Integer> findPostIdsByTagId(@Param("tagId") Integer tagId);

    /**
     * 删除帖子的所有标签关联
     */
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM PostTag pt WHERE pt.id.postId = :postId")
    void deleteByPostId(@Param("postId") Integer postId);

    /**
     * 检查帖子是否有某标签
     */
    boolean existsById(PostTagId id);
}
