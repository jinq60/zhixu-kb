# 知序智能知识库系统（Zhixu Knowledge Base）

> **在线知识体系整理平台**：个人知识管理产品，打通 **采集 → 识别 → 整理 → 图谱 → 问答**
> 五大环节，从零散资料（照片、文档、碎片信息）沉淀为可检索、可关联、可对话的个人知识体系。
>
> 由两个独立项目（学习笔记整理系统 + RAG 项目）合并重构而来：
> 统一后端（MyBatis-Plus）、统一前端（Vue3 + TypeScript + Element Plus），
> 知识问答（RAG）的检索对象是 **用户自己的笔记**，并补全了 **Neo4j 知识图谱** 能力。

---

## 1. 产品能力总览

| 环节 | 能力 | 技术 |
|---|---|---|
| 采集 | 图片与文档上传（txt/md/pdf/docx），文档自动提取文本 | Spring Boot + 本地文件存储 |
| 识别 | 图片 OCR 双引擎（RapidOCR / PaddleOCR / DeepSeek-OCR-2）自动降级、透视矫正；文档文本提取（POI/PDFBox） | Flask 微服务（:5001） |
| 整理 | AI 摘要/关键词/分类/多级大纲生成、思维导图预览、历史版本恢复 | DeepSeek API + 启发式规则兜底 |
| 图谱 | 实体关系抽取（AI 优先、规则兜底）、Neo4j 存储、力导图可视化 | Neo4j |
| 问答 | **基于个人知识库（自己的笔记）的 RAG 问答**、SSE 流式回答、引用溯源 | 笔记检索（MySQL 全文 + n-gram） |
| 分享 | 笔记公开发布、公开发现大厅、分享阅读 | REST API |
| 治理 | 系统总览、AI 引擎热切换（云端/本地）、用户角色管理、告警监控 | Redis + 限流 + Token 撤销 |

安全体系：JWT 会话 Cookie（HttpOnly + SameSite=Lax，JS 不可读）、Token 撤销（Redis + 本地双写）、接口限流、敏感数据加密（AES-GCM）与日志脱敏；DB 结构变更走 Flyway 版本化迁移。

---

## 2. 仓库结构

```text
IntelligentKnowledgeBase/
├─ backend/          # 统一后端 Spring Boot 3.2（Java 17）+ MyBatis-Plus
│  └─ sql/           # mysql-schema.sql 统一建库脚本
├─ frontend/         # 用户端 Vue3 + TypeScript + Element Plus + ECharts
├─ admin-frontend/   # 独立管理后台（登录制，深色布局，端口 5175）
├─ ocr-service/      # OCR 微服务（Flask，RapidOCR / PaddleOCR / DeepSeek-OCR-2）
├─ project-data/     # 样例数据
├─ scripts/          # 一键初始化与启动脚本
├─ .env.example      # 非敏感环境变量模板
├─ .env.secrets.example  # 敏感环境变量模板（API Key / 密钥）
├─ docker-compose.yml    # Docker 全栈编排（含全部中间件）
└─ legacy/           # 原两个项目归档（源码与论文/资料）
```

### 后端模块（com.zhixu.kb）

| 模块 | 说明 |
|---|---|
| `system` | 认证/用户/角色、健康检查、操作日志、用户治理 |
| `note` | 笔记、分类、文件、OCR 客户端、AI 整理、大纲、历史、公开分享 |
| `ask` | **知识问答**（检索自己的笔记 → RAG 生成）、引用溯源、SSE 流式、导出 |
| `graph` | 知识图谱（实体抽取 + Neo4j + 可视化数据） |
| `ai` | 云端 AI 引擎适配器（多厂商 OpenAI 兼容）+ 引擎状态 |
| `security` | JWT 过滤器、Token 撤销、限流、请求追踪、加密、脱敏 |
| `admin` | 系统总览、AI 引擎切换、告警、用户治理 |

---

## 3. 环境要求

