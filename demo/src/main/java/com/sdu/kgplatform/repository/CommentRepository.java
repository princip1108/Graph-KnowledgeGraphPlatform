package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Integer> {

    List<Comment> findByPostIdAndParentCommentIdIsNullOrderByCommentTimeAsc(Integer postId);

    List<Comment> findByPostIdOrderByCommentTimeAsc(Integer postId);

    List<Comment> findByParentCommentIdOrderByCommentTimeAsc(Integer parentCommentId);

    List<Comment> findByUserIdOrderByCommentTimeDesc(Integer userId);

    long countByPostId(Integer postId);

    @Query("SELECT c.postId, COUNT(c) FROM Comment c WHERE c.postId IN :postIds GROUP BY c.postId")
    List<Object[]> countByPostIds(@Param("postIds") List<Integer> postIds);

    void deleteByPostId(Integer postId);
}
