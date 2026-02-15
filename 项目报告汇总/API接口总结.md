# KnowledgeGraphPlatform API 接口总结

> 自动生成时间：2026-02-13

---

## 1. 认证模块 (`AuthController`)

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/register` | 用户注册（自动登录） | 公开 |
| POST | `/api/auth/send-code` | 发送邮箱验证码 | 公开 |
| POST | `/api/auth/verify-reset-code` | 找回密码 - 校验验证码 | 公开 |
| POST | `/api/auth/reset-password` | 找回密码 - 重置密码 | 公开 |

> 注：`GET /login` 为页面路由（返回登录注册页模板），不属于 REST API。

### 请求/响应示例

**POST /register**
- 参数：`userName`, `email`, `verificationCode`, `phone`(可选), `password`
- 成功：`{ "success": true, "redirect": "/graph/home.html" }`

**POST /api/auth/send-code**
- Body：`{ "email": "xxx@xx.com", "purpose": "register" }`
- 成功：`{ "message": "验证码已发送" }`

**POST /api/auth/reset-password**
- Body：`{ "email": "xxx", "newPassword": "xxx" }`
- 密码长度 8-20 位

---

## 2. 用户模块 (`UserController`)

基础路径：`/user`

> 注：`GET /user/profile` 为页面路由（返回个人中心模板），不属于 REST API。

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/user/api/profile` | 获取当前用户资料 | 登录 |
| PUT | `/user/api/profile` | 更新当前用户资料 | 登录 |
| GET | `/user/api/check-auth` | 检查登录状态 | 公开 |
| GET | `/user/api/users/{id}` | 获取指定用户资料（脱敏） | 公开 |
| POST | `/user/api/deactivate` | 用户自助注销账号 | 登录 |
| POST | `/user/api/change-password` | 修改密码（需旧密码+邮箱验证码） | 登录 |

### 关键说明
- `GET /user/api/check-auth` 返回 `{ authenticated: true/false, user: {...}, role: "..." }`
- `GET /user/api/users/{id}` 对已注销用户返回 `{ deleted: true, userName: "已注销用户" }`
- `POST /user/api/deactivate` OAuth 用户需传 `"CONFIRM"` 代替密码

---

## 3. 图谱模块 (`GraphController`)

基础路径：`/api/graph`

### 3.1 查询接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/graph/{id}?incrementView=true` | 获取图谱详情（默认自动增加浏览量，可设为 false 跳过） | 公开 |
| GET | `/api/graph/{id}/visualization` | 获取图谱可视化数据（轻量级） | 公开 |
| GET | `/api/graph/share/{shareLink}` | 根据分享链接获取图谱 | 公开 |
| GET | `/api/graph/my` | 获取当前用户的图谱列表 | 登录 |
| GET | `/api/graph/user/{userId}` | 获取指定用户的公开图谱 | 公开 |
| GET | `/api/graph/public` | 获取公开图谱列表（广场） | 公开 |
| GET | `/api/graph/search` | 搜索公开图谱（支持高级筛选） | 公开 |
| GET | `/api/graph/popular` | 获取热门图谱 | 公开 |
| GET | `/api/graph/recommend` | 个性化推荐图谱 | 公开 |
| GET | `/api/graph/my/stats` | 获取当前用户图谱统计 | 登录 |
| GET | `/api/graph/{id}/can-edit` | 检查是否可编辑图谱 | 公开 |

**GET /api/graph/my** 参数：
- `page`(0), `size`(10), `sortBy`, `status`, `categoryId`

**GET /api/graph/public** 参数：
- `page`(0), `size`(12), `sortBy`, `categoryId`

**GET /api/graph/search** 参数（`GraphSearchCriteria`）：
- `keyword`, `categoryId`, `domain`
- `startDate`, `endDate`
- `minNodes`, `maxNodes`, `minEdges`, `maxEdges`
- `minDensity`, `maxDensity`
- `minEntityRichness`, `maxEntityRichness`, `minRelationRichness`, `maxRelationRichness`
- `minViewCount`, `maxViewCount`, `minDownloadCount`, `maxDownloadCount`
- `page`(0), `size`(12), `sortBy`(viewCount/date/hot)

**GET /api/graph/recommend** 参数：
- `page`(0), `size`(12), `domain`(可选)
- 已登录：基于浏览历史的领域偏好；未登录：降级为热门排序

