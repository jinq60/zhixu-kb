# 知序智能知识库 - 项目规格说明书

> **免责声明**：本文档由代码库自动生成，基于 README、docker-compose、环境变量模板及源码结构。配置与实现细节可能随版本演进发生变化，部署前请以实际代码与配置文件为准。

---

## 1. 项目概述与定位

**知序智能知识库（Zhixu Knowledge Base）** 是一款面向个人用户的在线知识体系整理平台，核心定位是：

> 打通 **采集 → 识别 → 整理 → 图谱 → 问答** 五大环节，将照片、文档、碎片信息等零散资料沉淀为可检索、可关联、可对话的个人知识体系。

项目由两个独立项目（学习笔记整理系统 + RAG 项目）合并重构而来，统一后端（Spring Boot + MyBatis-Plus）与统一前端（Vue3 + TypeScript + Element Plus），并补全了 Neo4j 知识图谱能力。

### 1.1 目标用户

- 学生、研究人员：整理课程笔记、论文资料、阅读摘录。
- 职场人士：沉淀项目文档、会议纪要、技术碎片。
- 知识工作者：构建可检索、可问答的个人知识库。

### 1.2 核心价值

| 能力 | 价值 |
|---|---|
| 多源采集 | 图片、文档（txt/md/pdf/docx）统一上传与管理 |
| 智能识别 | OCR 多引擎自动降级，文档文本自动提取 |
| AI 整理 | 摘要、关键词、分类、多级大纲、思维导图自动生成 |
| 知识图谱 | 实体关系抽取 + Neo4j 存储 + 力导图可视化 |
| 知识问答 | 基于个人笔记的 RAG 问答，支持 SSE 流式回答与引用溯源 |
| 公开分享 | 笔记发布到公开大厅，支持分享阅读 |
| 管理治理 | 系统总览、AI 引擎热切换、用户角色管理、告警监控 |

---

## 2. 系统架构

### 2.1 整体架构图