- JDK 17+、Maven 3.6+（或项目自带 `mvnw.cmd`；Java 17 为桌面版 jpackage 打包前提）
- Node.js ^20.19.0 或 >=22.12.0、npm 10+
- Python 3.10+（Windows 建议 3.10）
- MySQL 8.0（全文检索依赖 ngram 解析器）
- Redis 7+（可选：未启动时自动降级为本地内存）
- Neo4j 4.4+（图谱功能，`neo4j.enabled=false` 可关闭）
- 平台默认模型已配置 OpenRouter 免费 API（零成本，无需额外环境）

---

## 4. 本地快速启动

### 4.1 配置环境变量

```powershell
Copy-Item .env.example .env.local
Copy-Item .env.secrets.example .env.secrets.local
# 编辑 .env.secrets.local，填入 DEEPSEEK_API_KEY（如使用云端模型）
```

### 4.2 初始化数据库

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\init-mysql-dev.ps1
```

### 4.3 启动服务（推荐 N 个终端）

| 顺序 | 服务 | 命令 | 端口 |
|---|---|---|---|
| 1 | OCR 服务 | `powershell -ExecutionPolicy Bypass -File .\scripts\start-ocr-local.ps1` | 5001 |
| 2 | 后端 | `powershell -ExecutionPolicy Bypass -File .\scripts\start-backend-local.ps1` | 8080 |
| 3 | 用户端 | `powershell -ExecutionPolicy Bypass -File .\scripts\start-frontend-local.ps1` | 5173 |
| 4 | 管理后台 | `powershell -ExecutionPolicy Bypass -File .\scripts\start-admin-frontend-local.ps1` | 5175 |
| 5 | Neo4j | 自行启动本机 Neo4j 服务（默认 bolt://localhost:7687），或 `docker run -d --name neo4j -p 7474:7474 -p 7687:7687 -e NEO4J_AUTH=neo4j/zhixu123456 neo4j:4.4-community` | 7687 |

### 4.4 访问地址

- 用户端：http://localhost:5173
- 管理后台：http://localhost:5175（管理员账号：注册时携带 `ADMIN_BOOTSTRAP_KEY` 创建，或首次部署时由运维配置；密码请勿使用默认弱口令）
- 后端接口：http://localhost:8080/api/health
- 接口文档（Swagger UI）：http://localhost:8080/swagger-ui.html
- OCR 健康检查：http://localhost:5001/ocr/health

---

## 5. Docker 一键部署（WSL2 / Docker Desktop）

中间件（MySQL / Redis / Neo4j）与应用服务（后端 / 前端 / OCR）
全部容器化，一条命令拉起全栈。

### 5.1 启动

```bash
# 1. 在仓库根目录创建 .env（可选，参考 .env.example / .env.secrets.example）
#    AI_API_KEY=sk-xxx            # DeepSeek API Key（AI 整理/问答/图谱抽取）
#    ADMIN_BOOTSTRAP_KEY=xxx      # 注册时携带该 key 自动成为管理员
#    NEO4J_PASSWORD=zhixu123456   # 如需修改图谱密码

# 2. 一键构建并启动（MySQL 首次启动自动建库）
docker compose up -d --build

# 3. 查看状态
docker compose ps

# 5.2 服务与端口

| 服务 | 容器名 | 端口 | 说明 |
|---|---|---|---|
| 用户端 | zhixu-frontend | 5173 | Nginx 托管 + /api 反代（SSE 已开） |
| 管理后台 | zhixu-admin-frontend | 5175 | 独立后台（登录制、AI 端点管理/用户治理） |
| 后端 | zhixu-backend | 8080 | Spring Boot |
| OCR | zhixu-ocr | 5001 | RapidOCR（onnxruntime，容器内默认） |
| MySQL | zhixu-mysql | 3306 | 自动执行 backend/sql/mysql-schema.sql |
| Redis | zhixu-redis | 6379 | Token 撤销 / 缓存 / 限流 |
| Neo4j | zhixu-neo4j | 7474/7687 | 知识图谱（默认密码 zhixu123456） |


