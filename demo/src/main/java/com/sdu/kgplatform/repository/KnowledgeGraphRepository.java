package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.GraphStatus;
import com.sdu.kgplatform.entity.KnowledgeGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识图谱 Repository
 */
@Repository
public interface KnowledgeGraphRepository
                extends JpaRepository<KnowledgeGraph, Integer>, JpaSpecificationExecutor<KnowledgeGraph> {

        /**
         * 根据上传者ID查找图谱
         */
        List<KnowledgeGraph> findByUploaderId(Integer uploaderId);

        /**
         * 根据上传者ID分页查找图谱
         */
        Page<KnowledgeGraph> findByUploaderId(Integer uploaderId, Pageable pageable);

        /**
         * 根据状态查找图谱
         */
        List<KnowledgeGraph> findByStatus(GraphStatus status);

        /**
         * 根据状态分页查找图谱
         */
        Page<KnowledgeGraph> findByStatus(GraphStatus status, Pageable pageable);

        Page<KnowledgeGraph> findByStatusAndCategoryId(GraphStatus status, Integer categoryId, Pageable pageable);

        @Query("SELECT g.graphId FROM KnowledgeGraph g")
        List<Integer> findAllGraphIds();

        /**
         * 根据名称模糊查询
         */
        Page<KnowledgeGraph> findByNameContainingIgnoreCase(String name, Pageable pageable);

        /**
         * 根据上传者ID和状态查找
         */
        Page<KnowledgeGraph> findByUploaderIdAndStatus(Integer uploaderId, GraphStatus status, Pageable pageable);

        Page<KnowledgeGraph> findByUploaderIdAndStatusAndCategoryId(Integer uploaderId, GraphStatus status,
                        Integer categoryId, Pageable pageable);

        /**
         * 统计用户的图谱数量
         */
        long countByUploaderId(Integer uploaderId);

        /**
         * 统计特定状态的图谱数量
         */
        long countByStatus(GraphStatus status);

        /**
         * 查找公开的图谱（已发布状态）
         */
        @Query("SELECT g FROM KnowledgeGraph g WHERE g.status = 'PUBLISHED' ORDER BY g.viewCount DESC")
        Page<KnowledgeGraph> findPublicGraphsOrderByViewCount(Pageable pageable);

        /**
         * 查找热门图谱（按浏览量排序）
         */
        @Query("SELECT g FROM KnowledgeGraph g WHERE g.status = 'PUBLISHED' ORDER BY g.viewCount DESC, g.collectCount DESC")
        List<KnowledgeGraph> findTopPopularGraphs(Pageable pageable);

        /**
         * 搜索图谱（名称或描述包含关键词）
         */
        @Query("SELECT g FROM KnowledgeGraph g WHERE g.status = 'PUBLISHED' AND " +
                        "(LOWER(g.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(g.description) LIKE LOWER(CONCAT('%', :keyword, '%')))")
        Page<KnowledgeGraph> searchPublicGraphs(@Param("keyword") String keyword, Pageable pageable);

        /**
         * 根据分享链接查找图谱
         */
        KnowledgeGraph findByShareLink(String shareLink);

        /**
         * 检查图谱名称是否已存在（同一用户下）
         */
        boolean existsByUploaderIdAndName(Integer uploaderId, String name);

        /**
         * 个性化推荐：按偏好领域加权 + hotScore 混合排序
         * 偏好领域的图谱优先，同时按热度排序
         */
        @Query("SELECT g FROM KnowledgeGraph g WHERE g.status = 'PUBLISHED' AND g.graphId NOT IN :excludeIds " +
                        "ORDER BY CASE WHEN g.domain IN :preferredDomains THEN 0 ELSE 1 END ASC, g.hotScore DESC")
        Page<KnowledgeGraph> findRecommendedGraphs(@Param("preferredDomains") List<String> preferredDomains,
                        @Param("excludeIds") List<Integer> excludeIds,
                        Pageable pageable);

        /**
         * 热门推荐降级（无偏好时按 hotScore 排序）
         */
        @Query("SELECT g FROM KnowledgeGraph g WHERE g.status = 'PUBLISHED' ORDER BY g.hotScore DESC")
        Page<KnowledgeGraph> findByHotScore(Pageable pageable);

        /**
         * 按领域筛选的热门推荐降级
         */
        @Query("SELECT g FROM KnowledgeGraph g WHERE g.status = 'PUBLISHED' AND g.domain = :domain ORDER BY g.hotScore DESC")
        Page<KnowledgeGraph> findByHotScoreAndDomain(@Param("domain") String domain, Pageable pageable);

        Page<KnowledgeGraph> findByStatusOrderByGraphIdAsc(GraphStatus status, Pageable pageable);

        @Transactional
        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                        UPDATE knowledge_graph
                        SET hot_score = (
                                COALESCE(view_count, 0)
                                + COALESCE(collect_count, 0) * 5
                                + COALESCE(download_count, 0) * 3
                                + 1
                        ) / POW(
                                GREATEST(
                                        TIMESTAMPDIFF(DAY, COALESCE(upload_date, CURRENT_DATE), CURRENT_TIMESTAMP),
                                        0
                                ) + 2,
                                1.5
                        )
                        WHERE status = 'PUBLISHED'
                        """, nativeQuery = true)
        int refreshPublishedHotScores();

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE KnowledgeGraph g SET g.viewCount = COALESCE(g.viewCount, 0) + 1 WHERE g.graphId = :graphId")
        int incrementViewCount(@Param("graphId") Integer graphId);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE KnowledgeGraph g SET g.downloadCount = COALESCE(g.downloadCount, 0) + 1 WHERE g.graphId = :graphId")
        int incrementDownloadCount(@Param("graphId") Integer graphId);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query("UPDATE KnowledgeGraph g SET g.collectCount = :collectCount, g.lastModified = :lastModified WHERE g.graphId = :graphId")
        int updateCollectCount(@Param("graphId") Integer graphId,
                        @Param("collectCount") int collectCount,
                        @Param("lastModified") LocalDateTime lastModified);
}