```text
┌─────────────────────────────────────────────────────────────────────┐
│                           客户端层                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────┐  │
│  │  用户端 Web   │  │  管理后台 Web │  │  OAuth 第三方登录 (GitHub  │  │
│  │  localhost:5173│  │  localhost:5175│  │  / Google / QQ)           │  │
│  └──────┬───────┘  └──────┬───────┘  └────────────┬─────────────┘  │
└─────────┼─────────────────┼───────────────────────┼────────────────┘
          │                 │                       │
          └─────────────────┼───────────────────────┘
                            │ HTTP / SSE / OAuth2
┌───────────────────────────▼─────────────────────────────────────────┐
│                          网关/代理层                                 │
│                    Nginx (Docker 中前端容器内置)                      │
│                 /api -> backend:8080 /actuator -> backend:8080      │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────────┐
│                          应用服务层                                  │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    zhixu-backend (Spring Boot)               │   │
│  │  system │ note │ ask │ graph │ ai │ security │ admin │ common │   │
│  └─────────────────────────────────────────────────────────────┘   │
│  ┌────────────────────────┐  ┌──────────────────────────────────┐  │
│  │  zhixu-ocr (Flask)     │  │  zhixu-pdf (xberg PDF 解析服务)   │  │
│  │  RapidOCR / PaddleOCR  │  │  PDF/Office 文档文本提取          │  │
│  └────────────────────────┘  └──────────────────────────────────┘  │
└───────────────────────────┬─────────────────────────────────────────┘
                            │
┌───────────────────────────▼─────────────────────────────────────────┐
│                          数据存储层                                  │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────────────┐ │
│  │  MySQL 8 │ │  Redis 7 │ │  Neo4j 4 │ │  Milvus (语义检索/RAG)  │ │
│  │ 业务数据  │ │ 缓存/限流 │ │ 知识图谱  │ │ 向量数据库              │ │
│  └──────────┘ └──────────┘ └──────────┘ └────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 后端模块划分

| 模块 | 包路径 | 职责 |
|---|---|---|
| `system` | `com.zhixu.kb.system` | 认证/用户/角色、健康检查、操作日志、OAuth、用户治理 |
| `note` | `com.zhixu.kb.note` | 笔记、分类、文件、OCR 客户端、AI 整理、大纲、历史、公开分享 |
| `ask` | `com.zhixu.kb.ask` | 知识问答（RAG）、引用溯源、SSE 流式、导出 |
| `graph` | `com.zhixu.kb.graph` | 知识图谱构建、Neo4j 读写、可视化数据、任务管理 |
| `ai` | `com.zhixu.kb.ai` | 云端 AI 引擎适配器、多厂商端点、用户 AI 配置 |
| `security` | `com.zhixu.kb.security` | JWT 过滤器、Token 撤销、限流、请求追踪、加密、脱敏 |
| `admin` | `com.zhixu.kb.admin` | 系统总览、AI 引擎切换、告警 |
| `common` | `com.zhixu.kb.common` | 通用结果封装、异常处理、工具类、告警服务 |

### 2.3 部署形态

- **本地开发**：分别启动 OCR / 后端 / 前端 / 管理后台 / Neo4j（可选）。
- **Docker 全栈**：`docker compose up -d --build` 一键拉起 MySQL / Redis / Neo4j / Milvus / OCR / PDF / 后端 / 前端 / 管理后台。

---

## 3. 核心功能与用户场景

### 3.1 功能矩阵

| 环节 | 功能 | 用户场景 |
|---|---|---|
| 采集 | 图片上传、文档上传（txt/md/pdf/docx）、分片上传大文件 | 手机拍摄笔记照片、上传课程 PPT |
| 识别 | OCR 多引擎识别、文档文本提取 | 将手写/印刷照片转为可编辑文本 |
| 整理 | AI 摘要、关键词、分类、多级大纲、思维导图 | 自动生成笔记概要与知识结构 |
| 图谱 | 实体关系抽取、Neo4j 存储、力导图可视化 | 发现知识点之间的关联 |
| 问答 | 基于个人笔记的 RAG 问答、SSE 流式、引用溯源 | 向知识库提问，获得带来源的回答 |
| 分享 | 笔记公开发布、公开发现大厅 | 将整理好的笔记分享给他人 |
| 治理 | 系统监控、AI 引擎管理、用户角色管理 | 管理员维护平台运行 |

### 3.2 关键用户旅程

```text
1. 注册/登录 -> 创建分类 -> 上传文件/创建笔记
2. 触发 OCR / 文档解析 -> 文本进入笔记
3. 触发 AI 整理 -> 生成摘要、关键词、大纲
4. 触发知识图谱构建 -> 查看实体关系网络
5. 在问答页提问 -> 系统检索个人笔记 -> 生成答案
6. 将优质笔记发布到公开大厅 -> 他人浏览
```

---

## 4. 技术栈

### 4.1 后端

| 技术 | 版本/说明 | 用途 |
|---|---|---|
| Spring Boot | 2.7.18（Java 8） | 基础框架 |
| Spring Security | 5.7.12 | 认证授权 |
| Spring Validation | - | 参数校验 |
| Spring Mail | - | 邮箱验证码 |
| MyBatis-Plus | 3.5.5 | ORM / 分页 |
| MySQL Connector/J | 8.x | 数据库驱动 |
| Redis | 7.x（可选，降级本地缓存） | Token 撤销 / 限流 / 缓存 |
| Neo4j Java Driver | 4.4.15 | 知识图谱 |
| Milvus SDK Java | 2.4.10 | 向量数据库（RAG 语义检索） |
| JJWT | 0.11.5 | JWT 生成与校验 |
| Hutool | 5.8.25 | 工具类 |
| Caffeine | 2.9.3 | 本地缓存 |
| Apache POI | 5.2.5 | docx 处理 |
| Apache PDFBox | 2.0.31 | PDF 文本提取 |
| OWASP Java HTML Sanitizer | 20240325.1 | 公开笔记 XSS 清洗 |
| Springdoc OpenAPI | 1.7.0 | Swagger UI |
| jqwik | 1.7.4 | 属性测试 |

### 4.2 前端

| 技术 | 版本/说明 | 用途 |
|---|---|---|
| Vue | 3.3.13 | 框架 |
| Vue Router | 4.2.5 | 路由 |
| Pinia | 2.1.7 + persistedstate | 状态管理 |
| TypeScript | 5.3.3 | 类型安全 |
| Element Plus | 2.4.4 | UI 组件库 |
| Vite | 5.0.10 | 构建工具 |
| Axios | 1.6.2 | HTTP 客户端 |
| ECharts | 5.5.0 | 图表 / 知识图谱可视化 |
| Mermaid | 10.9.1 | 思维导图 |
| WangEditor | 5.1.23 | 富文本编辑 |
| DOMPurify | 3.1.6 | XSS 清洗 |
| Vitest | 1.1.0 | 单元测试 |

### 4.3 管理后台

与前端栈一致，额外强调深色布局、登录制、管理员角色鉴权。

### 4.4 OCR 服务

| 技术 | 说明 |
|---|---|
| Python 3.10+ | 运行环境 |
| Flask | Web 框架 |
| RapidOCR | 纯 CPU ONNX 推理，容器默认 |
| PaddleOCR | 本地推荐，更高精度 |
| DeepSeek-OCR-2 | 云端 OCR，大模型视觉能力 |
| Pillow | 图像处理、透视矫正 |

### 4.5 PDF 解析服务

| 技术 | 说明 |
|---|---|
| xberg (ghcr.io/xberg-io/xberg:1.0.14) | 容器化 PDF/Office 文档解析 |
| 200MB 请求体上限 | 支持大文档 |

### 4.6 部署与运维

| 技术 | 说明 |
|---|---|
| Docker & Docker Compose | 容器编排 |
| Nginx | 前端静态托管 + API 反代 |
| PowerShell 脚本 | Windows 本地一键初始化/启动/备份 |
| Windows 计划任务 | MySQL 自动备份 |

---

## 5. 数据流：上传 → 清洗 → 分块 → 嵌入 → 问答

```text
┌──────────┐    ┌──────────────┐    ┌─────────────────┐
│ 用户上传  │ -> │ 文件服务 store │ -> │ 创建文档处理任务  │
│ 图片/文档 │    │ 保存到本地/容器 │    │ (异步 PENDING)   │
└──────────┘    └──────────────┘    └────────┬────────┘
                                             │
                    ┌────────────────────────┼────────────────────────┐
                    │                        │                        │
                    ▼                        ▼                        ▼
            ┌─────────────┐          ┌──────────────┐          ┌─────────────┐
            │ 图片 -> OCR  │          │ PDF/Office    │          │ txt/md 直接 │
            │ 服务识别文本 │          │ -> xberg 提取 │          │ 读取文本     │
            └──────┬──────┘          └──────┬───────┘          └──────┬──────┘
                   │                        │                         │
                   └────────────────────────┼─────────────────────────┘
                                            ▼
                                   ┌─────────────────┐
                                   │ 文本清洗/规范化  │
                                   │ (规则 + AI 可选) │
                                   └────────┬────────┘
                                            ▼
                                   ┌─────────────────┐
                                   │  文本分块(chunk) │
                                   │  按段落/语义切分  │
                                   └────────┬────────┘
                                            ▼
                                   ┌─────────────────┐
                                   │  Embedding 向量 │
                                   │  写入 Milvus     │
                                   └────────┬────────┘
                                            ▼
                                   ┌─────────────────┐
                                   │  RAG 问答时检索  │
                                   │  MySQL + Milvus  │
                                   └────────┬────────┘
                                            ▼
                                   ┌─────────────────┐
                                   │  LLM 生成回答   │
                                   │  SSE 流式返回   │
                                   └─────────────────┘
