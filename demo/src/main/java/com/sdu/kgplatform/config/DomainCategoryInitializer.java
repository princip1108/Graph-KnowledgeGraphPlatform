package com.sdu.kgplatform.config;

import com.sdu.kgplatform.entity.DomainCategory;
import com.sdu.kgplatform.repository.DomainCategoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 领域分类数据初始化器
 * 应用启动时检查 categories 表，为空则插入默认分类
 */
@Component
public class DomainCategoryInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DomainCategoryInitializer.class);

    private final DomainCategoryRepository repository;

    public DomainCategoryInitializer(DomainCategoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            log.info("categories 表已有数据，跳过初始化");
            return;
        }

        log.info("=== 初始化领域分类数据 ===");

        List<DomainCategory> categories = Arrays.asList(
                new DomainCategory(null, "自然科学", "science", null, 1),
                new DomainCategory(null, "工程技术", "engineering", null, 2),
                new DomainCategory(null, "医药健康", "medicine", null, 3),
                new DomainCategory(null, "人文社科", "humanities", null, 4),
                new DomainCategory(null, "经济管理", "economics", null, 5),
                new DomainCategory(null, "信息技术", "information", null, 6),
                new DomainCategory(null, "教育文化", "education", null, 7),
                new DomainCategory(null, "其他", "other", null, 99)
        );

        repository.saveAll(categories);
        log.info("已插入 {} 条领域分类", categories.size());
    }
}
