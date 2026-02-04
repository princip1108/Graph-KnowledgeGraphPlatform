package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.BrowsingHistory;
import com.sdu.kgplatform.entity.ResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BrowsingHistoryRepository extends JpaRepository<BrowsingHistory, Integer> {

    /**
     * 获取用户特定类型的浏览历史
     */
    Page<BrowsingHistory> findByUserIdAndResourceTypeOrderByViewTimeDesc(Integer userId, ResourceType resourceType,
            Pageable pageable);

    /**
     * 查找图谱浏览记录（用于更新）
     */
    Optional<BrowsingHistory> findByUserIdAndResourceTypeAndGraphId(Integer userId, ResourceType resourceType,
            Integer graphId);

    /**
     * 查找帖子浏览记录（用于更新）
     */
    Optional<BrowsingHistory> findByUserIdAndResourceTypeAndPostId(Integer userId, ResourceType resourceType,
            Integer postId);

    /**
     * 删除特定用户的特定类型的浏览历史
     */
    void deleteByUserIdAndResourceType(Integer userId, ResourceType resourceType);
}
