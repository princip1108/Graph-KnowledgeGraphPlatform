package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.BrowsingHistory;
import com.sdu.kgplatform.entity.ResourceType;
import com.sdu.kgplatform.entity.SearchHistory;
import com.sdu.kgplatform.repository.BrowsingHistoryRepository;
import com.sdu.kgplatform.repository.SearchHistoryRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class HistoryService {

    private final SearchHistoryRepository searchHistoryRepository;
    private final BrowsingHistoryRepository browsingHistoryRepository;

    public HistoryService(SearchHistoryRepository searchHistoryRepository,
            BrowsingHistoryRepository browsingHistoryRepository) {
        this.searchHistoryRepository = searchHistoryRepository;
        this.browsingHistoryRepository = browsingHistoryRepository;
    }

    // ==================== 搜索历史 ====================

    /**
     * 记录搜索历史
     * 策略：如果已存在相同关键词（同类型），则更新时间；否则插入。
     * 限制：每种类型最多保留最新的 20 条。
     */
    @Transactional
    public void recordSearch(Integer userId, String keyword, String searchType) {
        if (userId == null || keyword == null || keyword.trim().isEmpty()) {
            return;
        }

        String actualKeyword = keyword.trim();
        Optional<SearchHistory> existing = searchHistoryRepository.findByUserIdAndSearchTypeAndQueryText(userId,
                searchType, actualKeyword);

        if (existing.isPresent()) {
            // 更新时间
            SearchHistory history = existing.get();
            history.setSearchTime(LocalDateTime.now());
            searchHistoryRepository.save(history);
        } else {
            // 插入新记录
            SearchHistory history = new SearchHistory();
            history.setUserId(userId);
            history.setQueryText(actualKeyword);
            history.setSearchType(searchType);
            history.setSearchTime(LocalDateTime.now());
            searchHistoryRepository.save(history);

            // 检查数量限制，删除多余的
            long count = searchHistoryRepository.countByUserIdAndSearchType(userId, searchType);
            if (count > 20) {
                // 删除最早的一条
                searchHistoryRepository.findTopByUserIdAndSearchTypeOrderBySearchTimeAsc(userId, searchType)
                        .ifPresent(searchHistoryRepository::delete);
            }
        }
    }

    /**
     * 获取用户的搜索历史
     */
    public Page<SearchHistory> getUserSearchHistory(Integer userId, String searchType, Pageable pageable) {
        return searchHistoryRepository.findByUserIdAndSearchTypeOrderBySearchTimeDesc(userId, searchType, pageable);
    }

    /**
     * 清空特定类型的搜索历史
     */
    @Transactional
    public void clearSearchHistory(Integer userId, String searchType) {
        searchHistoryRepository.deleteByUserIdAndSearchType(userId, searchType);
    }

    /**
     * 删除单条搜索历史
     */
    @Transactional
    public void deleteSearchHistory(Integer userId, String searchType, String keyword) {
        searchHistoryRepository.deleteByUserIdAndSearchTypeAndQueryText(userId, searchType, keyword);
    }

    // ==================== 浏览历史 ====================

    /**
     * 记录浏览历史
     */
    @Transactional
    public void recordBrowsing(Integer userId, ResourceType resourceType, Integer resourceId) {
        if (userId == null || resourceId == null) {
            return;
        }

        Optional<BrowsingHistory> existing;
        if (resourceType == ResourceType.graph) {
            existing = browsingHistoryRepository.findByUserIdAndResourceTypeAndGraphId(userId, resourceType,
                    resourceId);
        } else {
            existing = browsingHistoryRepository.findByUserIdAndResourceTypeAndPostId(userId, resourceType, resourceId);
        }

        if (existing.isPresent()) {
            // 更新时间
            BrowsingHistory history = existing.get();
            history.setViewTime(LocalDateTime.now());
            browsingHistoryRepository.save(history);
        } else {
            // 插入新记录
            BrowsingHistory history = new BrowsingHistory();
            history.setUserId(userId);
            history.setResourceType(resourceType);
            history.setViewTime(LocalDateTime.now());

            if (resourceType == ResourceType.graph) {
                history.setGraphId(resourceId);
            } else {
                history.setPostId(resourceId);
            }

            browsingHistoryRepository.save(history);
        }
    }

    /**
     * 获取用户的浏览历史
     */
    /**
     * 获取用户的浏览历史
     */
    public Page<com.sdu.kgplatform.domain.dto.BrowsingHistoryDto> getUserBrowsingHistoryDto(Integer userId,
            ResourceType resourceType, Pageable pageable) {
        Page<BrowsingHistory> historyPage = browsingHistoryRepository
                .findByUserIdAndResourceTypeOrderByViewTimeDesc(userId, resourceType, pageable);

        return historyPage.map(history -> {
            com.sdu.kgplatform.domain.dto.BrowsingHistoryDto dto = new com.sdu.kgplatform.domain.dto.BrowsingHistoryDto();
            dto.setId(history.getId());
            dto.setUserId(history.getUserId());
            dto.setResourceType(history.getResourceType());
            dto.setViewTime(history.getViewTime());

            if (resourceType == ResourceType.graph) {
                dto.setResourceId(history.getGraphId());
                // 这里需要注入 KnowledgeGraphRepository 来查询名称
                // 为避免循环依赖问题或简化，暂不在此注入，而是在 Controller 层面处理或通过新查询方式
                // 但最佳实践是在 Service 处理。
                // 暂时返回 ID，名称在 Controller 组装，或者我们在 Service 引入 Repository
            } else {
                dto.setResourceId(history.getPostId());
            }
            return dto;
        });
    }

    // 为了在 Controller 中使用，我们需要注入 Repository
    // 但为保持 HistoryService 干净，可以考虑只返回 List<BrowsingHistory>，在 Controller 拼装
    // 还是原来的方法保留，通过增强 Controller 逻辑解决
    public Page<BrowsingHistory> getUserBrowsingHistory(Integer userId, ResourceType resourceType, Pageable pageable) {
        return browsingHistoryRepository.findByUserIdAndResourceTypeOrderByViewTimeDesc(userId, resourceType, pageable);
    }

    /**
     * 清空特定类型的浏览历史
     */
    @Transactional
    public void clearBrowsingHistory(Integer userId, ResourceType resourceType) {
        browsingHistoryRepository.deleteByUserIdAndResourceType(userId, resourceType);
    }

    /**
     * 删除单条浏览历史
     */
    @Transactional
    public void deleteBrowsingHistory(Integer userId, Integer id) {
        browsingHistoryRepository.findById(id).ifPresent(history -> {
            if (history.getUserId().equals(userId)) {
                browsingHistoryRepository.delete(history);
            }
        });
    }
}