### 3.2 创建/更新接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/graph` | 创建新图谱 | USER/ADMIN |
| PUT | `/api/graph/{id}` | 更新图谱信息 | USER/ADMIN |
| PUT | `/api/graph/{id}/cover` | 更新图谱封面 | USER/ADMIN |
| PATCH | `/api/graph/{id}/status` | 更新图谱状态 | USER/ADMIN |

### 3.3 删除接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| DELETE | `/api/graph/{id}` | 删除图谱（需所有者） | USER/ADMIN |
| DELETE | `/api/graph/admin/{id}` | 管理员删除图谱 | ADMIN |

### 3.4 批量操作

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/graph/batch/publish` | 批量上线图谱 | USER/ADMIN |
| POST | `/api/graph/batch/offline` | 批量下线图谱 | USER/ADMIN |
| POST | `/api/graph/batch/delete` | 批量删除图谱 | USER/ADMIN |

- Body：`{ "graphIds": [1, 2, 3] }`

### 3.5 收藏接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/graph/{id}/favorite` | 收藏/取消收藏图谱 | 登录 |
| GET | `/api/graph/{id}/favorite/status` | 检查是否已收藏 | 公开 |
| GET | `/api/graph/favorites` | 获取用户收藏的图谱列表 | 登录 |

---

## 4. 节点与关系模块 (`NodeController`)

基础路径：`/api/graph/{graphId}`

### 4.1 节点接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/graph/{graphId}/nodes` | 获取图谱所有节点 | 公开 |
| GET | `/api/graph/{graphId}/nodes/{nodeId}` | 获取单个节点 | 公开 |
| GET | `/api/graph/{graphId}/nodes/search?keyword=xxx` | 搜索节点 | 公开 |
| GET | `/api/graph/{graphId}/nodes/type/{type}` | 按类型获取节点 | 公开 |
| GET | `/api/graph/{graphId}/node-types` | 获取节点类型列表 | 公开 |
| GET | `/api/graph/{graphId}/nodes/{nodeId}/neighbors?direction=all` | 获取节点邻居 | 公开 |
| POST | `/api/graph/{graphId}/nodes` | 创建节点 | 所有者 |
| POST | `/api/graph/{graphId}/nodes/batch` | 批量创建节点 | 所有者 |
| PUT | `/api/graph/{graphId}/nodes/{nodeId}` | 更新节点 | 所有者 |
| DELETE | `/api/graph/{graphId}/nodes/{nodeId}` | 删除节点 | 所有者 |
| DELETE | `/api/graph/{graphId}/nodes/batch` | 批量删除节点 | 所有者 |

### 4.2 关系接口

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/graph/{graphId}/relations` | 获取图谱所有关系 | 公开 |
| GET | `/api/graph/{graphId}/relations/type/{type}` | 按类型获取关系 | 公开 |
| GET | `/api/graph/{graphId}/relation-types` | 获取关系类型列表 | 公开 |
| GET | `/api/graph/{graphId}/relation-stats` | 获取关系类型统计 | 公开 |
| POST | `/api/graph/{graphId}/relations` | 创建关系 | 所有者 |
| POST | `/api/graph/{graphId}/relations/batch` | 批量创建关系 | 所有者 |
| DELETE | `/api/graph/{graphId}/relations/{relationId}` | 删除关系 | 所有者 |
| DELETE | `/api/graph/{graphId}/relations/batch` | 批量删除关系 | 所有者 |

---

## 5. 帖子/论坛模块 (`PostController`)

基础路径：`/api`

### 5.1 帖子 CRUD

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/posts` | 获取帖子列表 | 公开 |
| GET | `/api/posts/{id}` | 获取帖子详情 | 公开 |
| POST | `/api/posts` | 发布帖子 | 登录 |
| PUT | `/api/posts/{id}` | 更新帖子 | 登录（作者） |
| DELETE | `/api/posts/{id}` | 删除帖子 | 登录（作者） |

**GET /api/posts** 参数：
- `page`(0), `size`(10), `sort`(latest), `keyword`, `categoryId`

**POST /api/posts** Body：
```json
{
  "title": "标题",
  "abstract": "摘要",
  "content": "内容",
  "tags": ["标签1", "标签2"],
  "graphId": 123,
  "categoryId": 1,
  "category": "other"
}
```

