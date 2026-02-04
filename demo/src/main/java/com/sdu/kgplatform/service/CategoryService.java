package com.sdu.kgplatform.service;

import com.sdu.kgplatform.entity.Category;
import com.sdu.kgplatform.repository.CategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    @Autowired
    private CategoryRepository categoryRepository;

    public List<Category> getCategoriesByType(String type) {
        return categoryRepository.findByTypeOrderByPriorityDesc(type);
    }

    public Category createCategory(String name, String type, Integer priority) {
        Category category = new Category();
        category.setName(name);
        category.setType(type);
        category.setPriority(priority);
        return categoryRepository.save(category);
    }

    public Category updateCategory(Integer id, String name, Integer priority) {
        Category category = categoryRepository.findById(id).orElse(null);
        if (category != null) {
            category.setName(name);
            if (priority != null) {
                category.setPriority(priority);
            }
            return categoryRepository.save(category);
        }
        return null;
    }

    public void deleteCategory(Integer id) {
        categoryRepository.deleteById(id);
    }
}
