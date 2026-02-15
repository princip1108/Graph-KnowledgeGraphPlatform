# Graph+ 智汇星图 — 知识图谱管理平台

> Knowledge Graph Platform：基于 Spring Boot + Neo4j + MySQL 的知识图谱可视化与社区协作平台。

---

## 技术栈

| 层级 | 技术 |
|------|------|
| **后端框架** | Spring Boot 4.0 / Java 21 |
| **关系数据库** | MySQL 8.0 |
| **图数据库** | Neo4j 5.x |
| **安全框架** | Spring Security（Session + OAuth2 GitHub 登录） |
| **前端模板** | Thymeleaf Layout Dialect + DaisyUI + TailwindCSS |
| **前端脚本** | 原生 JavaScript（模块化） |
| **邮件服务** | Spring Mail（163 SMTP） |
| **构建工具** | Maven |
| **容器化** | Docker + Docker Compose |

---

## 快速启动

### 环境要求

- JDK 21+
- MySQL 8.0+
- Neo4j 5.x
- Maven 3.8+

### 1. 克隆项目

```bash
git clone <仓库地址>
cd KnowledgeGraphPlatform
```

### 2. 配置环境变量

复制环境变量模板并填写实际值：

```bash
cp demo/.env.example demo/.env
```

必须配置的变量（详见 `.env.example` 文件注释）：

| 变量名 | 说明 |
|--------|------|
| `DB_PASSWORD` | MySQL root 密码 |
| `NEO4J_PASSWORD` | Neo4j 密码 |
| `GITHUB_CLIENT_ID` | GitHub OAuth App Client ID |
| `GITHUB_CLIENT_SECRET` | GitHub OAuth App Client Secret |
| `MAIL_USERNAME` | 163 邮箱账号 |
| `MAIL_AUTH_CODE` | 163 邮箱 SMTP 授权码 |

### 3a. 本地启动（开发模式）

确保 MySQL 和 Neo4j 已启动，且已创建数据库 `graph_db`：

```sql
CREATE DATABASE IF NOT EXISTS graph_db DEFAULT CHARSET utf8mb4;
```

```bash
cd demo
mvn spring-boot:run
```

访问 http://localhost:8080

### 3b. Docker 一键启动

```bash
cd demo
docker-compose up -d --build
```

自动启动 MySQL、Neo4j 和应用，访问 http://localhost:8080

---

## 项目结构

```
KnowledgeGraphPlatform/
├── demo/                          # 主工程
│   ├── src/main/java/.../
│   │   ├── common/                # 通用工具类
│   │   ├── config/                # 配置类（Security, Neo4j, JPA, MVC）
│   │   ├── controller/            # 控制器层（16 个 Controller）
│   │   ├── dto/                   # 数据传输对象
│   │   ├── entity/                # 实体类（MySQL + Neo4j）
│   │   ├── repository/            # 数据访问层
│   │   ├── security/              # 安全相关（OAuth2, UserDetails）
│   │   └── service/               # 业务逻辑层
│   ├── src/main/resources/
│   │   ├── application.yaml       # 主配置（引用环境变量）
│   │   ├── application-local.yaml # 本地开发配置（gitignored）
│   │   ├── application-prod.yaml  # 生产环境配置
│   │   ├── static/                # 前端静态资源（JS, CSS）
│   │   └── templates/             # Thymeleaf 模板
│   ├── Dockerfile
│   └── docker-compose.yml
├── uploads/                       # 用户上传文件存储
└── 项目报告汇总/                   # 项目文档
```

---

## 文档索引

所有详细文档位于 `项目报告汇总/` 目录：

| 文档 | 说明 |
|------|------|
| [API 接口总结](项目报告汇总/API接口总结.md) | 全部 125 个 REST API + 16 个页面路由 |
| [项目结构说明书](项目报告汇总/项目结构说明书.md) | 后端/前端模块详解、开发规范 |
| [部署指南](项目报告汇总/部署指南.md) | Docker 部署 & Linux 服务器上线流程 |
| [数据库 Schema 说明](项目报告汇总/数据库Schema说明与SQL修正.md) | 表结构 & 索引优化 SQL |
| [数据库初始化脚本](项目报告汇总/数据库初始化脚本.sql) | 完整建表 DDL + 初始数据 |
| [待实现功能清单](项目报告汇总/待实现功能清单.md) | 未完成功能 & 技术债务 |
| [已知问题清单](项目报告汇总/已知问题与遗留清单.md) | Bug、注意事项、临时方案 |
| [环境变量模板](demo/.env.example) | 所有必需配置项及获取方式 |

---

## 核心功能

- **图谱管理**：创建、编辑、可视化知识图谱（Neo4j 存储节点/关系）
- **图谱探索**：公开广场、高级筛选、个性化推荐
- **社区论坛**：发帖、评论、点赞、收藏、关注
- **私信系统**：用户间实时私信
- **用户系统**：注册/登录（含 GitHub OAuth2）、个人中心、密码找回
- **管理后台**：用户管理、帖子管理、公告管理
- **文件服务**：图谱上传/下载（JSON/CSV/PNG）、头像上传
- **历史记录**：搜索历史、浏览历史

---

## 常用命令

```bash
# 构建（跳过测试）
cd demo && mvn clean package -DskipTests

# 本地运行
cd demo && mvn spring-boot:run

# Docker 启动
cd demo && docker-compose up -d --build

# 查看日志
cd demo && docker-compose logs -f app

# 停止服务
cd demo && docker-compose down
```
