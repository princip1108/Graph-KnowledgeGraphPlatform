package com.sdu.kgplatform.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * JPA 配置类
 * 配置 JPA 事务管理器（用于 MySQL）
 */
@Configuration
@EnableJpaRepositories(
    basePackages = "com.sdu.kgplatform.repository",
    transactionManagerRef = "transactionManager",
    // 只管理 JPA 仓库（排除 Neo4j 仓库）
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = {
            com.sdu.kgplatform.repository.NodeRepository.class,
            com.sdu.kgplatform.repository.RelationshipRepository.class
        }
    )
)
public class JpaConfig {

    /**
     * JPA 事务管理器（主事务管理器，用于 MySQL）
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new JpaTransactionManager(entityManagerFactory);
    }
}


