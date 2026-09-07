# 知序智能知识库 - 后端 API 文档

> **免责声明**：本文档由代码库自动生成，基于 `backend/src/main/java/com/zhixu/kb/**/controller/` 下所有 Controller 的注解与源码。内容可能与最新实现存在偏差，建议结合 Swagger UI (`/swagger-ui.html`) 与单元测试进行核对。

---

## 1. 通用约定

### 1.1 基础信息

| 项目 | 说明 |
|---|---|
| 基础路径 | `http://localhost:8080`（开发）；Docker 环境下前端 Nginx 反代 `/api`） |
| 统一响应 | `Result<T>` 包装：`{ "code": 0, "message": "...", "data": T }`；非 0 为业务错误 |
| 认证方式 | JWT 会话 Cookie（`ZHIXU_SESSION`，HttpOnly + SameSite=Lax）：登录后由 Set-Cookie 下发，浏览器自动携带；非浏览器 API 调用仍可用 Header `Authorization: Bearer <token>` |
| 内容类型 | `application/json`；文件上传使用 `multipart/form-data`；SSE 流式问答返回 `text/event-stream` |
| 分页参数 | `page` 从 1 开始，`size` 默认 10 |

### 1.2 标准响应示例

```json
// 成功
{
  "code": 0,
  "message": "ok",
  "data": { ... }
}

// 失败
{
  "code": 400,
  "message": "用户名不能为空",
  "data": null
}
```

### 1.3 权限说明

| 标识 | 说明 |
|---|---|
| 公开 | 无需 Token |
| 登录用户 | 需要有效 JWT，数据按 `userId` 隔离 |
| 管理员 | 需要 `admin` 角色（`@PreAuthorize("hasRole('admin')")`） |

---

## 2. 公开端点速览

