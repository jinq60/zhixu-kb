package com.zhixu.kb.note.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

/**
 * 文档内容规范化：确定性清洗 Markdown/富文本标记（瞬间完成，不调用 AI）。
 * - 仅删除格式标记符号，绝不改写正文内容
 * - 保留标题层级（# 前缀行 → 由 NoteNormalizeExecutor 转为 h2/h3）
 * - 纯文本（无标记）直接返回原文
 */
@Slf4j
@Service
public class DocumentNormalizeService {

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("(?s)```[a-zA-Z0-9]*\\s*\\n?.*?```");
    private static final Pattern MARKDOWN_PATTERN = Pattern.compile(
            "(?m)^\\s*#{1,6}\\s|^\\s*[-*+]\\s|^\\s*\\d+[.、．)）]\\s|^\\s*>\\s|^\\s*[-*_]{3,}\\s*$|\\*\\*|\\*|`|~~|<[^>]+>");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("(?m)^\\s*[-*_]{3,}\\s*$");
    private static final Pattern LIST_PATTERN = Pattern.compile("(?m)^\\s*(?:[-*+]|\\d+[.、．)）])\\s+");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("(?m)^\\s*>\\s+");
    private static final Pattern MARKERS_PATTERN = Pattern.compile("\\*\\*|\\*|`|~~|_");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[ \\t]+");

    /**
     * 规范化文档提取文本（确定性，毫秒级）。
     *
     * @return 清洗后的文本（保留 # 标题结构）；无标记时返回原文；入参为空返回空串
     */
    public String normalize(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        String input = rawText.trim();
        // 纯文本（无 Markdown 标记）直接返回
        if (!hasMarkdownSyntax(input)) {
            return input;
        }
        return structuredClean(input);
    }

    private boolean hasMarkdownSyntax(String text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        return MARKDOWN_PATTERN.matcher(text).find();
    }

    /**
     * 确定性清洗：删标记、保留标题层级、保留正文。
     */
    private String structuredClean(String text) {
        String cleaned = text.replace("\r\n", "\n").replace('\r', '\n');
        cleaned = CODE_BLOCK_PATTERN.matcher(cleaned).replaceAll("");
        StringBuilder sb = new StringBuilder();
        for (String line : cleaned.split("\\n")) {
            String t = line == null ? "" : line.trim();
            if (t.isEmpty()) {
                continue;
            }
            // 分隔线整行删除
            if (SEPARATOR_PATTERN.matcher(t).matches()) {
                continue;
            }
            // 标题行：保留 # 前缀结构
            if (t.matches("^#{1,6}\\s+.*")) {
                sb.append(t).append("\n");
                continue;
            }
            // 普通行：删列表/引用/行内标记
            String plain = LIST_PATTERN.matcher(t).replaceFirst("");
            plain = QUOTE_PATTERN.matcher(plain).replaceFirst("");
            plain = MARKERS_PATTERN.matcher(plain).replaceAll("");
            plain = WHITESPACE_PATTERN.matcher(plain).replaceAll(" ");
            if (plain.trim().length() > 0) {
                sb.append(plain.trim()).append("\n");
            }
        }
        return sb.toString().trim();
    }
}
