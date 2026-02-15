package com.sdu.kgplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * PostgreSQL 序列重置器
 * 在生产环境启动时自动将所有表的自增序列重置到当前最大 ID 之后，
 * 避免从 MySQL 迁移数据后出现主键冲突 (duplicate key) 问题。
 * 仅在 prod profile 下激活。
 */
@Component
@Profile("prod")
public class PostgresSequenceResetRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PostgresSequenceResetRunner.class);

    private final JdbcTemplate jdbcTemplate;

    public PostgresSequenceResetRunner(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        log.info("=== 开始重置 PostgreSQL 自增序列 ===");

        try {
            // 查询所有 public schema 下的序列
            List<Map<String, Object>> sequences = jdbcTemplate.queryForList(
                    "SELECT sequence_name FROM information_schema.sequences WHERE sequence_schema = 'public'");

            for (Map<String, Object> seq : sequences) {
                String seqName = seq.get("sequence_name").toString();
                try {
                    // 从序列名推断表名（约定：表名_列名_seq）
                    // 例如 email_verification_codes_id_seq -> email_verification_codes
                    String tableName = seqName;
                    if (seqName.endsWith("_id_seq")) {
                        tableName = seqName.substring(0, seqName.length() - "_id_seq".length());
                    } else if (seqName.endsWith("_seq")) {
                        tableName = seqName.substring(0, seqName.length() - "_seq".length());
                    }

                    // 检查表是否存在且有 id 列
                    List<Map<String, Object>> check = jdbcTemplate.queryForList(
                            "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ? AND column_name = 'id'",
                            tableName);

                    if (!check.isEmpty()) {
                        Long maxId = jdbcTemplate.queryForObject(
                                "SELECT COALESCE(MAX(id), 0) FROM \"" + tableName + "\"", Long.class);
                        // setval 第三个参数 false 表示下一次 nextval 返回的就是设置的值
                        jdbcTemplate.execute(
                                "SELECT setval('\"" + seqName + "\"', " + (maxId + 1) + ", false)");
                        log.info("重置序列 {} -> 下一个 ID 将从 {} 开始 (表 {} 当前最大 ID: {})", seqName, maxId + 1, tableName, maxId);
                    } else {
                        log.info("跳过序列 {} (未找到对应表 {} 的 id 列)", seqName, tableName);
                    }
                } catch (Exception e) {
                    log.warn("重置序列 {} 时出错: {}", seqName, e.getMessage());
                }
            }

            log.info("=== PostgreSQL 自增序列重置完成 ===");
        } catch (Exception e) {
            log.error("查询序列列表时出错（不影响启动）: {}", e.getMessage());
        }
    }
}
