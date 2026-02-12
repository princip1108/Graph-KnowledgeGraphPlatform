package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.BrowsingHistory;
import com.sdu.kgplatform.entity.ResourceType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BrowsingHistoryRepository extends JpaRepository<BrowsingHistory, Integer> {

        /**
         * 获取用户最近的图谱浏览记录（用于推荐算法计算领域偏好）
         */
        List<BrowsingHistory> findTop50ByUserIdAndResourceTypeOrderByViewTimeDesc(Integer userId,
                        ResourceType resourceType);

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

        /**
         * 删除特定帖子的所有浏览历史（用于删除帖子前清理）
         */
        void deleteByPostId(Integer postId);

        /**
         * 删除特定图谱的所有浏览历史（用于删除图谱前清理）
         */
        void deleteByGraphId(Integer graphId);

        void deleteByUserId(Integer userId);
}