```

### 5.1 关键说明

- **上传接口不阻塞**：仅保存文件并创建异步任务，前端按 `taskId` 轮询进度。
- **清洗为确定性处理**：去标记保留标题层级，毫秒级完成。
- **AI 整理异步执行**：提交即返回，云端约 10 秒完成。
- **知识检索双路**：MySQL 全文检索（n-gram）+ Milvus 向量检索。
- **回答带引用溯源**：返回引用的笔记片段，便于用户核对。

---
## 6. 安全设计

### 6.1 认证与授权

- **JWT 认证**：登录后签发 JWT 并写入 HttpOnly Cookie（`ZHIXU_SESSION`，SameSite=Lax），浏览器自动携带，响应体不返回 token；非浏览器 API 调用仍可用 `Authorization: Bearer` 头。
- **Token 撤销**：登出时将 Token 加入撤销列表，Redis + 本地 Caffeine 双写，并清除 Cookie。
- **角色控制**：管理员接口使用 `@PreAuthorize("hasRole('admin')")`。
- **注册限流**：单 IP 每日自动注册次数上限（默认 10）。
- **验证码错误限流**：防止暴力破解。

### 6.2 数据安全

- **敏感数据加密**：AES-GCM 加密用户 API Key 等敏感字段。
- **日志脱敏**：JWT、密码、API Key 等不在日志中明文打印。
- **请求追踪**：traceId 贯穿请求链路，便于审计。

### 6.3 接口安全

- **接口限流**：用户/IP 维度，默认每分钟 120 次。
- **SSRF 防护**：`SafeUrlValidator` 限制内网/保留地址访问；Docker 开发环境默认放行 fake-ip 范围。
- **CORS 白名单**：通过 `CORS_ALLOWED_ORIGINS` 配置可信来源。
- **存储型 XSS 防护**：公开笔记 HTML 使用 OWASP Java HTML Sanitizer 白名单清洗。

### 6.4 OAuth 安全

- **state 参数**：防 CSRF。
- **一次性 code**：回调 URL 中的一次性 code 需调用 `/api/auth/oauth/exchange` 换取 JWT。

### 6.5 生产安全清单

- 公网必须启用 HTTPS。
- `JWT_SECRET` / `CRYPTO_AES_KEY` 缺失或过短时后端拒绝启动。
- 首次部署后立即修改管理员密码。
- MySQL/Redis/Neo4j 默认仅绑定 `127.0.0.1`。
- AI Key 泄露后需在厂商控制台立即轮换。

---

## 7. 部署指南

### 7.1 环境要求

- JDK 8、Maven 3.6+
- Node.js ^20.19.0 或 >=22.12.0
- Python 3.10+
- MySQL 8.0
- Redis 7+（可选，未启动自动降级）
- Neo4j 4.4+（可选，`neo4j.enabled=false` 关闭）
- Docker & Docker Compose（推荐）

### 7.2 本地开发启动

```powershell
# 1. 配置环境变量
Copy-Item .env.example .env.local
Copy-Item .env.secrets.example .env.secrets.local
# 编辑 .env.secrets.local，填入 JWT_SECRET / CRYPTO_AES_KEY / AI_API_KEY

