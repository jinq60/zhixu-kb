package com.zhixu.kb.note.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档内容规范化：确定性清洗文档解析结果（瞬间完成，不调用 AI，绝不改写正文语义）。
 * - 换行规范化、行尾连字符（PDF 断字）合并、孤立页码删除
 * - 控制字符 / 乱码替换符 / 零宽字符清理、多余空行合并
 * - Markdown 标记剥离（保留 # 标题层级，由 NoteNormalizeExecutor 转为 h2-h4）
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
    /** 行尾连字符（英文断字）：字母- 结尾 */
    private static final Pattern TRAILING_HYPHEN_PATTERN = Pattern.compile("([A-Za-z])-\\s*$");
    /** 孤立页码：纯数字 1-4 位 */
    private static final Pattern PAGE_NUMBER_PATTERN = Pattern.compile("^\\s*\\d{1,4}\\s*$");
    /** 控制字符与乱码：\u0000-\u001F（保留 \n \t）、\uFFFD 替换符、零宽字符 */
    private static final Pattern JUNK_CHAR_PATTERN = Pattern.compile(
            "[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\uFFFD\\u200B-\\u200D\\uFEFF]");

    // ---------- 文档自带元信息 / 目录区过滤（防止干扰正文阅读） ----------
    /** 聚合/阅读版元信息：由N个主题文档聚合生成、全文约N行、适合手机阅读与分享等（容忍空格与括号变体） */
    private static final Pattern META_INFO_PATTERN = Pattern.compile(
            "由\\s*\\d+\\s*个\\s*(?:主题|文档)[^\\n]{0,60}?生成"
            + "|全文约\\s*\\d+\\s*[行字]"
            + "|共\\s*\\d+\\s*个\\s*主题"
            + "|第\\s*\\d+\\s*篇\\s*·\\s*由"
            + "|适合手机阅读|手机阅读版|九篇系列|公众号|扫码关注|版权归原作者"
            + "|【[^】]{0,30}?(?:手机阅读|系列|聚合生成|第\\s*\\d+\\s*篇)[^】]{0,30}?】");
    /** 独立"目录"标题行 */
    private static final Pattern TOC_TITLE_PATTERN = Pattern.compile("^\\s*目录\\s*$");
    /** 目录项 / 每篇重复标题行：01-短名 | 全称 */
    private static final Pattern TOC_ITEM_PATTERN = Pattern.compile(
            "^\\s*\\d{1,3}-[^|\\n]{2,50}\\s*\\|\\s*[^|\\n]{2,80}\\s*$");
    /** 行内串联的多个目录项（"08-xxx | xxx 09-xxx | xxx ..."） */
    private static final Pattern INLINE_TOC_ITEM = Pattern.compile(
            "\\d{1,3}-[^|\\n]{2,50}\\s*\\|\\s*[^|\\n]{2,80}");

    /**
     * 过滤文档自带干扰行（元信息、目录区、重复标题行），按行处理，保留其余内容不变。
     * 适用于纯文本与 Xberg HTML 两种路径。
     */
    public String filterNoiseLines(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        boolean inToc = false;
        for (String rawLine : text.split("\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            // 剥离 HTML 标签后判定（兼容 Xberg HTML 行）
            String plain = stripHtmlTags(line);
            // 元信息行直接删除
            if (META_INFO_PATTERN.matcher(plain).find()) {
                continue;
            }
            // "目录"标题行：进入目录区模式
            if (TOC_TITLE_PATTERN.matcher(plain).matches()) {
                inToc = true;
                continue;
            }
            // 目录项行（在目录区内，或正文中每篇开头的重复标题行）→ 删除
            if (TOC_ITEM_PATTERN.matcher(plain).matches()) {
                continue;
            }
            if (inToc) {
                // 目录区结束：退出状态，当前行是第一个非目录项行，正常保留
                inToc = false;
            }
            // 行内串联多个目录项（如整行都是 "08-xxx | xxx 09-xxx | xxx"）→ 删除整行
            if (countInlineTocItems(plain) >= 2) {
                continue;
            }
            sb.append(rawLine).append('\n');
        }
        String result = sb.toString();
        return StringUtils.hasText(result) ? result.trim() : "";
    }

    private String stripHtmlTags(String line) {
        String text = line.replaceAll("<[^>]+>", " ").trim();
        return text.replaceAll("\\s+", " ").trim();
    }

    private int countInlineTocItems(String line) {
        Matcher m = INLINE_TOC_ITEM.matcher(line);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    /**
     * 规范化文档解析文本（确定性，毫秒级）。
     * 无论是否含 Markdown 标记，都执行基础清洗（连字符/页码/乱码/空行）；
     * 含 Markdown 标记时额外剥离格式标记并保留 # 标题结构。
     *
     * @return 清洗后的文本（保留标题层级）；入参为空返回空串
     */
    public String normalize(String rawText) {
        if (!StringUtils.hasText(rawText)) {
            return "";
        }
        String input = rawText.replace("\uFEFF", "").trim();
        if (!StringUtils.hasText(input)) {
            return "";
        }
        // 先过滤文档自带干扰行（聚合信息/目录区/重复标题），再做结构清洗
        input = filterNoiseLines(input);
        if (hasMarkdownSyntax(input)) {
            return structuredClean(input);
        }
        return basicClean(input);
    }

    private boolean hasMarkdownSyntax(String text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        return MARKDOWN_PATTERN.matcher(text).find();
    }

    /**
     * 基础清洗：换行规范化、断字合并、页码/乱码删除、空行合并。
     */
    private String basicClean(String text) {
        String cleaned = normalizeNewlines(text);
        cleaned = JUNK_CHAR_PATTERN.matcher(cleaned).replaceAll("");
        StringBuilder sb = new StringBuilder();
        String[] lines = cleaned.split("\\n");
        boolean prevEmpty = true;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i] == null ? "" : lines[i].trim();
            if (line.isEmpty()) {
                if (!prevEmpty) {
                    sb.append('\n');
                    prevEmpty = true;
                }
                continue;
            }
            // 行尾断字：合并到下一行（下一行以字母开头）
            if (TRAILING_HYPHEN_PATTERN.matcher(line).find() && i + 1 < lines.length) {
                String next = lines[i + 1] == null ? "" : lines[i + 1].trim();
                if (!next.isEmpty() && Character.isLetter(next.charAt(0))) {
                    line = TRAILING_HYPHEN_PATTERN.matcher(line).replaceFirst("$1") + next;
                    i++;
                } else {
                    line = line.replaceAll("-\\s*$", "");
                }
            }
            // 孤立页码行删除
            if (PAGE_NUMBER_PATTERN.matcher(line).matches()) {
                continue;
            }
            sb.append(line).append('\n');
            prevEmpty = false;
        }
        return sb.toString().trim();
    }

    /**
     * 确定性清洗：删标记、保留标题层级、保留正文（在基础清洗之上）。
     */
    private String structuredClean(String text) {
        String cleaned = normalizeNewlines(text);
        cleaned = CODE_BLOCK_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = JUNK_CHAR_PATTERN.matcher(cleaned).replaceAll("");
        StringBuilder sb = new StringBuilder();
        String[] lines = cleaned.split("\\n");
        boolean prevEmpty = true;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i] == null ? "" : lines[i].trim();
            if (t.isEmpty()) {
                if (!prevEmpty) {
                    sb.append('\n');
                    prevEmpty = true;
                }
                continue;
            }
            // 行尾断字合并
            if (TRAILING_HYPHEN_PATTERN.matcher(t).find() && i + 1 < lines.length) {
                String next = lines[i + 1] == null ? "" : lines[i + 1].trim();
                if (!next.isEmpty() && Character.isLetter(next.charAt(0))) {
                    t = TRAILING_HYPHEN_PATTERN.matcher(t).replaceFirst("$1") + next;
                    i++;
                } else {
                    t = t.replaceAll("-\\s*$", "");
                }
            }
            // 孤立页码行删除
            if (PAGE_NUMBER_PATTERN.matcher(t).matches()) {
                continue;
            }
            // 分隔线整行删除
            if (SEPARATOR_PATTERN.matcher(t).matches()) {
                continue;
            }
            // 标题行：保留 # 前缀结构
            if (t.matches("^#{1,6}\\s+.*")) {
                sb.append(t).append("\n");
                prevEmpty = false;
                continue;
            }
            // 列表行：保留行首标记（-/*/•/数字），由 toStructuredHtml 还原为 ul/ol
            if (LIST_PATTERN.matcher(t).matches()) {
                String plain = MARKERS_PATTERN.matcher(t).replaceAll("");
                plain = WHITESPACE_PATTERN.matcher(plain).replaceAll(" ");
                if (plain.trim().length() > 0) {
                    sb.append(plain.trim()).append("\n");
                    prevEmpty = false;
                }
                continue;
            }
            // 普通行：删引用/行内标记
            String plain = QUOTE_PATTERN.matcher(t).replaceFirst("");
            plain = MARKERS_PATTERN.matcher(plain).replaceAll("");
            plain = WHITESPACE_PATTERN.matcher(plain).replaceAll(" ");
            if (plain.trim().length() > 0) {
                sb.append(plain.trim()).append("\n");
                prevEmpty = false;
            }
        }
        return sb.toString().trim();
    }

    private String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** 数字编号标题：1. 1.1 1、 1） （1） */
    private static final Pattern NUMBER_HEADING_PATTERN = Pattern.compile(
            "^(\\d{1,2}(?:\\.\\d{1,2}){0,2}+)\\s*[.、．)）]?\\s*(.+)");
    /** 中文编号标题：一、 （一） 第一章（group(1)=标题文本） */
    private static final Pattern CN_HEADING_PATTERN = Pattern.compile(
            "^(?:第[一二三四五六七八九十百0-9]+[章节篇部部分]|（[一二三四五六七八九十]+）|[一二三四五六七八九十]+[、.．])\\s*(.+)");

    /**
     * 将清洗后的结构化文本转为可读 HTML（确定性，不调用 AI）：
     * - "# "→h2、"## "→h3、"###+"→h4（保留标题层级，与目录结构对应）
     * - 编号标题：1. / 1.1 / 一、 / 第一章 / （一） → h2-h4（按编号层级推导）
     * - 列表行（-、*、• 开头）→ ul/li
     * - 其余段落：空行分隔段落，段内连续行用 <br> 连接（避免逐行碎 p）
     */
    public String toStructuredHtml(String text) {
        StringBuilder sb = new StringBuilder();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String[] lines = text.split("\\n");
        StringBuilder paragraph = new StringBuilder();
        StringBuilder list = new StringBuilder();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                flushParagraph(sb, paragraph);
                flushList(sb, list);
                continue;
            }
            if (isHeadingLine(trimmed)) {
                flushParagraph(sb, paragraph);
                flushList(sb, list);
                appendHeading(sb, trimmed);
                continue;
            }
            if (isListLine(trimmed)) {
                flushParagraph(sb, paragraph);
                String item = stripListMark(trimmed);
                if (list.length() == 0) {
                    list.append("<ul>");
                }
                list.append("<li>").append(org.springframework.web.util.HtmlUtils.htmlEscape(item)).append("</li>");
                continue;
            }
            flushList(sb, list);
            String escaped = org.springframework.web.util.HtmlUtils.htmlEscape(trimmed);
            if (paragraph.length() > 0) {
                paragraph.append("<br>");
            }
            paragraph.append(escaped);
        }
        flushParagraph(sb, paragraph);
        flushList(sb, list);
        return sb.toString().trim();
    }

    private boolean isHeadingLine(String line) {
        if (line.startsWith("#")) {
            return true;
        }
        if (NUMBER_HEADING_PATTERN.matcher(line).find()) {
            return true;
        }
        return CN_HEADING_PATTERN.matcher(line).find();
    }

    private void appendHeading(StringBuilder sb, String line) {
        String text;
        int level;
        if (line.startsWith("#")) {
            int count = 0;
            while (count < line.length() && line.charAt(count) == '#') {
                count++;
            }
            text = line.substring(count).trim();
            level = Math.min(Math.max(count, 1), 3);
        } else {
            Matcher m = NUMBER_HEADING_PATTERN.matcher(line);
            if (m.find()) {
                text = m.group(2).trim();
                int segments = m.group(1).split("\\.").length;
                level = Math.min(Math.max(segments, 1), 3);
            } else {
                Matcher cm = CN_HEADING_PATTERN.matcher(line);
                if (!cm.find()) {
                    return;
                }
                text = cm.group(1).trim();
                level = 1;
            }
        }
        if (!StringUtils.hasText(text)) {
            return;
        }
        String tag = level == 1 ? "h2" : (level == 2 ? "h3" : "h4");
        sb.append("<").append(tag).append(">")
                .append(org.springframework.web.util.HtmlUtils.htmlEscape(text))
                .append("</").append(tag).append(">");
    }

    private boolean isListLine(String line) {
        return line.matches("^[-*•·]\\s+.+");
    }

    private String stripListMark(String line) {
        return line.replaceFirst("^[-*•·]\\s+", "");
    }

    private void flushParagraph(StringBuilder sb, StringBuilder paragraph) {
        if (paragraph.length() == 0) {
            return;
        }
        sb.append("<p>").append(paragraph).append("</p>");
        paragraph.setLength(0);
    }

    private void flushList(StringBuilder sb, StringBuilder list) {
        if (list.length() == 0) {
            return;
        }
        list.append("</ul>");
        sb.append(list);
        list.setLength(0);
    }
}