### 5.2 帖子互动

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/posts/{id}/like` | 点赞/取消点赞 | 登录 |
| POST | `/api/posts/{id}/favorite` | 收藏/取消收藏帖子 | 登录 |
| GET | `/api/posts/{id}/favorite/status` | 检查收藏状态 | 公开 |

### 5.3 帖子列表

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/posts/stats` | 获取论坛统计 | 公开 |
| GET | `/api/posts/user/{userId}` | 获取用户帖子 | 公开 |
| GET | `/api/posts/hot` | 获取热门帖子 | 公开 |
| GET | `/api/posts/pinned` | 获取置顶帖子 | 公开 |
| GET | `/api/posts/related?graphId=xxx` | 获取图谱相关帖子 | 公开 |
| GET | `/api/tags/hot` | 获取热门标签 | 公开 |

### 5.4 用户社交

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/users/{id}/follow` | 关注/取消关注用户 | 登录 |
| GET | `/api/users/{id}/follow/status` | 检查关注状态 | 公开 |
| GET | `/api/user/favorites` | 获取用户收藏的帖子 | 登录 |
| GET | `/api/user/following` | 获取用户关注列表 | 登录 |
| GET | `/api/user/drafts` | 获取用户草稿帖子 | 登录 |

### 5.5 批量操作

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/posts/batch/online` | 批量上线帖子 | 登录（作者） |
| POST | `/api/posts/batch/offline` | 批量下线帖子 | 登录（作者） |
| POST | `/api/posts/batch/delete` | 批量删除帖子 | 登录（作者） |

### 5.6 管理员操作

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/admin/posts/{id}/pin` | 置顶/取消置顶帖子 | ADMIN |

---

## 6. 评论模块 (`CommentController`)

基础路径：`/api`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/posts/{postId}/comments` | 获取帖子评论列表 | 公开 |
| POST | `/api/posts/{postId}/comments` | 发表评论 | 登录 |
| DELETE | `/api/comments/{commentId}` | 删除评论 | 登录（作者） |

**POST /api/posts/{postId}/comments** Body：
```json
{
  "text": "评论内容",
  "parentCommentId": null
}
```

---

## 7. 管理员模块 (`AdminController`)

基础路径：`/api/admin`（全部需要 ADMIN 角色）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/admin/posts` | 获取所有帖子（论坛管理） |
| GET | `/api/admin/users` | 获取所有用户列表 |
| GET | `/api/admin/users/search` | 搜索用户（支持筛选+分页） |
| GET | `/api/admin/stats/users` | 获取用户统计信息 |
| PUT | `/api/admin/users/{userId}/role` | 修改用户角色 |
| PUT | `/api/admin/users/{userId}/ban` | 封禁用户 |
| PUT | `/api/admin/users/{userId}/unban` | 解封用户 |
| PUT | `/api/admin/users/{userId}/status` | 禁用/启用用户 |
| DELETE | `/api/admin/users/{userId}` | 删除用户 |

**GET /api/admin/users/search** 参数：
- `keyword`(""), `filter`(ALL), `page`(0), `size`(20)

**PUT /api/admin/users/{userId}/ban** Body：
- `{ "hours": 24 }` — 0 或不传为永久封禁

---

## 8. 公告模块 (`AnnouncementController`)

基础路径：`/api/announcements`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/announcements` | 获取活跃公告列表（Top 5） | 公开 |
| POST | `/api/announcements` | 发布公告 | ADMIN |
| DELETE | `/api/announcements/{id}` | 删除公告 | ADMIN |
| POST | `/api/announcements/{id}/toggle` | 停用/启用公告 | ADMIN |
| POST | `/api/announcements/{id}/pin` | 置顶/取消置顶公告 | ADMIN |

---

## 9. 私信模块 (`MessageController`)

基础路径：`/api/messages`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/messages/conversations` | 获取会话列表 | 登录 |
| GET | `/api/messages/history?otherUserId=123` | 获取聊天记录（自动标记已读） | 登录 |
| POST | `/api/messages` | 发送私信 | 登录 |
| GET | `/api/messages/unread-count` | 获取未读消息数 | 登录 |
| DELETE | `/api/messages/{id}` | 删除私信 | 登录 |

**POST /api/messages** Body：
```json
{
  "receiverId": 123,
  "content": "消息内容"
}
```

---

## 10. 历史记录模块 (`HistoryController`)

基础路径：`/api/history`

### 10.1 搜索历史

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/history/search?type=graph` | 获取搜索历史 | 登录 |
| POST | `/api/history/search?type=graph&keyword=xxx` | 记录搜索历史 | 登录 |
| DELETE | `/api/history/search?type=graph` | 清空搜索历史 | 登录 |
| DELETE | `/api/history/search/item?type=graph&keyword=xxx` | 删除单条搜索历史 | 登录 |

