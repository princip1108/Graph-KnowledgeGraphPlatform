# 数据库 Schema 说明与 SQL 修正

**创建日期**: 2026-02-04
**状态**: 已验证 (Verified)

---

## 1. 数据库表结构分析

经过对后端代码 (`com.sdu.kgplatform.entity`) 的分析，确认了实际运行的 MySQL 表结构。

### 1.1 核心表概览

| 实体类 | 数据库表名 | 主键 | 关键字段 |
| :--- | :--- | :--- | :--- |
| **KnowledgeGraph** | `knowledge_graph` | `graphId` | `uploader_id`, `name`, `status`, `view_count`, `share_link` |
| **User** | `users` | `userId` | `user_name`, `email`, `phone`, `role` |
| **Post** | `post` | `postId` | `author_id`, `post_title`, `graph_id` |
| **NodeEntity** (Neo4j) | `:Entity` | `nodeId` (业务ID) | `graphId`, `name`, `type` |

> **注意**: 之前的优化计划中，我们根据惯例推测图谱表名为 复数 `knowledge_graphs`，但实际代码中使用的是 单数 **`knowledge_graph`**。这是一个关键差异。

---

## 2. 修正后的 SQL 优化代码

请使用以下**经过验证**的 SQL 代码来创建 MySQL 索引。
(之前的代码如果执行会报错 `Table 'knowledge_graphs' doesn't exist`)。

### 2.1 必须执行的 MySQL 优化命令

请在 MySQL 数据库终端（或 Workbench / Navicat）中执行：

```sql
-- 1. 切换到您的数据库 (如果数据库名不是 kgplatform 请修改)
USE kgplatform;

-- 2. 优化 "我的图谱" 查询
-- 解决: findByUploaderId 慢的问题
ALTER TABLE knowledge_graph ADD INDEX idx_uploader_id (uploader_id);

-- 3. 优化 "公开大厅" 筛选
-- 解决: findByStatus 慢的问题
ALTER TABLE knowledge_graph ADD INDEX idx_status (status);

-- 4. 优化 "热门图谱" 用到的排序
-- 解决: ORDER BY view_count DESC 慢的问题
ALTER TABLE knowledge_graph ADD INDEX idx_status_view (status, view_count DESC);

-- 5. 优化 "根据链接获取图谱"
-- 解决: findByShareLink 全表扫描
ALTER TABLE knowledge_graph ADD UNIQUE INDEX uk_share_link (share_link);

-- 6. (可选) 优化帖子查询
-- 解决: "某用户的帖子" 或 "某图谱的帖子" 加载慢
ALTER TABLE post ADD INDEX idx_author_id (author_id);
ALTER TABLE post ADD INDEX idx_graph_id (graph_id);
```

---

## 3. Neo4j 部分再次确认

Neo4j 部分的实体 (`NodeEntity`) 之前分析无误，且我已经为您添加了 `DatabaseInitializer` 自动代码，**无需手动执行 SQL**。

但如果您想手动核对，正确的 Cypher 语句目标确实是 `:Entity` 标签：
```cypher
CREATE CONSTRAINT FOR (n:Entity) REQUIRE n.nodeId IS UNIQUE;
CREATE INDEX FOR (n:Entity) ON (n.graphId);
```

---

## 4. 总结
- **MySQL 表名更正**: `knowledge_graph` (单数), `post` (单数), `users` (复数)。
- **操作建议**: 请复制本文第 2.1 节的代码执行，以确保 MySQL 性能得到优化。
