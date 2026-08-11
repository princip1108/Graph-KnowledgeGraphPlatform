package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.GraphFavorite;
import com.sdu.kgplatform.entity.GraphFavoriteId;
import com.sdu.kgplatform.entity.GraphStatus;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 图谱收藏仓库
 */
@Repository
public interface GraphFavoriteRepository extends JpaRepository<GraphFavorite, GraphFavoriteId> {

    /**
     * 根据用户ID查询收藏的图谱
     */
    List<GraphFavorite> findByIdUserId(Integer userId);

    @Query(value = """
            SELECT g FROM KnowledgeGraph g, GraphFavorite f
            WHERE f.id.userId = :userId
              AND f.id.graphId = g.graphId
              AND (g.status = :publishedStatus OR g.uploaderId = :userId)
            ORDER BY f.createdAt DESC, g.lastModified DESC, g.graphId DESC
            """,
            countQuery = """
            SELECT COUNT(g) FROM KnowledgeGraph g, GraphFavorite f
            WHERE f.id.userId = :userId
              AND f.id.graphId = g.graphId
              AND (g.status = :publishedStatus OR g.uploaderId = :userId)
            """)
    Page<KnowledgeGraph> findVisibleFavoriteGraphs(@Param("userId") Integer userId,
                                                   @Param("publishedStatus") GraphStatus publishedStatus,
                                                   Pageable pageable);

    /**
     * 根据图谱ID查询收藏该图谱的用户
     */
    List<GraphFavorite> findByIdGraphId(Integer graphId);

    /**
     * 统计图谱的收藏数
     */
    long countByIdGraphId(Integer graphId);

    void deleteByIdGraphId(Integer graphId);

    /**
     * 检查用户是否收藏了某图谱
     */
    boolean existsByIdUserIdAndIdGraphId(Integer userId, Integer graphId);
}
