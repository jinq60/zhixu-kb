package com.zhixu.kb.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 检索索引迁移器：为已有部署幂等补建 note(summary, keywords, ocr_text) 的
 * ngram FULLTEXT 索引（docker 首次建库由 mysql-schema.sql 直接创建，此处兜底存量库）。
 *
 * 背景：知识问答的 FULLTEXT 召回此前只覆盖 title/content，命中仅存在于
 * 摘要/关键词/OCR 文本中的笔记只能靠"最近 200 篇扫描"兜底，老笔记检索不到。
 * 迁移失败（如 MySQL 权限受限）仅降级为原有关键词召回，不阻断启动。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SearchIndexMigrator implements ApplicationRunner {

    public static final String META_INDEX_NAME = "ft_meta";

    private static final String CREATE_META_INDEX_SQL =
            "ALTER TABLE note ADD FULLTEXT INDEX " + META_INDEX_NAME
                    + " (summary, keywords, ocr_text) WITH PARSER ngram";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.statistics "
                            + "WHERE table_schema = DATABASE() AND table_name = 'note' AND index_name = ?",
                    Integer.class, META_INDEX_NAME);
            if (exists != null && exists > 0) {
                log.info("FULLTEXT index {} already present, skip migration", META_INDEX_NAME);
                return;
            }
            jdbcTemplate.execute(CREATE_META_INDEX_SQL);
            log.info("Created FULLTEXT index {} on note(summary, keywords, ocr_text)", META_INDEX_NAME);
        } catch (Exception ex) {
            // 存量数据大时 ALTER 可能耗时或权限不足：降级不阻断启动，问答仍可用原 title/content 召回
            log.warn("FULLTEXT index migration skipped ({}): {}", META_INDEX_NAME, ex.getMessage());
        }
    }
}
