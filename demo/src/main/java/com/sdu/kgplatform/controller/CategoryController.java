package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.entity.Category;
import com.sdu.kgplatform.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 获取图谱分类列表
     * GET /api/category/graph
     */
    @GetMapping("/graph")
    public ResponseEntity<?> getGraphCategories() {
        try {
            List<Category> categories = categoryService.getCategoriesByType("GRAPH");
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", categories));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
