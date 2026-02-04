# KnowledgeGraphPlatform 项目结构说明

> 知识图谱管理平台 - Graph+智汇星图

---

## 技术栈

| 层级 | 技术 |
|------|------|
| **前端模板** | Thymeleaf Layout Dialect + DaisyUI + TailwindCSS |
| **前端脚本** | 原生JavaScript (模块化) |
| **后端框架** | Spring Boot 3.x |
| **关系数据库** | MySQL |
| **图数据库** | Neo4j |
| **安全框架** | Spring Security |

---

## 目录结构

```
KnowledgeGraphPlatform/
├── demo/
│   └── src/main/
│       ├── java/com/sdu/kgplatform/
│       │   ├── common/              # 通用工具类
│       │   ├── config/              # 配置类
│       │   ├── controller/          # 控制器层
│       │   ├── dto/                 # 数据传输对象
│       │   ├── entity/              # 实体类
│       │   ├── exception/           # 异常处理
│       │   ├── repository/          # 数据访问层
│       │   ├── security/            # 安全相关
│       │   └── service/             # 业务逻辑层
│       └── resources/
│           ├── static/
│           │   ├── css/             # 样式文件
│           │   └── js/              # JavaScript文件
│           └── templates/
│               ├── layout/          # 布局模板
│               ├── fragments/       # 公共片段
│               ├── admin/           # 管理后台
│               ├── community/       # 社区论坛
│               ├── graph/           # 图谱相关
│               ├── pages/           # 静态页面
│               └── user/            # 用户相关
└── PROJECT_STRUCTURE.md
```

---

## 后端模块说明

### common/ - 通用工具类

| 文件 | 说明 |
|------|------|
| `SecurityUtils.java` | 安全工具类，提供获取当前用户、检查登录状态等方法 |
| `Result.java` | 统一API响应包装类 |

### config/ - 配置类

| 文件 | 说明 |
|------|------|
| `Neo4jConfig.java` | Neo4j数据库配置 |
| `SecurityConfig.java` | Spring Security配置 |
| `WebMvcConfig.java` | Web MVC配置 |
| `JpaConfig.java` | JPA配置（MySQL） |

### controller/ - 控制器层

| 文件 | 说明 | 主要端点 |
|------|------|---------|
| `GraphController.java` | 图谱管理 | `/api/graph/*` |
| `NodeController.java` | 节点管理 | `/api/graph/{id}/nodes/*` |
| `RelationshipController.java` | 关系管理 | `/api/graph/{id}/relations/*` |
| `PostController.java` | 帖子管理 | `/api/posts/*` |
| `CommentController.java` | 评论管理 | `/api/posts/{id}/comments/*` |
| `UserController.java` | 用户管理 | `/user/*` |
| `AuthController.java` | 认证相关 | `/auth/*` |
| `AdminController.java` | 管理后台 | `/admin/*` |
| `FileController.java` | 文件上传 | `/api/upload/*` |

### dto/ - 数据传输对象

| 文件 | 说明 |
|------|------|
| `GraphDetailDto.java` | 图谱详情DTO |
| `GraphListDto.java` | 图谱列表DTO |
| `NodeDto.java` | 节点DTO |
| `RelationshipDto.java` | 关系DTO |
| `UserProfileDto.java` | 用户资料DTO |

### entity/ - 实体类

| 文件 | 数据库 | 说明 |
|------|--------|------|
| `KnowledgeGraph.java` | MySQL | 知识图谱元数据 |
| `User.java` | MySQL | 用户信息 |
| `Post.java` | MySQL | 帖子信息 |
| `Comment.java` | MySQL | 评论信息 |
| `NodeEntity.java` | Neo4j | 图谱节点 |
| `RelationshipEntity.java` | Neo4j | 图谱关系 |

### exception/ - 异常处理

| 文件 | 说明 |
|------|------|
| `BusinessException.java` | 业务异常基类 |
| `GlobalExceptionHandler.java` | 全局异常处理器 |

### service/ - 业务逻辑层

| 文件 | 说明 |
|------|------|
| `GraphService.java` | 图谱业务逻辑 |
| `NodeService.java` | 节点业务逻辑 |
| `RelationshipService.java` | 关系业务逻辑 |
| `UserService.java` | 用户业务逻辑 |
| `PostService.java` | 帖子业务逻辑 |
| `CommentService.java` | 评论业务逻辑 |
| `AdminService.java` | 管理后台业务逻辑 |
| `FileStorageService.java` | 文件存储服务 |
| `GraphImportService.java` | 图谱导入服务 |

---

## 前端模块说明

### templates/layout/ - 布局模板

| 文件 | 说明 |
|------|------|
| `base.html` | 基础布局模板，所有页面继承此模板 |

### templates/fragments/ - 公共片段

| 文件 | 说明 |
|------|------|
| `header.html` | 导航栏片段 |
| `footer.html` | 页脚片段 |
| `common_styles.html` | 公共样式引用 |
| `common_scripts.html` | 公共脚本引用 |

