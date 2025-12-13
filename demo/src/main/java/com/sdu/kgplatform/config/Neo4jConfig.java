package com.sdu.kgplatform.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.neo4j.core.DatabaseSelectionProvider;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.data.neo4j.core.mapping.Neo4jMappingContext;
import org.springframework.data.neo4j.core.transaction.Neo4jTransactionManager;
import org.springframework.data.neo4j.repository.config.EnableNeo4jRepositories;

/**
 * Neo4j 配置类
 * 显式配置 Neo4j 驱动和事务管理器
 */
@Configuration
@EnableNeo4jRepositories(
    basePackages = "com.sdu.kgplatform.repository",
    transactionManagerRef = "neo4jTransactionManager",
    // 只包含 Neo4j 仓库
    includeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = {
            com.sdu.kgplatform.repository.NodeRepository.class,
            com.sdu.kgplatform.repository.RelationshipRepository.class
        }
    )
)
public class Neo4jConfig {

    @Value("${spring.neo4j.uri}")
    private String neo4jUri;

    @Value("${spring.neo4j.authentication.username}")
    private String neo4jUsername;

    @Value("${spring.neo4j.authentication.password}")
    private String neo4jPassword;

    /**
     * 创建 Neo4j Driver Bean
     */
    @Bean
    public Driver neo4jDriver() {
        System.out.println("=== 初始化 Neo4j Driver ===");
        System.out.println("URI: " + neo4jUri);
        System.out.println("Username: " + neo4jUsername);
        
        try {
            Driver driver = GraphDatabase.driver(
                neo4jUri,
                AuthTokens.basic(neo4jUsername, neo4jPassword)
            );
            
            // 验证连接
            driver.verifyConnectivity();
            System.out.println("Neo4j 连接成功！");
            
            return driver;
        } catch (Exception e) {
            System.err.println("Neo4j 连接失败: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 数据库选择提供者
     */
    @Bean
    public DatabaseSelectionProvider databaseSelectionProvider() {
        return DatabaseSelectionProvider.getDefaultSelectionProvider();
    }

    /**
     * Neo4j Client
     */
    @Bean
    public Neo4jClient neo4jClient(Driver driver, DatabaseSelectionProvider databaseSelectionProvider) {
        return Neo4jClient.create(driver, databaseSelectionProvider);
    }

    /**
     * Neo4j Template
     */
    @Bean
    public Neo4jTemplate neo4jTemplate(Neo4jClient neo4jClient, Neo4jMappingContext mappingContext) {
        return new Neo4jTemplate(neo4jClient, mappingContext);
    }

    /**
     * 配置 Neo4j 事务管理器
     */
    @Bean("neo4jTransactionManager")
    public Neo4jTransactionManager neo4jTransactionManager(Driver driver, DatabaseSelectionProvider databaseSelectionProvider) {
        return new Neo4jTransactionManager(driver, databaseSelectionProvider);
    }
}