以下接口无需认证即可访问：

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/health` | 简单探活 |
| GET | `/api/public/notes` | 公开笔记列表 |
| GET | `/api/public/notes/{id}` | 公开笔记详情 |
| GET | `/api/public/notes/{id}/structure` | 公开笔记结构（大纲/思维导图） |
| GET | `/api/auth/oauth/{provider}/authorize` | OAuth 授权重定向 |
| GET | `/api/auth/oauth/{provider}/callback` | OAuth 回调 |
| GET | `/login/oauth2/code/{provider}` | Spring Security 兼容 OAuth 回调 |

---

## 3. 认证模块（Auth）

基础路径：`/api/auth`

### 3.1 账号密码登录

- **HTTP**：`POST /api/auth/login`
- **权限**：公开
- **请求体**：
  ```json
  {
    "username": "alice",
    "password": "password123"
  }
  ```
- **响应**：`Set-Cookie: ZHIXU_SESSION=<jwt>; Path=/; HttpOnly; SameSite=Lax`（响应体不再含 token）
  ```json
  {
    "code": 0,
    "message": "ok",
    "data": {
      "userId": 1,
      "username": "alice",
      "roles": ["user"]
    }
  }
  ```

### 3.2 账号密码注册

- **HTTP**：`POST /api/auth/register`
- **权限**：公开
- **说明**：账号不存在时自动注册；携带 `adminBootstrapKey` 可注册为管理员（首个管理员引导）。
- **请求体**：
  ```json
  {
    "username": "alice",
    "password": "password123",
    "email": "alice@example.com",
    "adminBootstrapKey": "optional-bootstrap-key"
  }
  ```
- **响应**：
  ```json
  {
    "code": 0,
    "message": "注册成功",
    "data": null
  }
  ```

### 3.3 统一登录入口

- **HTTP**：`POST /api/auth/login/unified`
- **权限**：公开
- **说明**：通过 `method` 字段路由到具体适配器（password / email_code / sms_code 等）。
- **请求体**：
  ```json
  {
    "method": "password",
    "username": "alice",
    "password": "password123"
  }
  ```
- **响应**：同 `/api/auth/login`

### 3.4 邮箱验证码

#### 发送邮箱验证码

- **HTTP**：`POST /api/auth/email-code/send`
- **权限**：公开
- **请求体**：
  ```json
  {
    "email": "alice@example.com"
  }
  ```

#### 邮箱验证码登录

- **HTTP**：`POST /api/auth/email-code/login`
- **权限**：公开
- **请求体**：
  ```json
  {
    "email": "alice@example.com",
    "code": "123456"
  }
  ```
- **响应**：同 `/api/auth/login`

### 3.5 短信验证码

#### 发送短信验证码

- **HTTP**：`POST /api/auth/sms-code/send`
- **权限**：公开
- **请求体**：
  ```json
  {
    "phone": "13800138000"
  }
  ```

#### 短信验证码登录

- **HTTP**：`POST /api/auth/sms-code/login`
- **权限**：公开
- **请求体**：
  ```json
  {
    "phone": "13800138000",
    "code": "123456"
  }
  ```
- **响应**：同 `/api/auth/login`

### 3.6 OAuth 登录

#### 获取授权 URL

- **HTTP**：`GET /api/auth/oauth/{provider}/authorize`
- **权限**：公开
- **说明**：`provider` 支持 github / google / qq；后端重定向到 OAuth 服务商。

#### 后端回调

- **HTTP**：`GET /api/auth/oauth/{provider}/callback?code=...&state=...`
- **权限**：公开
- **说明**：完成后重定向回前端 `OAUTH_FRONTEND_CALLBACK`。

#### 兼容 Spring Security 回调

- **HTTP**：`GET /login/oauth2/code/{provider}?code=...&state=...`
- **权限**：公开

#### 回调直接登录（无 code 中转）

- Cookie 会话模式下，第三方回调的 302 响应直接携带 `Set-Cookie`，前端回调页无需再用 code 换 token；
  旧 `POST /api/auth/oauth/exchange` 接口已删除（JWT 不再经过 URL）。

### 3.7 登出

- **HTTP**：`POST /api/auth/logout`
- **权限**：登录用户（Cookie 自动携带，无需手动传 token）
- **说明**：后端将当前会话 Token 加入撤销列表（Redis + 本地缓存双写），并下发清除 Cookie。非浏览器调用仍可传 `Authorization: Bearer <token>` 指定撤销对象。
- **响应**：
  ```json
  {
    "code": 0,
    "message": "已退出",
    "data": null
  }
  ```

### 3.8 当前用户信息

- **HTTP**：`GET /api/auth/info`
- **HTTP**：`GET /api/auth/me`
- **权限**：登录用户
- **说明**：两个端点等价，返回当前登录用户基本信息。
- **响应**：
  ```json
  {
    "code": 0,
    "data": {
      "userId": 1,
      "username": "alice",
      "roles": ["user"]
    }
  }
  ```

---

## 4. 用户模块（User）

基础路径：`/api/user`

### 4.1 获取个人资料

- **HTTP**：`GET /api/user/profile`
- **权限**：登录用户
- **响应**：
  ```json
  {
    "code": 0,
    "data": {
      "userId": 1,
      "username": "alice",
      "email": "alice@example.com",
      "createdAt": "2024-01-01T12:00:00"
    }
  }
  ```

### 4.2 修改密码

- **HTTP**：`POST /api/user/password`
- **权限**：登录用户
- **请求体**：
  ```json
  {
    "oldPassword": "oldPass",
    "newPassword": "newPass123"
  }
  ```

### 4.3 绑定邮箱

#### 发送绑定验证码

- **HTTP**：`POST /api/user/bind/email/send`
- **权限**：登录用户
- **请求体**：
  ```json
  {
    "email": "alice@example.com"
  }
  ```

#### 确认绑定

- **HTTP**：`POST /api/user/bind/email`
- **权限**：登录用户
- **请求体**：
  ```json
  {
    "email": "alice@example.com",
    "code": "123456"
  }
  ```

---

## 5. 笔记模块（Notes）

基础路径：`/api/notes`

### 5.1 笔记列表

- **HTTP**：`GET /api/notes?page=1&size=10&categoryId=1`
- **权限**：登录用户
- **响应**：`Page<Note>` 分页对象

### 5.2 搜索笔记

- **HTTP**：`GET /api/notes/search?page=1&size=10&keyword=Spring&categoryId=1`
- **权限**：登录用户
- **说明**：基于 MySQL 全文索引与 n-gram 解析器。

### 5.3 笔记统计

- **HTTP**：`GET /api/notes/stats`
- **权限**：登录用户
- **响应**：
  ```json
  {
    "code": 0,
    "data": {
      "total": 42,
      "categoryCount": 5
    }
  }
  ```

### 5.4 笔记详情

- **HTTP**：`GET /api/notes/{id}`
- **权限**：登录用户
- **响应**：`NoteResponse`

### 5.5 创建笔记

- **HTTP**：`POST /api/notes`
- **权限**：登录用户
- **请求体**：
  ```json
  {
    "title": "Spring Boot 学习笔记",
    "content": "...",
    "summary": "Spring Boot 核心概念",
    "keywords": "Spring Boot,Java",
    "coverImage": "https://...",
    "categoryId": 1,
    "status": 1,
    "outline": []
  }
  ```
- **响应**：`Note` 实体

### 5.6 更新笔记

- **HTTP**：`PUT /api/notes/{id}`
- **权限**：登录用户
- **请求体**：同创建
- **响应**：`Note` 实体

### 5.7 删除笔记（软删除）

- **HTTP**：`DELETE /api/notes/{id}`
- **权限**：登录用户
- **响应**：`null`

### 5.8 OCR 识别

- **HTTP**：`POST /api/notes/{id}/ocr?engine=auto&fileId=123`
- **权限**：登录用户
- **说明**：对笔记关联的图片触发 OCR；支持 `auto` / `paddle` / `deepseek`。
- **请求体（可选）**：
  ```json
  {
    "engine": "auto",
    "fileIds": [123, 124]
  }
  ```
- **响应**：识别出的文本字符串

### 5.9 AI 整理

#### 提交 AI 整理

- **HTTP**：`POST /api/notes/{id}/ai-analysis`
- **权限**：登录用户
- **说明**：异步任务，提交后立即返回。
- **响应**：
  ```json
  {
    "code": 0,
    "message": "AI 整理已提交，可在页面查看进度",
    "data": {
      "submitted": true
    }
  }
  ```

#### 查询 AI 整理状态

- **HTTP**：`GET /api/notes/{id}/ai-analysis/status`
- **权限**：登录用户
- **响应**：
  ```json
  {
    "code": 0,
    "data": {
      "running": true,
      "stage": "SUMMARIZING",
      "error": null,
      "startedAt": 1710000000000,
      "finishedAt": 0
    }
  }
  ```

#### 查看全部 AI 整理任务

- **HTTP**：`GET /api/notes/ai-analysis/tasks`
- **权限**：登录用户

#### 删除 AI 整理任务

- **HTTP**：`DELETE /api/notes/ai-analysis/tasks/{noteId}`
- **权限**：登录用户

### 5.10 AI 清洗

#### 提交 AI 清洗

- **HTTP**：`POST /api/notes/{id}/normalize`
- **权限**：登录用户
- **说明**：异步任务，对笔记内容进行规范化清洗。
- **响应**：
  ```json
  {
    "code": 0,
    "data": { "submitted": true }
  }
  ```

#### 查询 AI 清洗状态

- **HTTP**：`GET /api/notes/{id}/normalize/status`
- **权限**：登录用户
- **响应**：同 AI 整理状态

### 5.11 笔记结构（大纲 / 思维导图）

#### 获取结构

- **HTTP**：`GET /api/notes/{id}/structure`
- **权限**：登录用户
- **响应**：`NoteStructureResponse`

#### 生成结构

- **HTTP**：`POST /api/notes/{id}/structure/generate`
- **权限**：登录用户
- **说明**：AI 自动生成多级大纲与思维导图数据，并记录历史快照。
- **响应**：`NoteStructureResponse`

#### 更新结构

- **HTTP**：`PUT /api/notes/{id}/structure`
- **权限**：登录用户
- **请求体**：
  ```json
  {
    "outline": [
      { "id": "1", "title": "第一章", "level": 0, "children": [] }
    ]
  }
  ```
- **响应**：`NoteStructureResponse`

### 5.12 历史版本

#### 查看历史

- **HTTP**：`GET /api/notes/{id}/history`
- **权限**：登录用户
- **响应**：`List<NoteHistoryItem>`

#### 恢复历史版本

- **HTTP**：`POST /api/notes/{id}/history/{historyId}/restore`
- **权限**：登录用户

---

## 6. 文件模块（Files）

基础路径：`/api/files`

### 6.1 单文件上传

- **HTTP**：`POST /api/files/upload`
- **权限**：登录用户
- **Content-Type**：`multipart/form-data`
- **参数**：
  - `file`：文件（图片 / txt / md / pdf / docx）
  - `noteId`（可选）：关联笔记 ID
  - `normalize`（可选，默认 `true`）：是否自动清洗
- **说明**：仅保存文件并创建异步文档处理任务（解析 / 清洗），前端按 `taskId` 轮询进度。
- **响应**：
  ```json
  {
    "code": 0,
    "data": {
      "file": { "id": 1, "originalName": "doc.pdf", "mimeType": "application/pdf" },
      "taskId": 100,
      "taskStatus": "PENDING"
    }
  }
  ```

### 6.2 分片上传

- **HTTP**：`POST /api/files/upload-chunk`
- **权限**：登录用户
- **Content-Type**：`multipart/form-data`
- **参数**：
  - `file`：当前分片
  - `identifier`：文件唯一标识
  - `chunkIndex`：当前片索引
  - `totalChunks`：总分片数

### 6.3 分片合并

- **HTTP**：`POST /api/files/upload-chunk/merge`
- **权限**：登录用户
- **参数**：
  - `identifier`
  - `fileName`
  - `totalChunks`
  - `noteId`（可选）
  - `normalize`（可选，默认 `true`）
- **响应**：同单文件上传，额外返回 `extractedText` / `normalizedText`

### 6.4 下载/预览文件内容

- **HTTP**：`GET /api/files/{id}/content`
- **权限**：登录用户
- **说明**：直接返回文件字节流，`Content-Disposition: inline`。

### 6.5 删除文件

- **HTTP**：`DELETE /api/files/{id}`
- **权限**：登录用户

### 6.6 文档处理任务中心

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/files/tasks/{taskId}` | 查询任务详情（阶段 / 进度 / 块级统计 / 耗时） |
| GET | `/api/files/tasks/note/{noteId}` | 按笔记查询最新任务 |
| GET | `/api/files/tasks/active` | 进行中任务列表 |
| GET | `/api/files/tasks/recent` | 最近任务（含完成/失败） |
| POST | `/api/files/tasks/{taskId}/retry` | 重试失败任务 |
| DELETE | `/api/files/tasks/{taskId}` | 删除任务日志 |
| DELETE | `/api/files/tasks` | 清空当前用户全部任务日志 |

