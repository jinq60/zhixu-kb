package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

/**
 * V2：老库补齐（V1 基线之后新增的列与表）。
 * <p>
 * MySQL 不支持 {@code ADD COLUMN IF NOT EXISTS}，故按 DatabaseMetaData 逐项检查后执行，
 * 新库（V1 已建全）走空操作，老库按需补齐。全部语句均为加法（ADD COLUMN / CREATE TABLE
 * IF NOT EXISTS / CREATE INDEX），不删不改现有数据。
 */
public class V2__Backfill_missing_columns_and_tables extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V2__Backfill_missing_columns_and_tables.class);

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        DatabaseMetaData meta = connection.getMetaData();
        String schema = connection.getCatalog();

        ensureColumn(connection, meta, schema,
                "sys_user", "email_verified", "ADD COLUMN email_verified TINYINT NOT NULL DEFAULT 0 AFTER email");
        ensureColumn(connection, meta, schema,
                "ask_records", "conversation_id", "ADD COLUMN conversation_id VARCHAR(36) NULL AFTER risk_flags");
        ensureColumn(connection, meta, schema,
                "ai_user_config", "embedding_base_url", "ADD COLUMN embedding_base_url VARCHAR(255) NULL AFTER model");
        ensureColumn(connection, meta, schema,
                "ai_user_config", "embedding_api_key", "ADD COLUMN embedding_api_key VARCHAR(500) NULL AFTER embedding_base_url");
        ensureColumn(connection, meta, schema,
                "ai_user_config", "embedding_model", "ADD COLUMN embedding_model VARCHAR(100) NULL AFTER embedding_api_key");
        ensureColumn(connection, meta, schema,
                "ai_endpoints", "embedding_model", "ADD COLUMN embedding_model VARCHAR(100) NULL AFTER model");

        ensureIndex(connection, meta, schema,
                "ask_records", "idx_conversation",
                "CREATE INDEX idx_conversation ON ask_records (user_id, conversation_id)");

        ensureTable(connection,
                "sys_user_auth",
                "CREATE TABLE IF NOT EXISTS sys_user_auth ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "user_id BIGINT NOT NULL,"
                        + "provider VARCHAR(32) NOT NULL,"
                        + "account VARCHAR(128) NOT NULL,"
                        + "credential VARCHAR(255),"
                        + "is_deleted TINYINT DEFAULT 0,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE,"
                        + "INDEX idx_user_provider (user_id, provider),"
                        + "INDEX idx_provider_account (provider, account),"
                        + "UNIQUE KEY uk_provider_account (provider, account, is_deleted)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        ensureTable(connection,
                "note_embedding_chunk",
                "CREATE TABLE IF NOT EXISTS note_embedding_chunk ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "note_id BIGINT NOT NULL,"
                        + "user_id BIGINT NOT NULL,"
                        + "chunk_index INT DEFAULT 0,"
                        + "chunk_text TEXT,"
                        + "vector BLOB,"
                        + "dimension INT DEFAULT 0,"
                        + "created_at DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "INDEX idx_user_note (user_id, note_id),"
                        + "FOREIGN KEY (note_id) REFERENCES note(id) ON DELETE CASCADE"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        ensureTable(connection,
                "document_process_task",
                "CREATE TABLE IF NOT EXISTS document_process_task ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "user_id BIGINT NOT NULL,"
                        + "note_id BIGINT NOT NULL,"
                        + "file_id BIGINT,"
                        + "file_name VARCHAR(255),"
                        + "status VARCHAR(20) DEFAULT 'PENDING',"
                        + "current_stage VARCHAR(20) DEFAULT 'PENDING',"
                        + "progress INT DEFAULT 0,"
                        + "fail_reason TEXT,"
                        + "retry_count INT DEFAULT 0,"
                        + "max_retry INT DEFAULT 5,"
                        + "parsed_text MEDIUMTEXT,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "INDEX idx_user_status (user_id, status),"
                        + "INDEX idx_status_update (status, update_time)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        ensureTable(connection,
                "clean_chunk_task",
                "CREATE TABLE IF NOT EXISTS clean_chunk_task ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "task_id BIGINT NOT NULL,"
                        + "chunk_index INT NOT NULL,"
                        + "raw_content MEDIUMTEXT,"
                        + "cleaned_content MEDIUMTEXT,"
                        + "status VARCHAR(20) DEFAULT 'PENDING',"
                        + "retry_count INT DEFAULT 0,"
                        + "error_msg TEXT,"
                        + "create_time DATETIME DEFAULT CURRENT_TIMESTAMP,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "INDEX idx_task_status (task_id, status)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        ensureTable(connection,
                "embed_chunk_task",
                "CREATE TABLE IF NOT EXISTS embed_chunk_task ("
                        + "id BIGINT PRIMARY KEY AUTO_INCREMENT,"
                        + "task_id BIGINT NOT NULL,"
                        + "chunk_index INT NOT NULL,"
                        + "content MEDIUMTEXT,"
                        + "status VARCHAR(20) DEFAULT 'PENDING',"
                        + "milvus_id VARCHAR(64),"
                        + "retry_count INT DEFAULT 0,"
                        + "error_msg TEXT,"
                        + "update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,"
                        + "INDEX idx_task_status (task_id, status)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    }

    private void ensureColumn(Connection connection, DatabaseMetaData meta, String schema,
                              String table, String column, String addClause) throws Exception {
        if (hasColumn(meta, schema, table, column)) {
            return;
        }
        execute(connection, "ALTER TABLE " + table + " " + addClause);
        log.info("flyway V2: column {}.{} added", table, column);
    }

    private void ensureIndex(Connection connection, DatabaseMetaData meta, String schema,
                             String table, String index, String createSql) throws Exception {
        try (ResultSet rs = meta.getIndexInfo(schema, null, table, false, false)) {
            while (rs.next()) {
                if (index.equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                    return;
                }
            }
        }
        execute(connection, createSql);
        log.info("flyway V2: index {}.{} created", table, index);
    }

    private void ensureTable(Connection connection, String table, String createSql) throws Exception {
        execute(connection, createSql);
        log.debug("flyway V2: table {} ensured", table);
    }

    private boolean hasColumn(DatabaseMetaData meta, String schema, String table, String column) throws Exception {
        try (ResultSet rs = meta.getColumns(schema, null, table, null)) {
            Set<String> names = new HashSet<>();
            while (rs.next()) {
                names.add(rs.getString("COLUMN_NAME").toLowerCase());
            }
            return names.contains(column.toLowerCase());
        } catch (Exception ex) {
            // 表本身不存在时 getColumns 可能抛异常：CREATE TABLE IF NOT EXISTS 会处理，列检查视为缺失
            log.debug("flyway V2: column check {}.{} failed (table may be missing): {}",
                    table, column, ex.getMessage());
            return false;
        }
    }

    private void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    @Override
    public String getDescription() {
        return "Backfill missing columns and tables";
    }
    // 注意：Java 迁移不提供 checksum（BaseJavaMigration 默认），Flyway 只按版本号判定是否执行；
    // 已应用的 V2 禁止再改，新增变更一律发新版本。
}