### 10.2 浏览历史

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/history/browsing?type=graph` | 获取浏览历史 | 登录 |
| DELETE | `/api/history/browsing?type=graph` | 清空浏览历史 | 登录 |
| DELETE | `/api/history/browsing/{id}` | 删除单条浏览历史 | 登录 |

- `type` 参数：`graph` 或 `post`
- 分页参数：`page`(0), `size`(20)

---

## 11. 文件上传模块 (`FileUploadController`)

基础路径：`/api/upload`

| 方法 | 路径 | 说明 | 权限 | 文件限制 |
|------|------|------|------|---------|
| POST | `/api/upload/avatar` | 上传头像 | 登录 | 图片, ≤2MB |
| POST | `/api/upload/image` | 上传帖子图片 | 登录 | 图片, ≤5MB |
| POST | `/api/upload/cover` | 上传图谱封面 | 登录 | 图片, ≤5MB |
| POST | `/api/upload/graph` | 上传图谱文件（JSON） | 登录 | JSON |

**POST /api/upload/graph** 参数：
- `file`(必填): JSON 图谱文件
- `name`, `description`: 可选
- `status`(DRAFT), `domain`(other)
- `cover`: 可选封面图片

---

## 12. 下载模块 (`DownloadController`)

基础路径：`/api/download`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/download/{graphId}?format=json` | 下载图谱（JSON格式） | 公开 |
| GET | `/api/download/{graphId}?format=csv` | 下载图谱（CSV格式） | 公开 |
| GET | `/api/download/{graphId}?format=png` | 下载图谱（PNG图片） | 公开 |

---

## 13. 分类模块

### DomainCategoryController

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/categories` | 获取所有领域分类（按排序） | 公开 |

### CategoryController

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| GET | `/api/category/graph` | 获取图谱分类列表 | 公开 |

---

## 14. 反馈模块 (`FeedbackController`)

基础路径：`/api/feedback`

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/feedback` | 提交用户反馈 | 公开（可匿名） |

**POST /api/feedback** Body：
```json
{
  "feedbackType": "bug",
  "subject": "主题",
  "content": "内容",
  "email": "联系邮箱"
}
```

---

## 15. 页面路由 (`PageController`)

非 API 接口，返回 Thymeleaf 模板页面：

| 路径 | 页面说明 |
|------|---------|
| `/` 或 `/graph/home.html` | 首页 |
| `/graph/graph_list.html` | 图谱探索页 |
| `/graph/graph_detail.html` | 图谱详情页 |
| `/user/login_register.html` | 登录注册页 |
| `/user/profile.html` | 个人中心 |
| `/user/registration_success.html` | 注册成功提示页 |
| `/user/password_reset.html` | 密码重置页 |
| `/community/forum_list.html` | 论坛列表页 |
| `/community/post_detail.html` | 帖子详情页 |
| `/community/post_edit.html` | 帖子编辑页 |
| `/community/feedback.html` | 反馈页 |
| `/pages/documentation.html` | 文档页 |
| `/pages/about.html` | 关于页 |
| `/pages/privacy.html` | 隐私政策 |
| `/pages/terms.html` | 服务条款 |
| `/admin/dashboard.html` | 管理员仪表盘 |

---

## 统计汇总

| 模块 | 接口数量 |
|------|---------|
| 认证 | 4 |
| 用户 | 6 |
| 图谱 | 22 |
| 节点与关系 | 19 |
| 帖子/论坛 | 23 |
| 评论 | 3 |
| 管理员 | 9 |
| 公告 | 5 |
| 私信 | 5 |
| 历史记录 | 7 |
| 文件上传 | 4 |
| 下载 | 1（3种格式） |
| 分类 | 2 |
| 反馈 | 1 |
| 页面路由 | 16 |
| **合计** | **~125 (REST API) + 16 (页面路由)** |
