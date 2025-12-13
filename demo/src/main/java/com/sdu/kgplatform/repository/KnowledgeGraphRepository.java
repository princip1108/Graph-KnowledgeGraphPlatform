package com.sdu.kgplatform.repository;

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
 * 知识图谱 Repository
 */
@Repository
public interface KnowledgeGraphRepository extends JpaRepository<KnowledgeGraph, Integer> {

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

    /**
     * 根据名称模糊查询
     */
    Page<KnowledgeGraph> findByNameContainingIgnoreCase(String name, Pageable pageable);

    /**
     * 根据上传者ID和状态查找
     */
    Page<KnowledgeGraph> findByUploaderIdAndStatus(Integer uploaderId, GraphStatus status, Pageable pageable);

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
}