数据持久化：全部中间件数据与后端上传文件挂载在 Docker volumes 中，
`docker compose down` 不丢数据；`docker compose down -v` 会清空。

### 5.3 容器化说明

- **OCR 引擎**：容器内默认使用 **RapidOCR**（onnxruntime 纯 CPU），
  规避 paddlepaddle 在 WSL2/Docker 虚拟化下的 SIGILL/内存崩溃兼容问题；
  本机直跑推荐 PaddleOCR（`requirements-paddle-legacy.txt`）。
- **AI 引擎**：`AI_ENGINE_TYPE=api`（DeepSeek 云端，需 `AI_API_KEY`）或
  云端 API。知识问答的知识检索基于 MySQL 笔记库（关键词 + n-gram
  相关性评分），不依赖外部向量库。
- **国内网络**：若拉取基础镜像超时，可用
  `docker pull dockerproxy.net/library/<image>:<tag>` 后手动 `docker tag` 替代。
- **健康检查**：`docker compose ps` 中 mysql/redis/neo4j 显示 healthy 后
  后端才正式提供服务。

### 5.4 ELK 日志中心（可选，profile: logs）

后端同时输出控制台日志与结构化 JSON 日志（`/app/logs/zhixu-backend.json.log`，
内容与控制台同一套脱敏规则），Filebeat 采集进 Elasticsearch，Kibana 查询。

```powershell
# 1. WSL2/Docker Desktop 必须先调内核参数（重启 Docker Desktop 后需重设）：
wsl -d docker-desktop -u root sysctl -w vm.max_map_count=262144

# 2. 启动日志栈（ES + Kibana + Filebeat，约 2~2.5GB 内存）：
docker compose --profile logs up -d

# 3. 打开 Kibana：http://localhost:5601
#    首次使用：Stack Management → Data Views → 创建 zhixu-backend-*（时间字段 @timestamp）
#    管理后台侧边栏「系统日志」可直达
```

资源占用：ES ~1.2GB / Kibana ~800MB / Filebeat ~300MB；不启用 profile 时主栈不受影响。
生产部署必须开启 ES/Kibana 的 TLS 与鉴权（当前为本地单机配置）。

### 5.5 数据备份与恢复

```powershell
# 手动备份（输出到 backups/mysql/，保留最近 14 天）
powershell -ExecutionPolicy Bypass -File .\scripts\backup-mysql.ps1

# 建议配置 Windows 计划任务每日 03:00 自动备份：
schtasks /Create /TN "zhixu-mysql-backup" /SC DAILY /ST 03:00 /TR "powershell -ExecutionPolicy Bypass -File <绝对路径>\scripts\backup-mysql.ps1"

# 恢复：
# docker exec -i zhixu-mysql mysql -uroot -proot zhixu_kb < 备份文件.sql
```

### 5.6 生产安全清单（上线前必读）

- **HTTPS**：公网部署必须在 nginx 前加 TLS 终止（域名证书 / 云负载均衡），
  否则密码、JWT、AI Key 均明文传输
- **密钥**：`JWT_SECRET` / `CRYPTO_AES_KEY` 缺失或过短时后端拒绝启动；
  请用随机数生成 32 字节以上字符串
- **管理员口令**：首次部署后立即修改管理员密码，勿沿用默认弱口令
- **中间件端口**：MySQL(3306)/Redis(6379)/Neo4j(7474/7687) 已默认仅绑定
  `127.0.0.1`，公网部署请保持此配置并确认防火墙
- **CORS**：通过 `CORS_ALLOWED_ORIGINS` 配置可信来源（逗号分隔），
  默认仅放行本机 5173/5175
- **AI Key 轮换**：仓库内多处保存真实 Key（.env / AI_ENDPOINTS），
  泄露后请立即在厂商控制台轮换

### 认证与多用户

