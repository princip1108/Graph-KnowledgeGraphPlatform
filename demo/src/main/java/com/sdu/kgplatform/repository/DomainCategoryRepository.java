package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.DomainCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DomainCategoryRepository extends JpaRepository<DomainCategory, Integer> {

    // 按排序字段升序查询所有
    List<DomainCategory> findAllByOrderBySortOrderAsc();

    // 根据代码查询
    DomainCategory findByCode(String code);
}