# 2. 初始化数据库
powershell -ExecutionPolicy Bypass -File .\scripts\init-mysql-dev.ps1

# 3. 分别启动服务
powershell -ExecutionPolicy Bypass -File .\scripts\start-ocr-local.ps1          # :5001
powershell -ExecutionPolicy Bypass -File .\scripts\start-backend-local.ps1       # :8080
powershell -ExecutionPolicy Bypass -File .\scripts\start-frontend-local.ps1      # :5173
powershell -ExecutionPolicy Bypass -File .\scripts\start-admin-frontend-local.ps1 # :5175

# 4. 可选启动 Neo4j
docker run -d --name neo4j -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/zhixu123456 neo4j:4.4-community
```

### 7.3 完整 Docker Compose 部署

```bash
# 1. 在仓库根目录创建 .env（参考 .env.example / .env.secrets.example）
# AI_API_KEY=sk-xxx
# JWT_SECRET=...
# CRYPTO_AES_KEY=...
# ADMIN_BOOTSTRAP_KEY=...

# 2. 一键构建并启动（含全部中间件）
docker compose up -d --build

# 3. 查看状态
docker compose ps
```

#### 7.3.1 完整版服务清单

| 服务 | 容器名 | 端口 | 说明 |
|---|---|---|---|
| 用户端 | zhixu-frontend | 5173 | Nginx 托管 |
| 管理后台 | zhixu-admin-frontend | 5175 | 独立后台 |
| 后端 | zhixu-backend | 8080 | Spring Boot |
| OCR | zhixu-ocr | 5001 | RapidOCR（容器默认） |
| PDF 解析 | zhixu-pdf | 5002 | xberg |
| MySQL | zhixu-mysql | 3306 | 自动执行 schema |
| Redis | zhixu-redis | 6379 | 缓存/限流 |
| Neo4j | zhixu-neo4j | 7474/7687 | 知识图谱 |
| Milvus | zhixu-milvus | 19530/9091 | 向量检索 |
| etcd | zhixu-etcd | 2379 | Milvus 依赖 |
| MinIO | zhixu-minio | 9000 | Milvus 依赖 |

### 7.4 最小化 Docker Compose（无图谱/向量检索）

若不需要知识图谱与语义检索，可注释或移除 `neo4j`、`milvus`、`etcd`、`minio`、`pdf-service` 服务，并设置：

```yaml
# backend environment
NEO4J_ENABLED: "false"
MILVUS_ENABLED: "false"
```

最小必要服务：

| 服务 | 容器名 | 端口 |
|---|---|---|
| MySQL | zhixu-mysql | 3306 |
| Redis | zhixu-redis | 6379 |
| OCR | zhixu-ocr | 5001 |
| 后端 | zhixu-backend | 8080 |
| 用户端 | zhixu-frontend | 5173 |
| 管理后台 | zhixu-admin-frontend | 5175 |

### 7.5 数据持久化与备份

- 所有中间件数据与后端上传文件使用 Docker volumes 持久化。
- `docker compose down` 不丢数据；`docker compose down -v` 会清空。
- MySQL 手动备份脚本：`scripts/backup-mysql.ps1`。
- 恢复：`docker exec -i zhixu-mysql mysql -uroot -proot zhixu_kb < backup.sql`。

---

## 8. 环境变量

### 8.1 非敏感变量（`.env.local` / `.env`）

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_HOST` | localhost | MySQL 主机 |
| `DB_PORT` | 3306 | MySQL 端口 |
| `DB_NAME` | zhixu_kb | 数据库名 |
| `DB_USERNAME` | root | 数据库用户名 |
| `DB_PASSWORD` | root | 数据库密码 |
| `REDIS_HOST` | localhost | Redis 主机 |
| `REDIS_PORT` | 6379 | Redis 端口 |
| `NEO4J_URI` | bolt://localhost:7687 | Neo4j 地址 |
| `NEO4J_USERNAME` | neo4j | Neo4j 用户名 |
| `NEO4J_PASSWORD` | zhixu123456 | Neo4j 密码 |
| `NEO4J_ENABLED` | true | 是否启用知识图谱 |
| `MILVUS_URI` | http://localhost:19530 | Milvus 地址 |
| `MILVUS_ENABLED` | true | 是否启用向量检索 |
| `MILVUS_DIMENSION` | 1536 | 向量维度 |
| `OCR_SERVICE_URL` | http://localhost:5001 | OCR 服务地址 |
| `PDF_PARSE_SERVICE_URL` | http://localhost:5002 | PDF 解析服务地址 |
| `AI_ENGINE_TYPE` | api | AI 引擎类型 |
| `AI_MODEL` | deepseek-chat | 默认模型 |
| `AI_API_BASE_URL` | https://api.deepseek.com | 默认 API 地址 |
| `SMTP_HOST` | smtp.qq.com | SMTP 服务器 |
| `SMTP_PORT` | 587 | SMTP 端口 |
| `FILE_UPLOAD_PATH` | ./uploads/images/ | 文件上传路径 |
| `CORS_ALLOWED_ORIGINS` | localhost:5173,5175 | CORS 白名单 |
| `APP_RATE_LIMIT_PER_MINUTE` | 120 | 每分钟限流 |
| `SSRF_ALLOW_FAKE_IP` | true | 开发环境放行 fake-ip |