- **登录方式**：账号密码（账号不存在时自动注册）、邮箱验证码、短信验证码、GitHub/Google/QQ OAuth 登录
- **数据隔离**：不同用户拥有独立的数据空间——笔记、问答、图谱互不干扰（后端按用户隔离）
- **注册限流**：单 IP 每日自动注册次数上限（`app.registration.max-per-day`，默认 10），防刷号
- **管理员**：注册时携带 `ADMIN_BOOTSTRAP_KEY` 创建管理员，或由管理员在管理后台变更角色
- **安全**：JWT 会话 Cookie（HttpOnly + SameSite=Lax，响应体不返回 token）、登出即撤销 Token（含 Cookie 清除）、验证码错误次数限制、OAuth 回调直接种 Cookie（无 code 经 URL 中转）

### AI 模型：云端 API（平台默认 + 用户自配）

- **平台默认云端 API**：本仓库 `.env` 已配置 OpenRouter **DeepSeek-chat**（超低价约 $0.3/百万 tokens，速度稳定），所有用户免配置直接可用
- **多端点自动切换（防 429）**：`.env` 的 `AI_ENDPOINTS` 支持配置多组（Key/地址/模型），调用失败自动冷却 60 秒并切换到下一个端点，可混配多个厂商规避限速
- **用户自配 API Key**（侧边栏"AI 设置"）：支持 DeepSeek / OpenAI / Kimi / 智谱 GLM / 通义千问 / 硅基流动 / 自定义（全部 OpenAI 兼容协议），Key 加密存储、脱敏展示、一键测试连接，配置后覆盖平台默认模型
- **优先级**：用户自配 Key → 平台端点池（多端点轮询）
- **速度优化**：文档清洗为确定性处理（毫秒级，去标记保留标题层级）；AI 整理异步执行（提交即返回，云端约 10 秒完成）；健康探测缓存避免额外请求

---

## 6. 核心接口速览

### 认证（/api/auth）
`POST /api/auth/register`（携带 `adminBootstrapKey` 可注册为管理员）、`POST /api/auth/login`、`POST /api/auth/logout`（撤销 Token）、`GET /api/auth/info`

### 笔记（/api/notes、/api/public/notes）
笔记 CRUD、全文搜索、`POST /api/files/upload`（图片 + txt/md/pdf/docx 文档，文档自动提取文本）、`POST /{id}/ocr`（engine=auto/paddle/deepseek）、`POST /{id}/ai-analysis`、大纲生成/编辑、历史版本恢复、公开大厅

### 知识图谱（/api/notes/{id}/graph）
`POST /graph/build` 构建（AI 抽取 + Neo4j 存储）、`GET /graph` 获取可视化数据、`DELETE /graph` 删除、`GET /api/graph/search` 全局实体检索

### 知识问答（/api/v1/ask，检索自己的笔记）
- `POST /api/v1/ask`：同步问答（自动检索个人知识库）
- `POST /api/v1/ask/stream`：SSE 流式问答
- `GET /api/v1/ask`：历史记录；`GET /api/v1/ask/{id}/export`：导出 Markdown

### 管理端（/api/v1/admin，需 admin 角色）
- `GET /api/v1/admin/system/overview`：系统总览（AI 引擎/告警）
- `POST /api/v1/admin/system/ai-engine`：AI 引擎配置
- `GET /api/v1/admin/users`：用户治理
- `GET /api/v1/admin/graph/overview`：知识图谱总览

---

## 7. 质量验证

```powershell
# 后端编译 + 测试
cd backend; mvn -q compile -DskipTests
cd backend; mvn test

# 前端类型检查 + 构建 + 单测
cd frontend; npm run build
cd frontend; npm run test
```

---

## 8. 工程实践要点

- **降级优先**：OCR 多引擎自动切换；AI 整理不可用时回退启发式规则；知识问答未命中
  知识库时明确提示；Redis 不可用时本地 TTL 缓存兜底
- **安全**：JWT + Token 撤销、接口限流（用户/IP 维度）、敏感字段 AES-GCM 加密、日志脱敏、请求 traceId 追踪
- **可观测**：系统资源监控（CPU/堆内存）超阈值自动告警，管理端可视化
- **可运维**：统一环境变量模板、一键初始化/启动脚本、Docker 全栈编排、幂等建库脚本