---

## 7. 分类模块（Categories）

基础路径：`/api/categories`

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/categories` | 登录用户 | 全部分类列表 |
| GET | `/api/categories/page?page=1&size=10&keyword=...` | 登录用户 | 分类分页 |
| POST | `/api/categories` | 登录用户 | 创建分类 |
| PUT | `/api/categories/{id}` | 登录用户 | 更新分类 |
| DELETE | `/api/categories/{id}` | 登录用户 | 删除分类 |

---
## 8. 知识问答模块（Ask）

基础路径：`/api/v1/ask`

### 8.1 同步问答

- **HTTP**：`POST /api/v1/ask`
- **权限**：登录用户
- **说明**：基于当前用户笔记库进行 RAG 检索后生成回答。
- **请求体**：
  ```json
  {
    "question": "什么是 Spring Boot 自动配置？",
    "conversationId": "uuid-optional"
  }
  ```
- **响应**：`AskRecord`

### 8.2 SSE 流式问答

- **HTTP**：`POST /api/v1/ask/stream`
- **权限**：登录用户
- **Content-Type**：`text/event-stream`
- **请求体**：同同步问答
- **响应示例**：
  ```text
  data: {"type":"chunk","content":"Spring Boot"}

  data: {"type":"chunk","content":"自动配置"}

  data: {"type":"done"}

  ```

### 8.3 问答历史

- **HTTP**：`GET /api/v1/ask?page=1&size=20`
- **权限**：登录用户
- **响应**：`List<AskRecord>`

### 8.4 问答详情

- **HTTP**：`GET /api/v1/ask/{id}`
- **权限**：登录用户

### 8.5 删除问答记录

- **HTTP**：`DELETE /api/v1/ask/{id}`
- **权限**：登录用户

### 8.6 导出问答

- **HTTP**：`GET /api/v1/ask/{id}/export`
- **权限**：登录用户
- **响应**：
  ```json
  {
    "code": 0,
    "data": {
      "fileName": "问答-20240101.md",
      "content": "# Q: ...\\n\\nA: ..."
    }
  }
  ```

---

## 9. 知识图谱模块（Graph）

### 9.1 单笔记图谱

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/notes/{id}/graph/build` | 登录用户 | 提交单笔记图谱构建任务（AI 实体关系抽取 → Neo4j） |
| GET | `/api/notes/{id}/graph` | 登录用户 | 获取单笔记图谱可视化数据 |
| DELETE | `/api/notes/{id}/graph` | 登录用户 | 删除单笔记图谱 |

