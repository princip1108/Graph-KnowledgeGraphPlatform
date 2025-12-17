package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.PostFavorite;
import com.sdu.kgplatform.entity.PostFavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 帖子收藏 Repository
 */
@Repository
public interface PostFavoriteRepository extends JpaRepository<PostFavorite, PostFavoriteId> {

    List<PostFavorite> findByIdUserId(Integer userId);

    List<PostFavorite> findByIdPostId(Integer postId);

    default List<PostFavorite> findByUserId(Integer userId) {
        return findByIdUserId(userId);
    }

    boolean existsById(PostFavoriteId id);

    long countByIdPostId(Integer postId);
}
