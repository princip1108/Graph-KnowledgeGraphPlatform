package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.SearchHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SearchHistoryRepository extends JpaRepository<SearchHistory, Integer> {

    /**
     * 查找用户的特定类型搜索历史
     */
    Page<SearchHistory> findByUserIdAndSearchTypeOrderBySearchTimeDesc(Integer userId, String searchType,
            Pageable pageable);

    /**
     * 查找特定的历史记录（用于去重）
     */
    Optional<SearchHistory> findByUserIdAndSearchTypeAndQueryText(Integer userId, String searchType, String queryText);

    /**
     * 删除特定用户的特定类型的所有搜索历史
     */
    void deleteByUserIdAndSearchType(Integer userId, String searchType);

    /**
     * 删除特定用户的特定类型的特定关键词记录
     */
    void deleteByUserIdAndSearchTypeAndQueryText(Integer userId, String searchType, String queryText);

    /**
     * 统计用户某类型的搜索记录条数
     */
    long countByUserIdAndSearchType(Integer userId, String searchType);

    /**
     * 获取用户最早的一条搜索记录（用于删除多余记录）
     */
    Optional<SearchHistory> findTopByUserIdAndSearchTypeOrderBySearchTimeAsc(Integer userId, String searchType);

    void deleteByUserId(Integer userId);
}
