package com.zhixu.kb.note.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhixu.kb.ai.AIEngineAdapterRouter;
import com.zhixu.kb.ai.AiTextCleaner;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.note.model.AIAnalysisResult;
import com.zhixu.kb.note.model.OutlineNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeepSeekAIService {

    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    private final com.zhixu.kb.ai.AIEngineAdapterRouter adapterRouter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AIAnalysisResult analyzeNoteContent(String title, String content) {
        String plainContent = truncate(cleanSourceForPrompt(stripHtml(content)));
        if (!StringUtils.hasText(plainContent)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Note content is empty");
        }

        try {
            String response = callDeepSeekAPI(buildFullOrganizePrompt(title, plainContent));
            AIAnalysisResult result = parseOrganizeResult(response);
            if (CollectionUtils.isEmpty(result.getOutline())) {
                result.setOutline(buildHeuristicOutline(title, plainContent));
            }
            fillFallbackFields(result, plainContent);
            return result;
        } catch (Exception e) {
            // 主流程失败后直接本地规则降级，避免多次串行调用 AI（本地模型单次可达数分钟）
            log.warn("Full AI organization failed, fallback to local rules", e);
            AIAnalysisResult fallback = new AIAnalysisResult();
            fallback.setTags(new ArrayList<>());
            fallback.setSuggestedCategory("");
            fallback.setOutline(buildHeuristicOutline(title, plainContent));
            fillFallbackFields(fallback, plainContent);
            return fallback;
        }
    }

    public AIAnalysisResult analyzeNoteMetadata(String title, String content) {
        String plainContent = truncate(cleanSourceForPrompt(stripHtml(content)));
        if (!StringUtils.hasText(plainContent)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Note content is empty");
        }

        String response = callDeepSeekAPI(buildMetadataPrompt(title, plainContent));
        AIAnalysisResult result = parseMetadataResult(response);
        fillFallbackFields(result, plainContent);
        result.setOutline(new ArrayList<>());
        return result;
    }

    public List<OutlineNode> generateOutline(String title, String content) {
        String plainContent = truncate(cleanSourceForPrompt(stripHtml(content)));
        if (!StringUtils.hasText(plainContent)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Note content is empty");
        }

        try {
            String response = callDeepSeekAPI(buildOutlinePrompt(title, plainContent));
            List<OutlineNode> outline = parseOutlineResult(response);
            if (outline.isEmpty()) {
                return buildHeuristicOutline(title, plainContent);
            }
            return outline;
        } catch (Exception e) {
            log.warn("Generate outline by AI failed, fallback to heuristic outline", e);
            return buildHeuristicOutline(title, plainContent);
        }
    }

    private void fillFallbackFields(AIAnalysisResult result, String plainContent) {
        if (result.getTags() == null) {
            result.setTags(new ArrayList<>());
        }
        if (!StringUtils.hasText(result.getSummary())) {
            result.setSummary(buildFallbackSummary(result.getOutline(), plainContent));
        }
        if (!StringUtils.hasText(result.getKeywords())) {
            result.setKeywords(buildFallbackKeywords(result.getOutline(), result.getTags(), plainContent));
        }
        if (result.getOutline() == null) {
            result.setOutline(new ArrayList<>());
        }
        if (result.getSuggestedCategory() == null) {
            result.setSuggestedCategory("");
        }
    }

    private String buildFullOrganizePrompt(String title, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个学习笔记整理助手，请把笔记整理为结构清晰、表述自然的内容。\n");
        sb.append("只返回 JSON，不要返回 markdown 代码块，不要输出额外说明。\n");
        sb.append("严格保持与原文主体语言一致，不要把中文翻译成英文，也不要把英文翻译成中文。\n");
        sb.append("不要凭空捏造原文没有的信息，要尽量保留原意，并把零散或 OCR 断裂文本整理通顺。\n");
        sb.append("大纲必须与整理后的正文一致。\n\n");
        sb.append("严格按下面 JSON 结构返回：\n");
        sb.append("{\n");
        sb.append("  \"suggestedCategory\": \"与原文语言一致的分类名\",\n");
        sb.append("  \"tags\": [\"标签1\", \"标签2\"],\n");
        sb.append("  \"summary\": \"与原文语言一致的摘要\",\n");
        sb.append("  \"keywords\": \"关键词1,关键词2,关键词3\",\n");
        sb.append("  \"outline\": [\n");
        sb.append("    {\n");
        sb.append("      \"title\": \"与原文语言一致的章节标题\",\n");
        sb.append("      \"content\": \"该章节整理后的正文\",\n");
        sb.append("      \"children\": [\n");
        sb.append("        {\n");
        sb.append("          \"title\": \"子章节标题\",\n");
        sb.append("          \"content\": \"该子章节整理后的正文\",\n");
        sb.append("          \"children\": []\n");
        sb.append("        }\n");
        sb.append("      ]\n");
        sb.append("    }\n");
        sb.append("  ]\n");
        sb.append("}\n\n");
        sb.append("要求：\n");
        sb.append("- 一级章节控制在 4 到 10 个，用 2 到 3 级层级组织，不要每行一个节点。\n");
        sb.append("- 章节标题精炼，不超过 20 个字，不要带编号或 Markdown 标记。\n");
        sb.append("- content 字段只放纯文本，不要写 HTML 或 markdown；多个段落之间用空行分隔。\n");
        sb.append("- 把重复、碎片化、断句异常的文本整理成自然、连贯的段落，保留关键细节与专业术语。\n");
        sb.append("- 不要省略、压缩或删减原文中的实质性信息；整理后的正文应尽可能包含原文全部要点。\n");
        sb.append("- 正文、标题、摘要、关键词、分类建议都必须保持与原文主语言一致。\n");
        sb.append("- 如果原文中出现专有名词或技术术语，可以保留原文写法。\n");
        sb.append("- 每个章节的内容应完整、有信息量，不要只列一句话要点。\n");
        sb.append("- 如果原文很长，请分段落完整呈现，不要一笔带过。\n\n");
        sb.append("笔记标题：").append(StringUtils.hasText(title) ? title : "未命名笔记").append("\n");
        sb.append("笔记内容：\n").append(content);
        return sb.toString();
    }

    private String buildMetadataPrompt(String title, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("请分析下面的学习笔记，并只返回 JSON。\n");
        sb.append("不要返回 markdown 代码块，不要输出任何解释。\n");
        sb.append("分类、标签、摘要、关键词必须保持与原文主语言一致，不要翻译原文。\n\n");
        sb.append("{\n");
        sb.append("  \"suggestedCategory\": \"与原文语言一致的分类名\",\n");
        sb.append("  \"tags\": [\"标签1\", \"标签2\"],\n");
        sb.append("  \"summary\": \"与原文语言一致的摘要\",\n");
        sb.append("  \"keywords\": \"关键词1,关键词2,关键词3\"\n");
        sb.append("}\n\n");
        sb.append("笔记标题：").append(StringUtils.hasText(title) ? title : "未命名笔记").append("\n");
        sb.append("笔记内容：\n").append(content);
        return sb.toString();
    }

    private String buildOutlinePrompt(String title, String content) {
        StringBuilder sb = new StringBuilder();
        sb.append("请将下面的学习笔记整理成章节大纲。\n");
        sb.append("只返回 JSON，不要返回 markdown 代码块。\n");
        sb.append("每个节点都必须包含 title、content、children，其中 children 必须始终是数组。\n");
        sb.append("章节标题和内容必须保持与原文主语言一致，不要翻译原文。\n\n");
        sb.append("示例：\n");
        sb.append("[\n");
        sb.append("  {\n");
        sb.append("    \"title\": \"章节标题\",\n");
        sb.append("    \"content\": \"这一章节的概要\",\n");
        sb.append("    \"children\": [\n");
        sb.append("      {\n");
        sb.append("        \"title\": \"子章节标题\",\n");
        sb.append("        \"content\": \"子章节要点\",\n");
        sb.append("        \"children\": []\n");
        sb.append("      }\n");
        sb.append("    ]\n");
        sb.append("  }\n");
        sb.append("]\n\n");
        sb.append("要求：\n");
        sb.append("- 保持与原文主语言一致，不要翻译。\n");
        sb.append("- 按语义组织，一级章节 3 到 8 个，可用 2 级子章节，不要每一行都成为一个节点。\n");
        sb.append("- 标题精炼，不超过 20 个字，不要带编号或 Markdown 标记。\n");
        sb.append("- content 用自然语言概括该章节，不要写 HTML。\n\n");
        sb.append("笔记标题：").append(StringUtils.hasText(title) ? title : "未命名笔记").append("\n");
        sb.append("笔记内容：\n").append(content);
        return sb.toString();
    }

    /**
     * 调用 AI 引擎（用户自配 Key → 平台默认云端 API）：
     * 统一走 AIEngineAdapterRouter，与问答/文档清洗/图谱抽取一致。
     * 模型不可用或返回 fallback 时抛异常，由调用方降级为规则处理。
     */
    private String callDeepSeekAPI(String prompt) {
        String response = adapterRouter.generateResponseResilient(prompt, Collections.singletonMap("max_tokens", 8192));
        if (!StringUtils.hasText(response) || looksLikeFallback(response)) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "AI engine unavailable");
        }
        // 剥离模型思考链（<think>...</think>）
        return AiTextCleaner.clean(response);
    }

    private boolean looksLikeFallback(String result) {
        if (result == null) {
            return true;
        }
        String normalized = result.toLowerCase();
        return normalized.contains("暂时无法调用外部模型")
                || normalized.contains("本地模型网关当前不可用")
                || normalized.contains("本地模型网关暂不可用")
                || normalized.contains("当前未配置外部api key")
                || normalized.contains("请稍后重试");
    }

    private AIAnalysisResult parseOrganizeResult(String response) {
        try {
            String jsonStr = cleanupJsonResponse(response);
            JsonNode root = objectMapper.readTree(jsonStr);
            AIAnalysisResult result = parseMetadataFromRoot(root);
            result.setOutline(parseOutlineFromRoot(root));
            return result;
        } catch (Exception e) {
            log.error("Parse AI organize result failed: {}", response, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "Parse AI organize result failed");
        }
    }

    private AIAnalysisResult parseMetadataResult(String response) {
        try {
            String jsonStr = cleanupJsonResponse(response);
            JsonNode root = objectMapper.readTree(jsonStr);
            AIAnalysisResult result = parseMetadataFromRoot(root);
            result.setOutline(new ArrayList<>());
            return result;
        } catch (Exception e) {
            log.error("Parse AI metadata result failed: {}", response, e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "Parse AI metadata result failed");
        }
    }

    private AIAnalysisResult parseMetadataFromRoot(JsonNode root) {
        AIAnalysisResult result = new AIAnalysisResult();
        result.setSuggestedCategory(root.path("suggestedCategory").asText(""));

        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = root.path("tags");
        if (tagsNode.isArray()) {
            tagsNode.forEach(tag -> {
                String value = tag.asText("").trim();
                if (StringUtils.hasText(value)) {
                    tags.add(value);
                }
            });
        }
        result.setTags(tags);
        result.setSummary(root.path("summary").asText(""));
        result.setKeywords(root.path("keywords").asText(""));
        return result;
    }

    private List<OutlineNode> parseOutlineResult(String response) {
        try {
            String jsonStr = cleanupJsonResponse(response);
            JsonNode root = objectMapper.readTree(jsonStr);
            return parseOutlineFromRoot(root);
        } catch (Exception e) {
            log.error("Parse outline result failed: {}", response, e);
            return Collections.emptyList();
        }
    }

    private List<OutlineNode> parseOutlineFromRoot(JsonNode root) throws Exception {
        JsonNode outlineNode = root;
        if (root.isObject() && root.has("outline")) {
            outlineNode = root.get("outline");
        }

        if (!outlineNode.isArray()) {
            return Collections.emptyList();
        }

        JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, OutlineNode.class);
        List<OutlineNode> outline = objectMapper.readValue(outlineNode.toString(), type);
        return normalizeOutline(outline);
    }

    private List<OutlineNode> normalizeOutline(List<OutlineNode> outline) {
        if (outline == null) {
            return new ArrayList<>();
        }

        List<OutlineNode> normalized = new ArrayList<>();
        for (OutlineNode node : outline) {
            if (node == null) {
                continue;
            }
            String title = node.getTitle() == null ? "" : node.getTitle().trim();
            String content = node.getContent() == null ? "" : node.getContent().trim();
            List<OutlineNode> children = normalizeOutline(node.getChildren());

            // 过滤分隔线/代码块等产生的空节点
            if (isPlaceholderTitle(title) && !StringUtils.hasText(content) && children.isEmpty()) {
                continue;
            }
            if (!StringUtils.hasText(title) && !StringUtils.hasText(content) && children.isEmpty()) {
                continue;
            }

            OutlineNode normalizedNode = new OutlineNode();
            String cleanTitle = cleanMarkdownTitle(StringUtils.hasText(title)
                    ? title
                    : buildTitleFromParagraph(content, normalized.size() + 1));
            if (isPlaceholderTitle(cleanTitle) && StringUtils.hasText(content)) {
                cleanTitle = buildTitleFromParagraph(content, normalized.size() + 1);
            }
            normalizedNode.setTitle(cleanTitle);
            normalizedNode.setContent(content);
            normalizedNode.setChildren(children);
            normalized.add(normalizedNode);
        }
        return normalized;
    }

    private boolean isPlaceholderTitle(String title) {
        return "未命名章节".equals(title) || "未命名节点".equals(title);
    }

    /**
     * 清洗大纲标题中的 Markdown 标记，避免进入 mermaid 导图后解析失败：
     * 标题标记（#）、列表（- 1.）、加粗/斜体（** * _）、行内代码（`）、
     * 引用（>）、分隔线（---）与代码块标记（```）。
     */
    private String cleanMarkdownTitle(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未命名章节";
        }
        String text = raw.trim();
        // 代码块标记
        if (text.matches("^`{3,}.*")) {
            return "未命名章节";
        }
        // 分隔线
        if (text.matches("^[-*_]{3,}$")) {
            return "未命名章节";
        }
        // 标题 / 列表 / 引用标记
        text = text.replaceFirst("^#{1,6}\\s*", "");
        text = text.replaceFirst("^\\s*[-*+]\\s+", "");
        text = text.replaceFirst("^\\s*\\d+[.、．)）]\\s*", "");
        text = text.replaceFirst("^\\s*>\\s*", "");
        // 加粗 / 斜体 / 行内代码
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("\\*(.+?)\\*", "$1");
        text = text.replaceAll("__(.+?)__", "$1");
        text = text.replaceAll("_(.+?)_", "$1");
        text = text.replaceAll("`(.+?)`", "$1");
        // 清理残留标记字符
        text = text.replace("*", "").replace("`", "").replace("#", "");
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() > 40) {
            text = text.substring(0, 40) + "…";
        }
        return StringUtils.hasText(text) ? text : "未命名章节";
    }

    private String cleanupJsonResponse(String response) {
        String jsonStr = response == null ? "" : response.trim();
        // 剥离 markdown 代码块包裹
        if (jsonStr.startsWith("```json")) {
            jsonStr = jsonStr.substring(7);
        }
        if (jsonStr.startsWith("```")) {
            jsonStr = jsonStr.substring(3);
        }
        if (jsonStr.endsWith("```")) {
            jsonStr = jsonStr.substring(0, jsonStr.length() - 3);
        }
        jsonStr = jsonStr.trim();
        // 模型可能输出解释性文字，提取其中的 JSON 主体
        int objStart = jsonStr.indexOf('{');
        int objEnd = jsonStr.lastIndexOf('}');
        if (objStart >= 0 && objEnd > objStart) {
            return jsonStr.substring(objStart, objEnd + 1);
        }
        int arrStart = jsonStr.indexOf('[');
        int arrEnd = jsonStr.lastIndexOf(']');
        if (arrStart >= 0 && arrEnd > arrStart) {
            return jsonStr.substring(arrStart, arrEnd + 1);
        }
        return jsonStr;
    }

    private List<OutlineNode> buildHeuristicOutline(String title, String content) {
        String plainText = stripHtml(content);
        String[] lines = plainText.split("\\r?\\n");
        List<String> paragraphs = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            paragraphs.add(trimmed);
        }

        if (paragraphs.isEmpty()) {
            OutlineNode node = new OutlineNode();
            node.setTitle(StringUtils.hasText(title) ? title : "学习笔记");
            node.setContent("暂无可整理内容");
            return Collections.singletonList(node);
        }

        List<OutlineNode> outline = new ArrayList<>();
        OutlineNode current = null;
        for (String paragraph : paragraphs) {
            if (looksLikeHeading(paragraph)) {
                current = new OutlineNode();
                current.setTitle(cleanMarkdownTitle(paragraph));
                current.setContent("");
                outline.add(current);
                continue;
            }

            if (current == null) {
                current = new OutlineNode();
                current.setTitle(buildTitleFromParagraph(paragraph, outline.size() + 1));
                current.setContent(paragraph);
                outline.add(current);
                continue;
            }

            String merged = StringUtils.hasText(current.getContent())
                    ? current.getContent() + "\n" + paragraph
                    : paragraph;
            current.setContent(merged);
        }

        return outline;
    }

    private boolean looksLikeHeading(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        if (text.length() <= 18 && !text.contains(" ")) {
            return true;
        }
        return text.matches("^[一二三四五六七八九十0-9]+[、.．）)].*")
                || text.startsWith("#")
                || text.endsWith(":")
                || text.endsWith("：");
    }

    private String buildTitleFromParagraph(String paragraph, int index) {
        String compact = paragraph == null ? "" : paragraph.replaceAll("\\s+", " ").trim();
        if (compact.length() > 16) {
            compact = compact.substring(0, 16) + "...";
        }
        if (!StringUtils.hasText(compact)) {
            compact = "未命名章节";
        }
        return "第" + index + "部分：" + compact;
    }

    private String buildFallbackSummary(List<OutlineNode> outline, String plainContent) {
        String source = firstNonEmptyContent(outline);
        if (!StringUtils.hasText(source)) {
            source = plainContent;
        }
        source = source == null ? "" : source.replaceAll("\\s+", " ").trim();
        if (source.length() > 120) {
            return source.substring(0, 120);
        }
        return source;
    }

    private String buildFallbackKeywords(List<OutlineNode> outline, List<String> tags, String plainContent) {
        Set<String> values = new LinkedHashSet<>();
        if (tags != null) {
            for (String tag : tags) {
                if (StringUtils.hasText(tag)) {
                    values.add(tag.trim());
                }
            }
        }
        collectOutlineTitles(outline, values);
        if (values.isEmpty() && StringUtils.hasText(plainContent)) {
            String compact = plainContent.replaceAll("\\s+", " ").trim();
            for (String token : compact.split("[，。；、,\\s]+")) {
                if (token.length() >= 2) {
                    values.add(token);
                }
                if (values.size() >= 5) {
                    break;
                }
            }
        }
        return String.join(",", values.stream().limit(5).collect(Collectors.toList()));
    }

    private void collectOutlineTitles(List<OutlineNode> outline, Set<String> values) {
        if (outline == null) {
            return;
        }
        for (OutlineNode node : outline) {
            if (node == null) {
                continue;
            }
            if (StringUtils.hasText(node.getTitle())) {
                values.add(node.getTitle().trim());
            }
            if (values.size() >= 5) {
                return;
            }
            collectOutlineTitles(node.getChildren(), values);
            if (values.size() >= 5) {
                return;
            }
        }
    }

    private String firstNonEmptyContent(List<OutlineNode> outline) {
        if (outline == null) {
            return "";
        }
        for (OutlineNode node : outline) {
            if (node == null) {
                continue;
            }
            if (StringUtils.hasText(node.getContent())) {
                return node.getContent();
            }
            String child = firstNonEmptyContent(node.getChildren());
            if (StringUtils.hasText(child)) {
                return child;
            }
        }
        return "";
    }

    private String stripHtml(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        String plain = HTML_TAG_PATTERN.matcher(content).replaceAll("\n");
        plain = plain.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&");
        return plain.replaceAll("\\n{2,}", "\n").trim();
    }

    /**
     * 清洗送入 AI 的源文本（轻量去 Markdown 标记）：
     * 避免 AI 把格式行（#、---、列表、代码块）当作内容节点，
     * 只做符号级处理，不改写内容。
     */
    /** 送入 AI 的内容上限：截断超长文本，控制生成延迟 */
    private static final int MAX_AI_INPUT_CHARS = 60000;

    private String truncate(String text) {
        if (text == null || text.length() <= MAX_AI_INPUT_CHARS) {
            return text;
        }
        return text.substring(0, MAX_AI_INPUT_CHARS);
    }

    private String cleanSourceForPrompt(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String cleaned = text.replace("\r\n", "\n").replace('\r', '\n');
        cleaned = CODE_BLOCK_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = SEPARATOR_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = HEADING_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = LIST_MARKER_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = QUOTE_PATTERN.matcher(cleaned).replaceAll("");
        cleaned = BOLD_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = ITALIC_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = INLINE_CODE_PATTERN.matcher(cleaned).replaceAll("$1");
        cleaned = cleaned.replace("*", "").replace("`", "").replace("#", "");
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        cleaned = cleaned.replace("\n ", "\n").replace(" \n", "\n");
        cleaned = MULTI_BLANK_PATTERN.matcher(cleaned).replaceAll("\n\n");
        return cleaned.trim();
    }

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("(?s)```[a-zA-Z0-9]*\\s*\\n?.*?```");
    private static final Pattern SEPARATOR_PATTERN = Pattern.compile("(?m)^\\s*[-*_]{3,}\\s*$");
    private static final Pattern HEADING_PATTERN = Pattern.compile("(?m)^#{1,6}\\s*");
    private static final Pattern LIST_MARKER_PATTERN = Pattern.compile("(?m)^\\s*(?:[-*+]|\\d+[.、．)）])\\s+");
    private static final Pattern QUOTE_PATTERN = Pattern.compile("(?m)^\\s*>\\s*");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("\\*(.+?)\\*");
    private static final Pattern INLINE_CODE_PATTERN = Pattern.compile("`(.+?)`");
    private static final Pattern MULTI_BLANK_PATTERN = Pattern.compile("\\n{3,}");
}
