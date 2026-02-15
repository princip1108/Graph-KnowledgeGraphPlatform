-- ============================================================
-- Graph+ 智汇星图 — MySQL 数据库初始化脚本
-- ============================================================
-- 生成时间：2026-02-13
-- 说明：基于 JPA 实体类手动编写的 DDL，用于全新环境初始化。
--       项目配置了 ddl-auto: update，正常启动会自动建表，
--       但本脚本可用于：手动建库、数据迁移、文档备查。
--
-- 使用方式：
--   mysql -u root -p < 数据库初始化脚本.sql
-- ============================================================

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS graph_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE graph_db;

-- ============================================================
-- 2. 用户相关表
-- ============================================================

-- 2.1 用户表
CREATE TABLE IF NOT EXISTS `users` (
    `user_id`        INT AUTO_INCREMENT PRIMARY KEY,
    `user_name`      VARCHAR(100),
    `gender`         VARCHAR(20)   COMMENT '枚举: MALE, FEMALE, OTHER',
    `birthday`       DATE,
    `created_at`     DATETIME      COMMENT '注册时间（自动填充）',
    `update_at`      DATETIME      COMMENT '最近更新时间（自动填充）',
    `email`          VARCHAR(100),
    `phone`          VARCHAR(20),
    `password_hash`  VARCHAR(300),
    `institution`    VARCHAR(100),
    `email_verified`  TINYINT(1),
    `phone_verified`  TINYINT(1),
    `profile`        TEXT          COMMENT '个人简介（长文本）',
    `user_status`    VARCHAR(20)   COMMENT '枚举: ACTIVE, BANNED, DEACTIVATED',
    `avatar`         VARCHAR(200),
    `bio`            VARCHAR(500),
    `nickname`       VARCHAR(200),
    `role`           VARCHAR(20)   COMMENT '枚举: ADMIN, USER, VISITOR',
    `last_login_at`  DATETIME,
    `banned_until`   DATETIME,
    `github_id`      VARCHAR(255) UNIQUE,
    `oauth_provider` VARCHAR(20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2.2 用户关注关系表（复合主键）
CREATE TABLE IF NOT EXISTS `user_follow` (
    `follower_id`  INT NOT NULL,
    `followed_id`  INT NOT NULL,
    `follow_time`  DATETIME,
    PRIMARY KEY (`follower_id`, `followed_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2.3 OAuth 账户表
CREATE TABLE IF NOT EXISTS `oauth_accounts` (
    `id`                INT AUTO_INCREMENT PRIMARY KEY,
    `user_id`           INT,
    `platform_name`     VARCHAR(20)   COMMENT '枚举: GITHUB',
    `platform_uid`      VARCHAR(128),
    `access_token`      VARBINARY(1024),
    `refresh_token`     VARBINARY(1024),
    `scope`             VARCHAR(512),
    `platform_nickname` VARCHAR(100),
    `created_at`        DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2.4 邮箱验证码表
CREATE TABLE IF NOT EXISTS `email_verification_codes` (
    `id`         BIGINT AUTO_INCREMENT PRIMARY KEY,
    `email`      VARCHAR(100) NOT NULL,
    `code`       VARCHAR(6)   NOT NULL,
    `expire_at`  DATETIME     NOT NULL,
    `used`       TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at` DATETIME     NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 3. 图谱相关表
-- ============================================================

-- 3.1 知识图谱元数据表
CREATE TABLE IF NOT EXISTS `knowledge_graph` (
    `graph_id`                   INT AUTO_INCREMENT PRIMARY KEY,
    `uploader_id`                INT,
    `name`                       VARCHAR(255),
    `description`                VARCHAR(900),
    `status`                     VARCHAR(20)    COMMENT '枚举: DRAFT, PUBLISHED, PRIVATE',
    `upload_date`                DATE,
    `last_modified`              DATETIME,
    `cover_image`                VARCHAR(512),
    `is_custom_cover`            TINYINT(1)     DEFAULT 0,
    `share_link`                 VARCHAR(255),
    `cached_file_path`           VARCHAR(512),
    `cached_file_format`         VARCHAR(20)    COMMENT '枚举: JSON, CSV, PNG',
    `cached_generation_datetime` DATETIME,
    `is_cache_valid`             TINYINT(1),
    `view_count`                 INT            DEFAULT 0,
    `download_count`             INT            DEFAULT 0,
    `collect_count`              INT            DEFAULT 0,
    `node_count`                 INT            DEFAULT 0,
    `relation_count`             INT            DEFAULT 0,
    `density`                    DECIMAL(5,2)   DEFAULT 0.00,
    `relation_richness`          DECIMAL(5,2)   DEFAULT 0.00,
    `entity_richness`            DECIMAL(5,2)   DEFAULT 0.00,
    `category_id`                INT,
    `domain`                     VARCHAR(50)    DEFAULT 'other',
    `hot_score`                  DOUBLE         DEFAULT 0.0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3.2 图谱收藏表（复合主键）
CREATE TABLE IF NOT EXISTS `graph_favorite` (
    `user_id`    INT NOT NULL,
    `graph_id`   INT NOT NULL,
    `created_at` DATETIME,
    PRIMARY KEY (`user_id`, `graph_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3.3 图谱标签关联表（复合主键）
CREATE TABLE IF NOT EXISTS `graph_tags` (
    `graph_id` INT NOT NULL,
    `tag_id`   INT NOT NULL,
    PRIMARY KEY (`graph_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 4. 论坛/帖子相关表
-- ============================================================

-- 4.1 帖子表
CREATE TABLE IF NOT EXISTS `post` (
    `post_id`       INT AUTO_INCREMENT PRIMARY KEY,
    `author_id`     INT,
    `post_title`    VARCHAR(255),
    `post_abstract` VARCHAR(900),
    `post_text`     LONGTEXT,
    `post_status`   VARCHAR(20)    COMMENT '枚举: 已发布, 草稿, 仅自己可见',
    `like_count`    INT            DEFAULT 0,
    `upload_time`   DATETIME,
    `graph_id`      INT            COMMENT '关联的图谱 ID（可为空）',
    `is_pinned`     TINYINT(1)     DEFAULT 0,
    `view_count`    INT            DEFAULT 0,
    `favorite_count` INT           DEFAULT 0,
    `category_id`   INT,
    `category`      VARCHAR(50)    DEFAULT 'other'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4.2 评论表
CREATE TABLE IF NOT EXISTS `comment` (
    `comment_id`       INT AUTO_INCREMENT PRIMARY KEY,
    `comment_text`     TEXT,
    `comment_time`     DATETIME,
    `user_id`          INT,
    `post_id`          INT,
    `parent_comment_id` INT         COMMENT '父评论 ID（支持嵌套回复）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4.3 帖子点赞表（复合主键）
CREATE TABLE IF NOT EXISTS `post_like` (
    `user_id`   INT NOT NULL,
    `post_id`   INT NOT NULL,
    `like_time` DATETIME,
    PRIMARY KEY (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4.4 帖子收藏表（复合主键）
CREATE TABLE IF NOT EXISTS `post_favorite` (
    `user_id`       INT NOT NULL,
    `post_id`       INT NOT NULL,
    `favorite_time` DATETIME,
    PRIMARY KEY (`user_id`, `post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4.5 标签表
CREATE TABLE IF NOT EXISTS `tag` (
    `tag_id`      INT AUTO_INCREMENT PRIMARY KEY,
    `tag_name`    VARCHAR(100),
    `created_at`  DATETIME,
    `usage_count` INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4.6 帖子标签关联表（复合主键）
CREATE TABLE IF NOT EXISTS `post_tag` (
    `post_id` INT NOT NULL,
    `tag_id`  INT NOT NULL,
    PRIMARY KEY (`post_id`, `tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 5. 私信、公告、反馈
-- ============================================================

-- 5.1 私信表
CREATE TABLE IF NOT EXISTS `message` (
    `message_id`     INT AUTO_INCREMENT PRIMARY KEY,
    `sender_id`      INT,
    `to_id`          INT,
    `send_time`      DATETIME,
    `message_status` VARCHAR(20)    COMMENT '枚举: SENT, READ, DELETED',
    `message_text`   TEXT,
    `is_read`        TINYINT(1)     DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5.2 公告表
CREATE TABLE IF NOT EXISTS `announcement` (
    `id`         INT AUTO_INCREMENT PRIMARY KEY,
    `title`      VARCHAR(255) NOT NULL,
    `content`    VARCHAR(1000),
    `type`       VARCHAR(20)  DEFAULT 'INFO' COMMENT '枚举: INFO, WARNING, UPDATE, EVENT',
    `author_id`  INT,
    `is_active`  TINYINT(1)   DEFAULT 1,
    `is_pinned`  TINYINT(1)   DEFAULT 0,
    `created_at` DATETIME,
    `updated_at` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5.3 用户反馈表
CREATE TABLE IF NOT EXISTS `user_feedback` (
    `feedback_id`  INT AUTO_INCREMENT PRIMARY KEY,
    `user_id`      INT            COMMENT '可为空（允许匿名反馈）',
    `subject`      VARCHAR(100),
    `content`      TEXT,
    `contact_info` VARCHAR(255),
    `submit_time`  DATETIME,
    `handler_id`   INT            COMMENT '处理人（管理员 ID）'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 6. 分类相关
-- ============================================================

-- 6.1 通用分类表（图谱分类 / 帖子分类）
CREATE TABLE IF NOT EXISTS `category` (
    `category_id` INT AUTO_INCREMENT PRIMARY KEY,
    `name`        VARCHAR(50) NOT NULL,
    `type`        VARCHAR(20) NOT NULL COMMENT 'GRAPH 或 POST',
    `priority`    INT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6.2 领域分类表
CREATE TABLE IF NOT EXISTS `categories` (
    `id`         INT AUTO_INCREMENT PRIMARY KEY,
    `name`       VARCHAR(255) NOT NULL,
    `code`       VARCHAR(255) NOT NULL UNIQUE,
    `icon`       VARCHAR(255),
    `sort_order` INT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 7. 历史记录
-- ============================================================

-- 7.1 搜索历史表
CREATE TABLE IF NOT EXISTS `search_history` (
    `id`          INT AUTO_INCREMENT PRIMARY KEY,
    `user_id`     INT,
    `query_text`  VARCHAR(255),
    `search_type` VARCHAR(20) COMMENT 'graph 或 forum',
    `search_time` DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7.2 浏览历史表
CREATE TABLE IF NOT EXISTS `browsing_history` (
    `id`            INT AUTO_INCREMENT PRIMARY KEY,
    `user_id`       INT,
    `resource_type` VARCHAR(20) COMMENT '枚举: graph, post',
    `post_id`       INT,
    `graph_id`      INT,
    `view_time`     DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 8. 其他
-- ============================================================

-- 8.1 团队成员表（关于页面展示用）
CREATE TABLE IF NOT EXISTS `team_member` (
    `member_id`            INT AUTO_INCREMENT PRIMARY KEY,
    `name`                 VARCHAR(100),
    `role`                 VARCHAR(50),
    `avatar_url`           VARCHAR(512),
    `bio`                  VARCHAR(500),
    `professional_field`   VARCHAR(100),
    `work_experience`      TEXT,
    `education_background` TEXT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 9. 推荐索引（性能优化）
-- ============================================================
-- 参考文档：数据库Schema说明与SQL修正.md

ALTER TABLE `knowledge_graph` ADD INDEX IF NOT EXISTS `idx_uploader_id`   (`uploader_id`);
ALTER TABLE `knowledge_graph` ADD INDEX IF NOT EXISTS `idx_status`        (`status`);
ALTER TABLE `knowledge_graph` ADD INDEX IF NOT EXISTS `idx_status_view`   (`status`, `view_count` DESC);
ALTER TABLE `knowledge_graph` ADD UNIQUE INDEX IF NOT EXISTS `uk_share_link` (`share_link`);

ALTER TABLE `post` ADD INDEX IF NOT EXISTS `idx_author_id` (`author_id`);
ALTER TABLE `post` ADD INDEX IF NOT EXISTS `idx_graph_id`  (`graph_id`);

ALTER TABLE `browsing_history` ADD INDEX IF NOT EXISTS `idx_bh_user_type` (`user_id`, `resource_type`);
ALTER TABLE `search_history`   ADD INDEX IF NOT EXISTS `idx_sh_user_type` (`user_id`, `search_type`);
ALTER TABLE `message`          ADD INDEX IF NOT EXISTS `idx_msg_sender`   (`sender_id`);
ALTER TABLE `message`          ADD INDEX IF NOT EXISTS `idx_msg_to`       (`to_id`);

-- ============================================================
-- 10. 初始数据
-- ============================================================

-- 10.1 默认管理员账号（密码需用 BCrypt 加密后替换）
-- 注意：下方 password_hash 是 "admin123" 的 BCrypt 哈希示例，
-- 请在生产环境中替换为真实密码的哈希值。
-- INSERT INTO `users` (`user_name`, `email`, `password_hash`, `role`, `user_status`, `created_at`, `update_at`)
-- VALUES ('admin', 'admin@example.com', '$2a$10$xxxxxx...', 'ADMIN', 'ACTIVE', NOW(), NOW());

-- 10.2 默认领域分类
INSERT IGNORE INTO `categories` (`name`, `code`, `icon`, `sort_order`) VALUES
    ('计算机科学', 'cs',        '💻', 1),
    ('自然科学',   'science',   '🔬', 2),
    ('人文社科',   'humanities', '📚', 3),
    ('医学健康',   'medical',   '🏥', 4),
    ('工程技术',   'engineering','⚙️', 5),
    ('经济金融',   'finance',   '💰', 6),
    ('法律',       'law',       '⚖️', 7),
    ('教育',       'education', '🎓', 8),
    ('其他',       'other',     '📦', 99);

-- 10.3 默认图谱分类
INSERT IGNORE INTO `category` (`name`, `type`, `priority`) VALUES
    ('知识百科', 'GRAPH', 1),
    ('人物关系', 'GRAPH', 2),
    ('学科体系', 'GRAPH', 3),
    ('企业组织', 'GRAPH', 4),
    ('其他',     'GRAPH', 99);

-- 10.4 默认帖子分类
INSERT IGNORE INTO `category` (`name`, `type`, `priority`) VALUES
    ('使用教程', 'POST', 1),
    ('经验分享', 'POST', 2),
    ('问题求助', 'POST', 3),
    ('功能建议', 'POST', 4),
    ('其他',     'POST', 99);

-- ============================================================
-- Neo4j 初始化（在 Neo4j Browser 中执行）
-- ============================================================
-- CREATE CONSTRAINT FOR (n:Entity) REQUIRE n.nodeId IS UNIQUE;
-- CREATE INDEX FOR (n:Entity) ON (n.graphId);
-- ============================================================
