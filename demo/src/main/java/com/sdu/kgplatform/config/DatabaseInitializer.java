package com.sdu.kgplatform.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * 数据库初始化器
 * 在应用启动时自动检查并创建必要的数据库索引和约束
 */
@Component
public class DatabaseInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private final Driver neo4jDriver;

    public DatabaseInitializer(Driver neo4jDriver) {
        this.neo4jDriver = neo4jDriver;
    }

    @Override
    public void run(String... args) {
        log.info("=== 开始检查 Neo4j 数据库索引与约束 ===");

        try (Session session = neo4jDriver.session()) {
            // 1. 创建约束 (Constraints)
            createConstraint(session, "entity_nodeid_unique", "Entity", "nodeId");

            // 2. 创建索引 (Indexes)
            createIndex(session, "entity_graphid_index", "Entity", "graphId");
            createIndex(session, "entity_name_index", "Entity", "name");
            createIndex(session, "entity_type_index", "Entity", "type");

            // 3. 创建关系索引 (Neo4j 4.3+)
            // 注意：关系索引语法略有不同，且旧版本可能不支持。这里使用 TRY-CATCH 包裹或检查版本
            // 为了兼容性，这里暂时只打印日志，建议在 Neo4j 5.x 环境下手动开启
            log.info("提示: 建议在生产环境中为 RELATES_TO 关系的 graphId 属性创建索引");

        } catch (Exception e) {
            log.error("数据库初始化期间发生错误: ", e);
            // 不阻断应用启动，但记录错误
        }

        log.info("=== Neo4j 数据库索引检查完成 ===");
    }

    private void createConstraint(Session session, String constraintName, String label, String property) {
        try {
            // Neo4j 4.x/5.x 通用语法: CREATE CONSTRAINT IF NOT EXISTS ...
            // 注意: 语法可能随版本略有变化，使用 IF NOT EXISTS 是最安全的
            String query = String.format(
                    "CREATE CONSTRAINT %s IF NOT EXISTS FOR (n:%s) REQUIRE n.%s IS UNIQUE",
                    constraintName, label, property);
            session.run(query);
            log.info("已验证约束: {} (Label: {}, Property: {})", constraintName, label, property);
        } catch (Exception e) {
            log.warn("创建约束 {} 失败: {}", constraintName, e.getMessage());
        }
    }

    private void createIndex(Session session, String indexName, String label, String property) {
        try {
            String query = String.format(
                    "CREATE INDEX %s IF NOT EXISTS FOR (n:%s) ON (n.%s)",
                    indexName, label, property);
            session.run(query);
            log.info("已验证索引: {} (Label: {}, Property: {})", indexName, label, property);
        } catch (Exception e) {
            log.warn("创建索引 {} 失败: {}", indexName, e.getMessage());
        }
    }
}
