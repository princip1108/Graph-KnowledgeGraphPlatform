package com.sdu.kgplatform.config;

import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 数据库初始化器
 * 在应用启动时自动检查并创建必要的数据库索引和约束
 */
@Component
@Profile("!local-no-neo4j")
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
            createCompositeIndex(session, "entity_graphid_nodeid_index", "Entity", "graphId", "nodeId");
            createCompositeIndex(session, "entity_graphid_name_index", "Entity", "graphId", "name");
            createCompositeIndex(session, "entity_graphid_type_index", "Entity", "graphId", "type");

            // 3. 创建关系索引 (Neo4j 4.3+)
            createRelationshipIndex(session, "relates_to_graphid_index", "RELATES_TO", "graphId");

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

    private void createCompositeIndex(Session session, String indexName, String label, String... properties) {
        try {
            String props = String.join(", ", java.util.Arrays.stream(properties)
                    .map(property -> "n." + property)
                    .toArray(String[]::new));
            String query = String.format(
                    "CREATE INDEX %s IF NOT EXISTS FOR (n:%s) ON (%s)",
                    indexName, label, props);
            session.run(query);
            log.info("已验证复合索引: {} (Label: {}, Properties: {})", indexName, label, String.join(",", properties));
        } catch (Exception e) {
            log.warn("创建复合索引 {} 失败: {}", indexName, e.getMessage());
        }
    }

    private void createRelationshipIndex(Session session, String indexName, String relationshipType, String property) {
        try {
            String query = String.format(
                    "CREATE INDEX %s IF NOT EXISTS FOR ()-[r:%s]-() ON (r.%s)",
                    indexName, relationshipType, property);
            session.run(query);
            log.info("已验证关系索引: {} (Type: {}, Property: {})", indexName, relationshipType, property);
        } catch (Exception e) {
            log.warn("创建关系索引 {} 失败，可能是 Neo4j 版本不支持关系属性索引: {}", indexName, e.getMessage());
        }
    }
}
