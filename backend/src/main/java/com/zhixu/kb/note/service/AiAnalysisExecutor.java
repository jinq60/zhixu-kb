package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.note.entity.Category;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.CategoryMapper;
import com.zhixu.kb.note.mapper.NoteMapper;
import com.zhixu.kb.note.model.AIAnalysisResult;
import com.zhixu.kb.note.model.OutlineNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * AI 整理执行器：供同步接口与异步任务共用，
 * 避免 NoteService 与异步 Runner 之间产生循环依赖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisExecutor {

    private final NoteMapper noteMapper;
    private final CategoryMapper categoryMapper;
    private final DeepSeekAIService deepSeekAIService;
    private final NoteStructureService noteStructureService;
    private final NoteHistoryService noteHistoryService;

    private static final Pattern IMG_TAG_PATTERN = Pattern.compile("<img\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern HEADING_NUMBER_PATTERN = Pattern.compile("^(?:第[一二三四五六七八九十0-9]+[章节篇])|^(?:[（(]?[一二三四五六七八九十]+[）)]?[、.．\\s])|^(?:\\d+(?:\\.\\d+)*[.．、\\s])");
    private static final Pattern NUMBER_DEPTH_PATTERN = Pattern.compile("^(\\d+(?:\\.\\d+)*)");
    private static final int AI_REWRITE_MAX_CHARS = 6000;

    /**
     * 执行 AI 整理并更新笔记（事务由调用方保证）。
     * 除了摘要/关键词/分类/大纲，还会根据 AI 整理出的大纲重新排版正文。
     */
    @Transactional
    public AIAnalysisResult execute(Long userId, Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || !note.getUserId().equals(userId)
                || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        String contentToAnalyze = note.getContent();
        if (!StringUtils.hasText(contentToAnalyze)) {
            contentToAnalyze = note.getOcrText();
        }
        if (!StringUtils.hasText(contentToAnalyze)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Note content is empty, cannot run AI analysis");
        }

        AIAnalysisResult analysis = deepSeekAIService.analyzeNoteContent(note.getTitle(), contentToAnalyze);
        applyAIAnalysis(note, analysis, userId);
        List<OutlineNode> outline = analysis.getOutline() == null ? new ArrayList<>() : analysis.getOutline();
        if (!CollectionUtils.isEmpty(outline)) {
            String formattedContent = chooseFormattedContent(note.getContent(), outline);
            if (StringUtils.hasText(formattedContent)) {
                String preservedImages = extractImageTags(note.getContent());
                if (StringUtils.hasText(preservedImages)) {
                    formattedContent = formattedContent + "<h2>\u76f8\u5173\u56fe\u7247</h2>\n" + preservedImages;
                }
                note.setContent(formattedContent);
            }
        }
        noteMapper.updateById(note);
        if (!CollectionUtils.isEmpty(outline)) {
            noteStructureService.saveStructure(note.getId(), outline, null);
        }
        noteHistoryService.recordNoteSnapshot(
                note.getId(),
                "NOTE_AI_ANALYSIS",
                "Run AI full organization",
                "/api/notes/" + note.getId() + "/ai-analysis",
                analysis
        );

        return analysis;
    }

    private void applyAIAnalysis(Note note, AIAnalysisResult analysis, Long userId) {
        String suggestedCategory = normalizeCategoryName(analysis.getSuggestedCategory());
        if (suggestedCategory != null) {
            Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                    .eq(Category::getUserId, userId)
                    .eq(Category::getName, suggestedCategory)
                    .eq(Category::getIsDeleted, 0)
                    .last("LIMIT 1"));

            if (category == null) {
                category = new Category();
                category.setUserId(userId);
                category.setName(suggestedCategory);
                category.setDescription("AI generated category");
                category.setSortOrder(0);
                categoryMapper.insert(category);
            }

            note.setCategoryId(category.getId());
        }

        if (StringUtils.hasText(analysis.getSummary())) {
            note.setSummary(analysis.getSummary());
        }
        if (StringUtils.hasText(analysis.getKeywords())) {
            note.setKeywords(analysis.getKeywords());
        }
    }

    private String normalizeCategoryName(String rawCategoryName) {
        if (!StringUtils.hasText(rawCategoryName)) {
            return null;
        }
        return rawCategoryName.trim();
    }

    /**
     * 根据原文长度决定排版策略：
     * - 短篇（<=6000 字符）：允许 AI 重新生成正文，可读性更好。
     * - 长文（>6000 字符）：保留原文所有文字，仅根据大纲/启发式规则插入标题。
     */
    private String chooseFormattedContent(String originalHtml, List<OutlineNode> outline) {
        String plainSource = stripHtmlTags(originalHtml);
        if (plainSource.length() <= AI_REWRITE_MAX_CHARS) {
            return buildFormattedContent(outline);
        }
        return formatContentPreservingOriginal(originalHtml, outline);
    }

    /**
     * 将 AI 整理出的大纲转换为排版后的 HTML 正文。
     * 顶层章节使用 h2，逐级递减，正文按段落用 p 包裹。
     */
    private String buildFormattedContent(List<OutlineNode> outline) {
        if (CollectionUtils.isEmpty(outline)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (OutlineNode node : outline) {
            appendOutlineNodeAsHtml(sb, node, 1);
        }
        return sb.toString().trim();
    }

    private void appendOutlineNodeAsHtml(StringBuilder sb, OutlineNode node, int level) {
        if (node == null) {
            return;
        }
        String title = node.getTitle() == null ? "" : node.getTitle().trim();
        String content = node.getContent() == null ? "" : node.getContent();
        if (!StringUtils.hasText(title) && !StringUtils.hasText(content)) {
            return;
        }

        String tag = headingTagForLevel(level);
        if (StringUtils.hasText(title)) {
            sb.append("<").append(tag).append(">").append(escapeHtml(title)).append("</").append(tag).append(">\n");
        }
        appendContentParagraphs(sb, content);

        if (!CollectionUtils.isEmpty(node.getChildren())) {
            for (OutlineNode child : node.getChildren()) {
                appendOutlineNodeAsHtml(sb, child, level + 1);
            }
        }
    }

    private String headingTagForLevel(int level) {
        int clamped = Math.min(Math.max(level, 1), 4);
        if (clamped == 1) return "h2";
        if (clamped == 2) return "h3";
        if (clamped == 3) return "h4";
        return "h5";
    }

    private void appendContentParagraphs(StringBuilder sb, String content) {
        if (!StringUtils.hasText(content)) {
            return;
        }
        String[] paragraphs = content.replace("\r\n", "\n").split("\n");
        for (String paragraph : paragraphs) {
            String trimmed = paragraph.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            sb.append("<p>").append(escapeHtml(trimmed)).append("</p>\n");
        }
    }

    /**
     * 保留原文所有文字，仅根据 AI 大纲和启发式规则插入标题。
     * 用于长文档，避免 AI  truncation 导致内容丢失。
     */
    private String formatContentPreservingOriginal(String originalHtml, List<OutlineNode> outline) {
        String plain = stripHtmlTags(originalHtml);
        if (!StringUtils.hasText(plain)) {
            return "";
        }

        Map<String, Integer> titleLevelMap = new LinkedHashMap<>();
        collectOutlineTitles(outline, 1, titleLevelMap);
        Map<String, String> normalizedToOriginal = new HashMap<>();
        for (Map.Entry<String, Integer> entry : titleLevelMap.entrySet()) {
            String normalized = normalizeForHeadingMatch(entry.getKey());
            if (StringUtils.hasText(normalized)) {
                normalizedToOriginal.put(normalized, entry.getKey());
            }
        }

        String[] lines = plain.replace("\r\n", "\n").split("\n");
        StringBuilder html = new StringBuilder();
        List<String> paragraphBuffer = new ArrayList<>();

        for (String rawLine : lines) {
            String trimmed = rawLine.trim();
            if (!StringUtils.hasText(trimmed)) {
                flushParagraphBuffer(html, paragraphBuffer);
                continue;
            }

            String normalizedLine = normalizeForHeadingMatch(trimmed);
            if (normalizedToOriginal.containsKey(normalizedLine)) {
                flushParagraphBuffer(html, paragraphBuffer);
                String title = normalizedToOriginal.get(normalizedLine);
                if (title.length() <= 80) {
                    int level = titleLevelMap.getOrDefault(title, 1);
                    String tag = headingTagForLevel(level);
                    html.append("<").append(tag).append(">").append(escapeHtml(title)).append("</").append(tag).append(">\n");
                }
                continue;
            }

            if (looksLikeHeading(trimmed)) {
                flushParagraphBuffer(html, paragraphBuffer);
                int level = inferHeadingLevel(trimmed);
                String tag = headingTagForLevel(level);
                html.append("<").append(tag).append(">").append(escapeHtml(trimmed)).append("</").append(tag).append(">\n");
                continue;
            }

            paragraphBuffer.add(trimmed);
        }
        flushParagraphBuffer(html, paragraphBuffer);
        return html.toString().trim();
    }

    private void collectOutlineTitles(List<OutlineNode> outline, int level, Map<String, Integer> titleLevelMap) {
        if (outline == null) {
            return;
        }
        for (OutlineNode node : outline) {
            if (node == null) {
                continue;
            }
            String title = node.getTitle() == null ? "" : node.getTitle().trim();
            if (StringUtils.hasText(title)) {
                titleLevelMap.put(title, Math.min(Math.max(level, 1), 4));
            }
            collectOutlineTitles(node.getChildren(), level + 1, titleLevelMap);
        }
    }

    private String normalizeForHeadingMatch(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", "")
                .replaceAll("[\\p{P}\\p{S}]", "")
                .toLowerCase();
    }

    private boolean looksLikeHeading(String line) {
        if (!StringUtils.hasText(line) || line.length() > 60) {
            return false;
        }
        if (line.startsWith("#")) {
            return true;
        }
        if (HEADING_NUMBER_PATTERN.matcher(line).find()) {
            return true;
        }
        return line.matches("^第[一二三四五六七八九十0-9]+[章节篇].*");
    }

    private int inferHeadingLevel(String line) {
        if (line.startsWith("#")) {
            int level = 0;
            while (level < line.length() && line.charAt(level) == '#') {
                level++;
            }
            return Math.min(Math.max(level, 1), 4);
        }
        Matcher matcher = NUMBER_DEPTH_PATTERN.matcher(line);
        if (matcher.find()) {
            String number = matcher.group(1);
            int depth = number.split("\\.").length;
            return Math.min(Math.max(depth, 1), 4);
        }
        if (line.matches("^[（(]?[一二三四五六七八九十]+[）)]?[、.．].*")) {
            return 2;
        }
        if (line.matches("^第[一二三四五六七八九十0-9]+[章节篇].*")) {
            return 1;
        }
        return 2;
    }

    private void flushParagraphBuffer(StringBuilder html, List<String> buffer) {
        if (buffer.isEmpty()) {
            return;
        }
        String joined = buffer.stream()
                .map(this::escapeHtml)
                .collect(Collectors.joining("<br>"));
        html.append("<p>").append(joined).append("</p>\n");
        buffer.clear();
    }

    private String stripHtmlTags(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String plain = HTML_TAG_PATTERN.matcher(html).replaceAll("\n");
        plain = plain.replace("\\n", "\n")
                .replace("\\r", "\n")
                .replace("\\t", " ")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        return plain.replaceAll("\\n{2,}", "\n").trim();
    }

    /**
     * 保留原正文中已插入的图片标签，避免 AI 整理后图片丢失。
     */
    private String extractImageTags(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        Matcher matcher = IMG_TAG_PATTERN.matcher(html);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            sb.append("<p>").append(matcher.group()).append("</p>\n");
        }
        return sb.toString().trim();
    }

    private String escapeHtml(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
