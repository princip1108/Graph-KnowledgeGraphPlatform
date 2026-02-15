package com.sdu.kgplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
            jdbcTemplate.execute("""
                        DO $$
                        DECLARE
                            r RECORD;
                            seq_name TEXT;
                            max_id BIGINT;
                        BEGIN
                            FOR r IN
                                SELECT tablename FROM pg_tables WHERE schemaname = 'public'
                            LOOP
                                seq_name := pg_get_serial_sequence(r.tablename, 'id');
                                IF seq_name IS NOT NULL THEN
                                    EXECUTE format('SELECT COALESCE(MAX(id), 0) FROM %I', r.tablename) INTO max_id;
                                    EXECUTE format('SELECT setval(''%s'', %s)', seq_name, max_id + 1);
                                    RAISE NOTICE 'Reset sequence % to %', seq_name, max_id + 1;
                                END IF;
                            END LOOP;
                        END $$;
                    """);

            log.info("=== PostgreSQL 自增序列重置完成 ===");
        } catch (Exception e) {
            log.warn("重置 PostgreSQL 序列时出错（不影响启动）: {}", e.getMessage());
        }
    }
}