### templates/ - 页面模板

| 目录 | 页面 | 说明 |
|------|------|------|
| **admin/** | `dashboard.html` | 管理后台仪表盘 |
| **community/** | `forum_list.html` | 论坛列表 |
| | `post_detail.html` | 帖子详情 |
| | `post_edit.html` | 帖子编辑 |
| | `feedback.html` | 反馈页面 |
| **graph/** | `home.html` | 首页 |
| | `graph_list.html` | 图谱列表 |
| | `graph_detail.html` | 图谱详情 |
| **pages/** | `about.html` | 关于页面 |
| | `documentation.html` | 使用文档 |
| **user/** | `login_register.html` | 登录注册 |
| | `profile.html` | 个人中心 |
| | `password_reset.html` | 密码重置 |
| | `registration_success.html` | 注册成功 |

### static/js/ - JavaScript模块

| 文件 | 说明 |
|------|------|
| `home.js` | 首页逻辑 |
| `graph-list.js` | 图谱列表逻辑 |
| `graph-detail-page.js` | 图谱详情页面逻辑 |
| `graph-detail-core.js` | 图谱核心功能 |
| `graph-detail-search.js` | 图谱搜索功能 |
| `graph-detail-query.js` | 图谱查询功能 |
| `graph-detail-edit.js` | 图谱编辑功能 |
| `forum-list.js` | 论坛列表逻辑 |
| `post-detail.js` | 帖子详情逻辑 |
| `post-edit.js` | 帖子编辑逻辑 |
| `profile.js` | 个人中心逻辑 |
| `dashboard.js` | 管理后台逻辑 |
| `login-register.js` | 登录注册逻辑 |

### static/css/ - 样式文件

| 文件 | 说明 |
|------|------|
| `home.css` | 首页样式 |
| `graph-detail.css` | 图谱详情样式 |
| `post-detail.css` | 帖子详情样式 |
| `post-edit.css` | 帖子编辑样式 |
| `forum-list.css` | 论坛列表样式 |
| `profile.css` | 个人中心样式 |
| `dashboard.css` | 管理后台样式 |

---

## 前端模板规范

所有页面均使用 Thymeleaf Layout Dialect 继承 `layout/base.html`：

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org" 
      xmlns:layout="http://www.ultraq.net.nz/thymeleaf/layout"
      layout:decorate="~{layout/base}">
<head>
    <title>页面标题 - GraphWisdom智汇星图</title>
    <th:block layout:fragment="css">
        <!-- 页面特定CSS -->
    </th:block>
</head>
<body>
    <div layout:fragment="content">
        <!-- 页面内容 -->
    </div>
    <th:block layout:fragment="js">
        <!-- 页面特定JS -->
    </th:block>
</body>
</html>
```

---

## API响应格式

所有API使用统一的响应格式：

```json
{
    "success": true,
    "code": 200,
    "message": "操作成功",
    "data": { ... },
    "error": null
}
```

错误响应：

```json
{
    "success": false,
    "code": 400,
    "message": null,
    "data": null,
    "error": "错误信息"
}
```

---

## 数据库设计

### MySQL 表

- `users` - 用户表
- `knowledge_graphs` - 知识图谱元数据表
- `posts` - 帖子表
- `comments` - 评论表
- `post_likes` - 帖子点赞表
- `post_favorites` - 帖子收藏表
- `graph_favorites` - 图谱收藏表
- `user_follows` - 用户关注表

### Neo4j 节点/关系

- `:Entity` - 图谱节点
  - `nodeId` - 节点业务ID
  - `graphId` - 所属图谱ID
  - `name` - 节点名称
  - `type` - 节点类型
  - `description` - 节点描述

- `[:RELATES_TO]` - 节点关系
  - `type` - 关系类型
  - `graphId` - 所属图谱ID

---

## 启动说明

### 环境要求

- JDK 17+
- MySQL 8.0+
- Neo4j 5.x
- Maven 3.8+

### 配置文件

编辑 `application.yml` 或 `application.properties`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/kgplatform
    username: root
    password: your_password
  neo4j:
    uri: bolt://localhost:7687
    authentication:
      username: neo4j
      password: your_password
```

### 启动命令

```bash
cd demo
mvn spring-boot:run
```

访问 http://localhost:8080

---

## 开发规范

### 后端规范

1. **依赖注入**：使用构造器注入
2. **日志记录**：使用 SLF4J Logger，禁止 `System.out.println`
3. **异常处理**：使用 `BusinessException` + `GlobalExceptionHandler`
4. **响应格式**：使用 `Result<T>` 统一包装

### 前端规范

1. **模板继承**：所有页面使用 `layout:decorate`
2. **JS模块化**：页面JS放入独立文件
3. **URL引用**：使用 `th:href` 和 `th:src`

---

*文档更新：2026-01-16*