### 8.2 敏感变量（`.env.secrets.local`）

| 变量 | 说明 |
|---|---|
| `AI_API_KEY` | 平台默认云端模型 API Key |
| `DEEPSEEK_API_KEY` | 兼容旧变量名 |
| `JWT_SECRET` | JWT 签名密钥（≥32 字节） |
| `CRYPTO_AES_KEY` | AES 加密密钥（≥32 字节） |
| `ADMIN_BOOTSTRAP_KEY` | 注册管理员引导密钥 |
| `SMTP_USERNAME` | 邮箱用户名 |
| `SMTP_PASSWORD` | 邮箱授权码 |
| `OAUTH_GITHUB_CLIENT_ID` | GitHub OAuth Client ID |
| `OAUTH_GITHUB_CLIENT_SECRET` | GitHub OAuth Secret |
| `OAUTH_GOOGLE_CLIENT_ID` | Google OAuth Client ID |
| `OAUTH_GOOGLE_CLIENT_SECRET` | Google OAuth Secret |
| `OAUTH_QQ_CLIENT_ID` | QQ OAuth Client ID |
| `OAUTH_QQ_CLIENT_SECRET` | QQ OAuth Secret |

### 8.3 多 AI 端点池

`AI_ENDPOINTS` 支持配置多组端点（JSON 格式），调用失败自动冷却 60 秒并切换，可混配多个厂商规避限速。

---

## 9. 当前状态与限制

### 9.1 已实现能力

