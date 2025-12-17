package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.GraphFavorite;
import com.sdu.kgplatform.entity.GraphFavoriteId;
import org.springframework.data.jpa.repository.JpaRepository;
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

    /**
     * 根据图谱ID查询收藏该图谱的用户
     */
    List<GraphFavorite> findByIdGraphId(Integer graphId);

    /**
     * 统计图谱的收藏数
     */
    long countByIdGraphId(Integer graphId);

    /**
     * 检查用户是否收藏了某图谱
     */
    boolean existsByIdUserIdAndIdGraphId(Integer userId, Integer graphId);
}
