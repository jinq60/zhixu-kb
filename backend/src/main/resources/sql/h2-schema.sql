-- =====================================================================
-- 知序智能知识库 - H2 建表脚本（桌面版 desktop profile 专用）
-- 以 MODE=MySQL 打开：jdbc:h2:file:<dataDir>/zhixu;MODE=MySQL;DATABASE_TO_LOWER=TRUE
-- 与 backend/sql/mysql-schema.sql 保持同构；差异：
--   1) 去 FULLTEXT/ENGINE/CHARSET（H2 无 ngram，检索走 LIKE 降级路径）
--   2) 去列内联 COMMENT（降低方言解析风险）
--   3) 初始角色数据放 h2-data.sql（避免 ON DUPLICATE 方言差异）
-- 服务器 Docker 部署不受本文件影响（仍执行 mysql-schema.sql）。
-- =====================================================================

-- ---------- 用户 ----------
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    email VARCHAR(100) UNIQUE,
    email_verified TINYINT NOT NULL DEFAULT 0,
    avatar VARCHAR(255),
    status TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL,
    role_key VARCHAR(50) NOT NULL UNIQUE,
    status TINYINT DEFAULT 1,
    remark VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE
);

-- ---------- 用户多方式认证绑定 ----------
CREATE TABLE IF NOT EXISTS sys_user_auth (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    provider VARCHAR(32) NOT NULL,
    account VARCHAR(128) NOT NULL,
    credential VARCHAR(255),
    is_deleted TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,
    UNIQUE KEY uk_provider_account (provider, account, is_deleted)
);

-- ---------- 笔记（个人知识库） ----------
CREATE TABLE IF NOT EXISTS category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    UNIQUE KEY uk_user_name (user_id, name, is_deleted)
);

CREATE TABLE IF NOT EXISTS note (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT,
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT,
    ocr_text MEDIUMTEXT,
    summary TEXT,
    keywords VARCHAR(255),
    cover_image VARCHAR(500),
    status TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    FOREIGN KEY (user_id) REFERENCES sys_user(id),
    FOREIGN KEY (category_id) REFERENCES category(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS file_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    note_id BIGINT,
    original_name VARCHAR(255) NOT NULL,
    stored_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(500) NOT NULL,
    file_size BIGINT,
    mime_type VARCHAR(100),
    upload_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS note_section (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    note_id BIGINT NOT NULL,
    parent_id BIGINT,
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT,
    level TINYINT DEFAULT 1,
    sort_order INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT DEFAULT 0,
    FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES note_section(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS note_mindmap (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    note_id BIGINT NOT NULL,
    map_type VARCHAR(50) NOT NULL,
    map_data LONGTEXT,
    map_url VARCHAR(500),
    thumbnail_url VARCHAR(500),
    version INT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_note_mindmap (note_id),
    FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS note_structure (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    note_id BIGINT NOT NULL,
    outline_json LONGTEXT,
    architecture_img_url VARCHAR(500),
    updated_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_note_structure (note_id),
    FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
);

-- ---------- 知识问答（基于个人知识库的 RAG） ----------
CREATE TABLE IF NOT EXISTS ask_records (
    id VARCHAR(36) PRIMARY KEY,
    user_id BIGINT NOT NULL,
    question TEXT,
    answer MEDIUMTEXT,
    related_notes TEXT,
    status VARCHAR(20) DEFAULT 'processing',
    confidence_level VARCHAR(20),
    risk_flags TEXT,
    conversation_id VARCHAR(36),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at DATETIME
);

-- ---------- 笔记向量块（桌面版默认关闭向量检索，表结构保留以兼容） ----------
CREATE TABLE IF NOT EXISTS note_embedding_chunk (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    note_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    chunk_index INT DEFAULT 0,
    chunk_text TEXT,
    vector BLOB,
    dimension INT DEFAULT 0,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE
);

-- ---------- 文档处理状态机 ----------
CREATE TABLE IF NOT EXISTS document_process_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    note_id BIGINT NOT NULL,
    file_id BIGINT,
    file_name VARCHAR(255),
    status VARCHAR(20) DEFAULT 'PENDING',
    current_stage VARCHAR(20) DEFAULT 'PENDING',
    progress INT DEFAULT 0,
    fail_reason TEXT,
    retry_count INT DEFAULT 0,
    max_retry INT DEFAULT 5,
    parsed_text MEDIUMTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS clean_chunk_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    raw_content MEDIUMTEXT,
    cleaned_content MEDIUMTEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    retry_count INT DEFAULT 0,
    error_msg TEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS embed_chunk_task (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    chunk_index INT NOT NULL,
    content MEDIUMTEXT,
    status VARCHAR(20) DEFAULT 'PENDING',
    milvus_id VARCHAR(64),
    retry_count INT DEFAULT 0,
    error_msg TEXT,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ---------- 用户级 AI 配置 ----------
CREATE TABLE IF NOT EXISTS ai_user_config (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL UNIQUE,
    provider VARCHAR(50) DEFAULT 'deepseek',
    base_url VARCHAR(255),
    api_key VARCHAR(500),
    model VARCHAR(100),
    embedding_base_url VARCHAR(255),
    embedding_api_key VARCHAR(500),
    embedding_model VARCHAR(100),
    enabled TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ---------- 平台 AI 端点 ----------
CREATE TABLE IF NOT EXISTS ai_endpoints (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    base_url VARCHAR(255) NOT NULL,
    api_key VARCHAR(500) NOT NULL,
    model VARCHAR(100) NOT NULL,
    embedding_model VARCHAR(100),
    enabled TINYINT DEFAULT 1,
    remark VARCHAR(255),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ---------- 操作日志 ----------
CREATE TABLE IF NOT EXISTS operation_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT,
    operation_type VARCHAR(50),
    operation_desc VARCHAR(255),
    request_method VARCHAR(10),
    request_url VARCHAR(255),
    request_params MEDIUMTEXT,
    response_result MEDIUMTEXT,
    ip_address VARCHAR(50),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);
