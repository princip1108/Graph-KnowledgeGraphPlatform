package com.sdu.kgplatform.controller;

import com.sdu.kgplatform.entity.DomainCategory;
import com.sdu.kgplatform.repository.DomainCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class DomainCategoryController {

    @Autowired
    private DomainCategoryRepository repository;

    @GetMapping
    public ResponseEntity<List<DomainCategory>> getAllCategories() {
        return ResponseEntity.ok(repository.findAllByOrderBySortOrderAsc());
    }
}