- 完整的用户认证体系：账号密码、邮箱/短信验证码、OAuth2（GitHub/Google/QQ）。
- 笔记 CRUD、分类管理、全文搜索、历史版本恢复。
- 文件上传（含分片上传）、OCR、文档解析、AI 整理、AI 清洗。
- 笔记结构（大纲/思维导图）生成与编辑。
- 知识图谱构建（单笔记 / 分类 / 全局）与 Neo4j 存储。
- 基于个人笔记的 RAG 问答，同步与 SSE 流式两种模式。
- 公开分享大厅。
- 管理后台：系统总览、AI 引擎切换、用户治理、AI 端点管理、告警。

### 9.2 已知限制

- **Spring Boot 2.7.x EOL**：已通过依赖加固覆盖关键 CVE，但建议未来升级到 Spring Boot 3.x。
- **本地 OCR 精度**：容器内默认 RapidOCR，复杂场景推荐本地 PaddleOCR 或 DeepSeek-OCR-2。
- **Milvus 资源占用**：完整 Docker Compose 对内存要求较高（建议 ≥8GB）。
- **OCR 服务无鉴权**：仅绑定 `127.0.0.1`，不得暴露到公网。
- **AI 问答依赖外部 API**：未配置 Key 时相关功能不可用。
- **知识图谱构建耗时**：大笔记 / 全量全局图谱可能需要数秒至数十秒。

### 9.3 后续可扩展方向

- 迁移到 Spring Boot 3 + JDK 17。
- 引入更精细的 RBAC 权限模型。
- 支持更多文档格式（epub、html）。
- 多租户与团队协作空间。
- 移动端 App / 小程序。

---

## 10. 目录结构

```text
IntelligentKnowledgeBase/
├─ backend/                    # 统一后端（Spring Boot 2.7 + Java 8）
│  ├─ src/main/java/com/zhixu/kb/
│  │  ├─ admin/                # 管理端接口
│  │  ├─ ai/                   # AI 引擎适配器、用户配置、端点管理
│  │  ├─ ask/                  # 知识问答（RAG）
│  │  ├─ common/               # 通用工具、异常、结果封装、告警
│  │  ├─ config/               # 配置属性类
│  │  ├─ graph/                # 知识图谱
│  │  ├─ note/                 # 笔记、分类、文件、OCR、AI 整理
│  │  ├─ security/             # JWT、限流、加密、脱敏
│  │  └─ system/               # 认证、用户、角色、健康检查
│  ├─ src/main/resources/
│  ├─ sql/                     # mysql-schema.sql 建库脚本
│  ├─ Dockerfile
│  └─ pom.xml
├─ frontend/                   # 用户端（Vue3 + TS + Element Plus）
│  ├─ src/api/                 # API 客户端
│  ├─ src/components/          # 公共组件
│  ├─ src/router/              # 路由
│  ├─ src/stores/              # Pinia 状态
│  ├─ src/views/               # 页面视图
│  ├─ public/
│  ├─ Dockerfile
│  └─ package.json
├─ admin-frontend/             # 管理后台（Vue3 + TS + Element Plus）
│  ├─ src/api/
│  ├─ src/components/
│  ├─ src/router/
│  ├─ src/stores/
│  ├─ src/views/
│  ├─ Dockerfile
│  └─ package.json
├─ ocr-service/                # OCR 微服务（Flask + Python）
│  ├─ app.py
│  ├─ config.py
│  ├─ ocr_service.py
│  ├─ deepseek_ocr_service.py
│  ├─ image_processor.py
│  ├─ requirements.txt
│  └─ Dockerfile
├─ scripts/                    # 初始化/启动/备份脚本
│  ├─ init-mysql-dev.ps1
│  ├─ start-backend-local.ps1
│  ├─ start-frontend-local.ps1
│  ├─ start-admin-frontend-local.ps1
│  ├─ start-ocr-local.ps1
│  └─ backup-mysql.ps1
├─ project-data/               # 样例数据
├─ backups/                    # 备份输出
├─ legacy/                     # 原两个项目归档
├─ .env.example                # 非敏感环境变量模板
├─ .env.secrets.example        # 敏感环境变量模板
├─ docker-compose.yml          # Docker 全栈编排
└─ README.md                   # 项目简介
```

---

## 11. 接口文档与测试

- **Swagger UI**：启动后端后访问 `http://localhost:8080/swagger-ui.html`
- **健康检查**：`http://localhost:8080/api/health`
- **OCR 健康**：`http://localhost:5001/ocr/health`
- **后端测试**：`cd backend && mvn test`
- **前端测试**：`cd frontend && npm run test`
- **前端构建**：`cd frontend && npm run build`
