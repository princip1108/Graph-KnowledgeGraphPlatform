package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.PostFavorite;
import com.sdu.kgplatform.entity.PostFavoriteId;
import com.sdu.kgplatform.entity.Post;
import com.sdu.kgplatform.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 帖子收藏 Repository
 */
@Repository
public interface PostFavoriteRepository extends JpaRepository<PostFavorite, PostFavoriteId> {

    List<PostFavorite> findByIdUserId(Integer userId);

    @Query(value = """
            SELECT p FROM Post p, PostFavorite f
            WHERE f.id.userId = :userId
              AND f.id.postId = p.postId
              AND (p.postStatus = :publishedStatus OR p.authorId = :userId)
            ORDER BY f.favoriteTime DESC, p.uploadTime DESC, p.postId DESC
            """,
            countQuery = """
            SELECT COUNT(p) FROM Post p, PostFavorite f
            WHERE f.id.userId = :userId
              AND f.id.postId = p.postId
              AND (p.postStatus = :publishedStatus OR p.authorId = :userId)
            """)
    Page<Post> findVisibleFavoritePosts(@Param("userId") Integer userId,
                                        @Param("publishedStatus") PostStatus publishedStatus,
                                        Pageable pageable);

    List<PostFavorite> findByIdPostId(Integer postId);

    default List<PostFavorite> findByUserId(Integer userId) {
        return findByIdUserId(userId);
    }

    boolean existsById(PostFavoriteId id);

    long countByIdPostId(Integer postId);
}
