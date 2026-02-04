package com.sdu.kgplatform.repository;

import com.sdu.kgplatform.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {
    List<Category> findByTypeOrderByPriorityDesc(String type);
}