### 9.2 分类图谱

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/graph/category/{categoryId}/build` | 登录用户 | 构建分类图谱 |
| GET | `/api/graph/category/{categoryId}` | 登录用户 | 获取分类图谱 |

### 9.3 全局图谱

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| POST | `/api/graph/global/build` | 登录用户 | 构建全局知识体系图谱 |
| GET | `/api/graph/global` | 登录用户 | 获取全局图谱 |

### 9.4 全局实体检索

- **HTTP**：`GET /api/graph/search?keyword=Spring`
- **权限**：登录用户
- **响应**：`List<GraphNode>`

### 9.5 图谱任务中心

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/graph/tasks` | 登录用户 | 活跃任务与最近任务 |
| GET | `/api/graph/tasks/{taskId}` | 登录用户 | 任务状态 |
| DELETE | `/api/graph/tasks/{taskId}` | 登录用户 | 删除任务记录 |

### 9.6 管理端图谱总览

- **HTTP**：`GET /api/v1/admin/graph/overview`
- **权限**：管理员
- **响应**：`GraphOverview`

---

## 10. AI 配置模块（AI Config）

基础路径：`/api/v1/ai/config`

### 10.1 获取用户 AI 配置

- **HTTP**：`GET /api/v1/ai/config`
- **权限**：登录用户
- **响应**：`AiUserConfig`

