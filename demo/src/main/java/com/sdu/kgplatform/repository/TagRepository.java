package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 标签 Repository
 */
@Repository
public interface TagRepository extends JpaRepository<Tag, Integer> {

    /**
     * 根据标签名查询
     */
    Optional<Tag> findByTagName(String tagName);

    /**
     * 根据标签名模糊搜索
     */
    List<Tag> findByTagNameContainingIgnoreCase(String keyword);

    /**
     * 查询热门标签（按使用次数排序）
     */
    List<Tag> findTop20ByOrderByUsageCountDesc();

    /**
     * 检查标签是否存在
     */
    boolean existsByTagName(String tagName);

    /**
     * 根据多个标签名查询
     */
    List<Tag> findByTagNameIn(List<String> tagNames);
}
