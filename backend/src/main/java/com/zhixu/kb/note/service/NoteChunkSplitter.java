package com.zhixu.kb.note.service;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义切分：基于笔记 HTML 标题结构（h1-h6）切块，超长段按句子边界二次切分，块间重叠。
 * - 保留标题作为块前缀（提供上下文，防止切太碎丢失语义）
 * - 相邻小块合并（< minChars 与下一块合并，避免碎片）
 * - 总块数上限，防止超大文档无限切块（截断时输出告警日志）
 */
@Slf4j
public final class NoteChunkSplitter {

    /** 单块目标字符数 */
    private static final int TARGET_CHARS = 700;
    /** 块间重叠字符数 */
    private static final int OVERLAP_CHARS = 120;
    /** 最小独立块（小于此值并入相邻块） */
    private static final int MIN_CHARS = 180;
    /** 每篇最大块数（大文档覆盖前约 4.5 万字符；再大靠分页/分卷或后续多文档策略） */
    private static final int MAX_CHUNKS = 64;

    /** HTML 标题 → 文本标题标记 */
    private static final Pattern HEADING_TAG_PATTERN =
            Pattern.compile("<h([1-6])[^>]*>(.*?)</h\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern OTHER_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[ \\t]+");

    private NoteChunkSplitter() {
    }

    public static List<String> split(String htmlOrText) {
        String text = toTextWithHeadings(htmlOrText);
        if (text == null || text.isEmpty()) {
            return new ArrayList<>();
        }
        // 1) 按标题行（## 等）切分为章节
        List<String> sections = splitByHeadings(text);
        // 2) 每个章节再按目标大小切分（句子边界 + 重叠）
        List<String> chunks = new ArrayList<>();
        for (String section : sections) {
            chunks.addAll(splitSection(section));
        }
        // 3) 合并过小的碎片
        List<String> merged = mergeSmallChunks(chunks);
        // 4) 块数上限：截断时必须告警——尾部内容不参与向量检索，不能静默丢失
        if (merged.size() > MAX_CHUNKS) {
            int droppedChars = 0;
            for (int i = MAX_CHUNKS; i < merged.size(); i++) {
                droppedChars += merged.get(i).length();
            }
            log.warn("Chunk split truncated: totalChunks={} max={} droppedChars={} sourceChars={} "
                            + "（超出部分不参与 RAG 向量检索）",
                    merged.size(), MAX_CHUNKS, droppedChars, text.length());
            return new ArrayList<>(merged.subList(0, MAX_CHUNKS));
        }
        return merged;
    }

    /**
     * HTML → 纯文本，标题行转为 "## 标题" 标记（保留层级语义）。
     */
    private static String toTextWithHeadings(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Matcher matcher = HEADING_TAG_PATTERN.matcher(html);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            String title = cleanInline(matcher.group(2));
            String prefix = level <= 2 ? "## " : "### ";
            matcher.appendReplacement(sb, Matcher.quoteReplacement("\n" + prefix + title + "\n"));
        }
        matcher.appendTail(sb);
        String text = OTHER_TAG_PATTERN.matcher(sb.toString()).replaceAll(" ");
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"");
        return WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
    }

    private static String cleanInline(String raw) {
        String t = OTHER_TAG_PATTERN.matcher(raw == null ? "" : raw).replaceAll(" ");
        t = WHITESPACE_PATTERN.matcher(t).replaceAll(" ").trim();
        return t.length() > 40 ? t.substring(0, 40) : t;
    }

    /**
     * 按 "## 标题" 行切分为章节（标题保留在块开头作为上下文）。
     */
    private static List<String> splitByHeadings(String text) {
        List<String> sections = new ArrayList<>();
        String[] lines = text.split("\n");
        StringBuilder current = new StringBuilder();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ") || trimmed.startsWith("### ")) {
                if (current.length() > 0) {
                    sections.add(current.toString().trim());
                    current.setLength(0);
                }
                current.append(trimmed).append('\n');
            } else if (trimmed.isEmpty()) {
                current.append('\n');
            } else {
                current.append(trimmed).append('\n');
            }
        }
        if (current.length() > 0) {
            sections.add(current.toString().trim());
        }
        if (sections.isEmpty() && text.trim().length() > 0) {
            sections.add(text.trim());
        }
        return sections;
    }

    /**
     * 章节内按句子边界贪心切分，块间重叠 OVERLAP_CHARS。
     */
    private static List<String> splitSection(String section) {
        if (section.length() <= TARGET_CHARS) {
            List<String> single = new ArrayList<>();
            single.add(section);
            return single;
        }
        List<String> chunks = new ArrayList<>();
        String remaining = section;
        while (remaining.length() > TARGET_CHARS) {
            int cut = findSentenceBoundary(remaining, TARGET_CHARS);
            String chunk = remaining.substring(0, cut).trim();
            chunks.add(chunk);
            // 重叠：保留上一块末尾 OVERLAP_CHARS 字符
            int nextStart = Math.max(0, cut - OVERLAP_CHARS);
            remaining = remaining.substring(nextStart).trim();
        }
        if (remaining.length() > 0) {
            chunks.add(remaining);
        }
        return chunks;
    }

    /**
     * 在 [min, max] 范围内找句子边界（。！？；；\n），找不到则按 max 截断。
     */
    private static int findSentenceBoundary(String text, int max) {
        int min = (int) (max * 0.6);
        for (int i = max; i > min; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n' || c == ';' || c == '.' || c == ' ') {
                return i + 1;
            }
        }
        return max;
    }

    /**
     * 合并过小碎片：< MIN_CHARS 的块并入下一块（最后一块并入前一块）。
     */
    private static List<String> mergeSmallChunks(List<String> chunks) {
        if (chunks.size() <= 1) {
            return chunks;
        }
        List<String> merged = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        for (String chunk : chunks) {
            pending.append(chunk).append('\n');
            if (pending.length() >= MIN_CHARS) {
                merged.add(pending.toString().trim());
                pending.setLength(0);
            }
        }
        if (pending.length() > 0) {
            if (!merged.isEmpty()) {
                String last = merged.remove(merged.size() - 1);
                merged.add((last + "\n" + pending.toString()).trim());
            } else {
                merged.add(pending.toString().trim());
            }
        }
        return merged;
    }
}