### 10.2 保存用户 AI 配置

- **HTTP**：`PUT /api/v1/ai/config`
- **权限**：登录用户
- **说明**：用户自定义 API Key / Base URL / Model，加密存储，覆盖平台默认模型。
- **请求体**：
  ```json
  {
    "baseUrl": "https://api.deepseek.com",
    "apiKey": "sk-xxx",
    "model": "deepseek-chat"
  }
  ```

### 10.3 清除用户 AI 配置

- **HTTP**：`DELETE /api/v1/ai/config`
- **权限**：登录用户
- **说明**：回退到平台默认模型。

### 10.4 测试连接

- **HTTP**：`POST /api/v1/ai/config/test`
- **权限**：登录用户
- **请求体**：
  ```json
  {
    "baseUrl": "https://api.deepseek.com",
    "apiKey": "sk-xxx",
    "model": "deepseek-chat"
  }
  ```
- **响应**：连接测试结果

---

## 11. 管理端模块（Admin）

### 11.1 系统总览

- **HTTP**：`GET /api/v1/admin/system/overview?alertLimit=20`
- **权限**：管理员
- **响应**：
  ```json
  {
    "code": 0,
    "data": {
      "serverTime": "2024-01-01T12:00:00",
      "status": "UP",
      "aiEngineType": "api",
      "aiEngineConfiguredType": "api",
      "aiEngineOverrideEnabled": false,
      "aiEngineOverrideType": null,
      "aiHealthy": true,
      "monitorEnabled": true,
      "rateLimitPerMinute": 120,
      "recentAlertCount": 0,
      "recentAlerts": []
    }
  }
  ```

### 11.2 AI 引擎状态

- **HTTP**：`GET /api/v1/admin/system/ai-engine`
- **权限**：管理员

### 11.3 切换 AI 引擎

- **HTTP**：`POST /api/v1/admin/system/ai-engine`
- **权限**：管理员
- **请求体**：
  ```json
  {
    "engineType": "api"
  }
  ```

### 11.4 重置 AI 引擎

- **HTTP**：`POST /api/v1/admin/system/ai-engine/reset`
- **权限**：管理员
- **说明**：清除运行时覆盖，恢复配置文件设置。

### 11.5 发送测试告警

- **HTTP**：`POST /api/v1/admin/system/emit-test-alert`
- **权限**：管理员

### 11.6 用户治理

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/admin/users` | 用户列表 |
| POST | `/api/v1/admin/users/{userId}/role` | 变更用户角色，Body `{ "role": "admin" \| "user" }` |

### 11.7 AI 端点管理

基础路径：`/api/v1/admin/ai/endpoints`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/admin/ai/endpoints` | 端点列表 |
| POST | `/api/v1/admin/ai/endpoints` | 创建端点 |
| PUT | `/api/v1/admin/ai/endpoints/{id}` | 更新端点 |
| DELETE | `/api/v1/admin/ai/endpoints/{id}` | 删除端点 |
| POST | `/api/v1/admin/ai/endpoints/{id}/toggle?enabled=true` | 启用/停用端点 |
| POST | `/api/v1/admin/ai/endpoints/{id}/test` | 测试端点连通性 |

### 11.8 告警管理

基础路径：`/api/v1/alerts`

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/v1/alerts/recent?limit=20` | 最近告警 |
| POST | `/api/v1/alerts/emit-test` | 发送测试告警 |

---

## 12. 健康检查模块（Health）

### 12.1 简单探活

- **HTTP**：`GET /api/health`
- **权限**：公开
- **响应**：
  ```json
  {
    "code": 0,
    "data": "ok"
  }
  ```

### 12.2 详细健康状态

- **HTTP**：`GET /api/v1/health`
- **权限**：公开
- **响应**：
  ```json
  {
    "code": 0,
    "data": {
      "status": "UP",
      "serverTime": "2024-01-01T12:00:00",
      "aiEngineType": "api",
      "aiEngineConfiguredType": "api",
      "aiEngineOverrideEnabled": false,
      "aiEngineOverrideType": null,
      "aiHealthy": true,
      "platformDefaultApi": true,
      "recentAlertCount": 0
    }
  }
  ```

---

## 13. 公开分享模块（Public Notes）

基础路径：`/api/public/notes`

### 13.1 公开笔记列表

- **HTTP**：`GET /api/public/notes?page=1&size=10&keyword=...`
- **权限**：公开
- **响应**：`Page<PublicNoteSummary>`

### 13.2 公开笔记详情

- **HTTP**：`GET /api/public/notes/{id}`
- **权限**：公开
- **响应**：`PublicNoteDetailResponse`

### 13.3 公开笔记结构

- **HTTP**：`GET /api/public/notes/{id}/structure`
- **权限**：公开
- **响应**：`NoteStructureResponse`

---

## 14. 错误码速查

| Code | 含义 |
|---|---|
| 0 | 成功 |
| 400 | 请求参数错误 / 业务校验失败 |
| 401 | 未认证或 Token 已撤销 |
| 403 | 无权限（如非管理员访问 admin 接口） |
| 404 | 资源不存在或无权访问 |
| 429 | 接口限流 |
| 500 | 服务端内部错误 |

---

## 15. 附录：端点总表

| 分组 | 端点数量 | 基础路径 |
|---|---|---|
| Auth | 13 | `/api/auth` |
| User | 4 | `/api/user` |
| Admin | 14 | `/api/v1/admin/*`, `/api/v1/alerts` |
| Notes | 18 | `/api/notes` |
| Files | 10 | `/api/files` |
| Categories | 5 | `/api/categories` |
| Ask | 5 | `/api/v1/ask` |
| Graph | 9 | `/api/notes/{id}/graph`, `/api/graph` |
| Health | 2 | `/api/health`, `/api/v1/health` |
| Public | 3 | `/api/public/notes` |
| AI Config | 4 | `/api/v1/ai/config` |
| OAuth 兼容 | 1 | `/login/oauth2/code/{provider}` |
