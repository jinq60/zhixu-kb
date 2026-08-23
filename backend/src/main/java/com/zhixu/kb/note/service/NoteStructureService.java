package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.entity.NoteMindmap;
import com.zhixu.kb.note.entity.NoteSection;
import com.zhixu.kb.note.entity.NoteStructure;
import com.zhixu.kb.note.mapper.NoteMapper;
import com.zhixu.kb.note.mapper.NoteMindmapMapper;
import com.zhixu.kb.note.mapper.NoteSectionMapper;
import com.zhixu.kb.note.mapper.NoteStructureMapper;
import com.zhixu.kb.note.model.NoteStructureResponse;
import com.zhixu.kb.note.model.OutlineNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteStructureService {

    private final NoteMapper noteMapper;
    private final NoteStructureMapper noteStructureMapper;
    private final NoteMindmapMapper noteMindmapMapper;
    private final NoteSectionMapper noteSectionMapper;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public NoteStructureResponse getStructure(Long noteId) {
        Note note = findOwnNote(noteId);
        return buildStructureResponse(note);
    }

    public NoteStructureResponse getReadableStructure(Long noteId) {
        Note note = findReadableNote(noteId);
        return buildStructureResponse(note);
    }

private NoteStructureResponse buildStructureResponse(Note note) {
        Long noteId = note.getId();
        NoteStructure structure = findStructure(noteId);
        NoteMindmap mindmap = findMindmap(noteId);
        List<OutlineNode> outline = parseOutlineJson(structure == null ? null : structure.getOutlineJson());
        List<NoteSection> sections = loadSectionsInTreeOrder(noteId);

        // 无 AI/启发式生成的存储结构时，用正文自身的标题层级（h2-h5）镜像出目录：
        // 目录与正文所见一致，且不依赖 AI 可用性（只读兜底，不落库，用户可点“保存”固化）
        boolean contentMirror = (outline == null || outline.isEmpty()) && (sections == null || sections.isEmpty());
        if (contentMirror) {
            outline = extractOutlineFromContent(note.getContent());
            contentMirror = outline != null && !outline.isEmpty();
        }

        String mermaid = !outline.isEmpty()
                ? buildMindmapMermaid(note.getTitle(), outline)
                : (mindmap != null ? mindmap.getMapData() : buildMindmapMermaid(note.getTitle(), outline));
        LocalDateTime updateTime = structure != null ? structure.getUpdateTime() : null;
        if (contentMirror) {
            sections = flattenOutlineToSections(outline);
        }
        return new NoteStructureResponse(outline, sections, mermaid, updateTime);
    }

    /**
     * 从正文 HTML 提取标题层级作为目录：h2→一级、h3→二级、h4→三级、h5+→四级。
     * AI 整理重排正文时使用大纲标题生成 h 标签，因此该镜像目录与正文天然一致。
     */
    public List<OutlineNode> extractOutlineFromContent(String html) {
        List<OutlineNode> roots = new ArrayList<>();
        if (!StringUtils.hasText(html)) {
            return roots;
        }
        Matcher matcher = HEADING_PATTERN.matcher(html);
        List<OutlineNode> ancestors = new ArrayList<>();
        while (matcher.find()) {
            int tag = Integer.parseInt(matcher.group(1));
            String title = stripTags(matcher.group(2)).trim();
            if (!StringUtils.hasText(title)) {
                continue;
            }
            int depth = Math.max(1, Math.min(4, tag - 1));
            while (ancestors.size() >= depth) {
                ancestors.remove(ancestors.size() - 1);
            }
            OutlineNode node = new OutlineNode();
            node.setTitle(title.length() > 40 ? title.substring(0, 40) : title);
            node.setContent("");
            node.setChildren(new ArrayList<>());
            if (ancestors.isEmpty()) {
                roots.add(node);
            } else {
                ancestors.get(ancestors.size() - 1).getChildren().add(node);
            }
            ancestors.add(node);
        }
        return roots;
    }

    private List<NoteSection> flattenOutlineToSections(List<OutlineNode> outline) {
        List<NoteSection> flat = new ArrayList<>();
        int[] order = {0};
        appendSectionFlat(flat, outline, 1, order);
        return flat;
    }

    private void appendSectionFlat(List<NoteSection> flat, List<OutlineNode> outline, int level, int[] order) {
        for (OutlineNode node : outline) {
            if (node == null || !StringUtils.hasText(node.getTitle())) {
                continue;
            }
            NoteSection section = new NoteSection();
            section.setTitle(node.getTitle());
            section.setContent(node.getContent());
            section.setLevel(level);
            section.setSortOrder(order[0]++);
            flat.add(section);
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                appendSectionFlat(flat, node.getChildren(), level + 1, order);
            }
        }
    }

    private String stripTags(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String text = html.replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        // 标题来自 HTML 源码，可能含 &middot; / &mdash; 等实体，需解码后再落库，
        // 否则前端渲染后的 heading text 与目录标题无法按文本匹配。
        return HtmlUtils.htmlUnescape(text);
    }

    /**
     * 大纲生成：从正文标题层级提取，与清洗/阅读页目录保持一致，不再调用 AI。
     * 提取在事务外执行，保存使用短事务，避免请求线程与数据库连接被长时间占用。
     */
    public NoteStructureResponse generateStructure(Long noteId) {
        Note note = findOwnNote(noteId);
        String source = StringUtils.hasText(note.getContent()) ? note.getContent() : note.getOcrText();
        if (!StringUtils.hasText(source)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Note content is empty, cannot generate outline");
        }

        List<OutlineNode> outline = extractOutlineFromContent(source);
        if (outline.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "正文中未找到可用标题，无法生成目录");
        }
        String mermaid = buildMindmapMermaid(note.getTitle(), outline);
        return transactionTemplate.execute(status -> {
            saveStructure(noteId, outline, mermaid);
            return getStructure(noteId);
        });
    }

    @Transactional
    public void saveStructure(Long noteId, String outlineJson, String mermaid) {
        List<OutlineNode> outline = parseOutlineJson(outlineJson);
        saveStructure(noteId, outline, mermaid);
    }

    @Transactional
    public void saveStructure(Long noteId, List<OutlineNode> outline, String mermaid) {
        Note note = findOwnNote(noteId);
        List<OutlineNode> sanitized = sanitizeOutlineForStructure(
                outline == null ? Collections.emptyList() : outline, 1, new int[]{STRUCTURE_MAX_NODES});
        String outlineJson = writeOutlineJson(sanitized);
        String finalMermaid = StringUtils.hasText(mermaid)
                ? mermaid
                : buildMindmapMermaid(note.getTitle(), sanitized);
        upsertStructure(noteId, outlineJson);
        upsertMindmap(noteId, finalMermaid, note.getTitle());
        rebuildSections(noteId, sanitized);
    }

    /** 目录（大纲/章节）可读性上限 */
    private static final int STRUCTURE_MAX_DEPTH = 4;
    private static final int STRUCTURE_MAX_CHILDREN = 10;
    private static final int STRUCTURE_MAX_NODES = 60;

    /**
     * 保存前净化大纲，消除 AI/启发式生成的“没必要”的目录节点：
     * 1) 空标题节点；2) 占位标题（未命名章节/节点）且无内容无子节点；3) 与上一个兄弟标题重复的节点；
     * 4) 深度 > STRUCTURE_MAX_DEPTH；5) 每层子节点 > STRUCTURE_MAX_CHILDREN；6) 总节点 > STRUCTURE_MAX_NODES。
     * 注意：正文内容由调用方（AI 整理）基于完整大纲生成，此处只影响目录/导图/章节导航。
     */
    private List<OutlineNode> sanitizeOutlineForStructure(List<OutlineNode> outline, int depth, int[] nodeBudget) {
        if (outline == null || outline.isEmpty() || depth > STRUCTURE_MAX_DEPTH) {
            return new ArrayList<>();
        }
        List<OutlineNode> result = new ArrayList<>();
        String previousTitle = null;
        for (OutlineNode node : outline) {
            if (node == null || nodeBudget[0] <= 0) {
                break;
            }
            String title = node.getTitle() == null ? "" : node.getTitle().trim();
            boolean placeholder = (isPlaceholderTitle(title))
                    && !StringUtils.hasText(node.getContent())
                    && (node.getChildren() == null || node.getChildren().isEmpty());
            boolean duplicate = previousTitle != null && title.equals(previousTitle);
            if (!StringUtils.hasText(title) || placeholder || duplicate) {
                continue;
            }

            OutlineNode cleaned = new OutlineNode();
            cleaned.setTitle(title.length() > 40 ? title.substring(0, 40) : title);
            cleaned.setContent(node.getContent());
            nodeBudget[0]--;

            List<OutlineNode> children = node.getChildren() == null ? Collections.emptyList() : node.getChildren();
            if (depth < STRUCTURE_MAX_DEPTH && !children.isEmpty()) {
                List<OutlineNode> capped = children.size() > STRUCTURE_MAX_CHILDREN
                        ? children.subList(0, STRUCTURE_MAX_CHILDREN)
                        : children;
                cleaned.setChildren(sanitizeOutlineForStructure(capped, depth + 1, nodeBudget));
            } else {
                cleaned.setChildren(new ArrayList<>());
            }

            result.add(cleaned);
            previousTitle = title;
        }
        return result;
    }

    private boolean isPlaceholderTitle(String title) {
        return "未命名章节".equals(title) || "未命名节点".equals(title);
    }

    @Transactional
    public NoteStructureResponse updateStructure(Long noteId, List<OutlineNode> outline) {
        saveStructure(noteId, outline, null);
        return getStructure(noteId);
    }

    @Transactional
    public void clearStructure(Long noteId) {
        findOwnNote(noteId);
        noteSectionMapper.delete(new LambdaUpdateWrapper<NoteSection>()
                .eq(NoteSection::getNoteId, noteId));
        noteMindmapMapper.delete(new LambdaUpdateWrapper<NoteMindmap>()
                .eq(NoteMindmap::getNoteId, noteId));
        noteStructureMapper.delete(new LambdaUpdateWrapper<NoteStructure>()
                .eq(NoteStructure::getNoteId, noteId));
    }

    /** 思维导图可读性上限：深度、每节点子节点数、总节点数（节点少时完整展示，多时才压缩） */
    private static final int MINDMAP_MAX_DEPTH = 4;
    private static final int MINDMAP_MAX_CHILDREN_PER_NODE = 10;
    private static final int MINDMAP_MAX_NODES = 60;

    /** 正文标题（h1-h6）提取模式，用于“正文镜像目录”兜底 */
    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "<h([1-6])[^>]*>(.*?)</h\\1>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    public String buildMindmapMermaid(String title, List<OutlineNode> outline) {
        String rootTitle = sanitizeMermaidLabel(StringUtils.hasText(title) ? title : "学习笔记");
        StringBuilder sb = new StringBuilder("mindmap\n");
        sb.append("  root[\"").append(rootTitle).append("\"]\n");
        int[] sequence = {0};
        // 总节点预算：root 占 1，剩余给大纲节点
        int[] nodeBudget = {MINDMAP_MAX_NODES - 1};
        for (OutlineNode node : outline == null ? Collections.<OutlineNode>emptyList() : outline) {
            if (nodeBudget[0] <= 0) {
                break;
            }
            appendMindmapNode(sb, node, 2, sequence, nodeBudget);
        }
        return sb.toString();
    }

    private void appendMindmapNode(StringBuilder sb, OutlineNode node, int depth, int[] sequence, int[] nodeBudget) {
        if (node == null || !StringUtils.hasText(node.getTitle())) {
            return;
        }
        if (nodeBudget[0] <= 0) {
            appendMorePlaceholder(sb, depth, sequence);
            return;
        }
        nodeBudget[0]--;
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        String nodeId = "node" + (++sequence[0]);
        sb.append(nodeId)
                .append("[\"")
                .append(sanitizeMermaidLabel(node.getTitle()))
                .append("\"]\n");

        if (node.getChildren() == null || depth >= MINDMAP_MAX_DEPTH) {
            return;
        }
        int appendedChildren = 0;
        boolean truncated = false;
        for (OutlineNode child : node.getChildren()) {
            if (appendedChildren >= MINDMAP_MAX_CHILDREN_PER_NODE || nodeBudget[0] <= 0) {
                truncated = true;
                break;
            }
            appendMindmapNode(sb, child, depth + 1, sequence, nodeBudget);
            appendedChildren++;
        }
        // 子节点被截断时追加省略提示节点
        if (truncated) {
            appendMorePlaceholder(sb, depth + 1, sequence);
        }
    }

    private void appendMorePlaceholder(StringBuilder sb, int depth, int[] sequence) {
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        String nodeId = "node" + (++sequence[0]);
        sb.append(nodeId).append("[\"…\"]\n");
    }

    private String sanitizeMermaidLabel(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "未命名节点";
        }
        String text = raw.replace("\n", " ")
                .replace("\r", " ")
                .replace("\"", "'")
                .replace("\\", "/")
                .trim();
        // Markdown / mermaid 特殊字符清洗：防止导图解析失败
        text = text.replaceFirst("^#{1,6}\\s*", "");
        text = text.replaceFirst("^\\s*[-*+]\\s+", "");
        text = text.replaceFirst("^\\s*\\d+[.、．)）]\\s*", "");
        text = text.replaceAll("\\*\\*(.+?)\\*\\*", "$1");
        text = text.replaceAll("\\*(.+?)\\*", "$1");
        text = text.replaceAll("`(.+?)`", "$1");
        text = text.replaceAll("[\\[\\](){}<>|&_*#`]", " ");
        if (text.matches("^[-_*]{2,}$")) {
            return "未命名节点";
        }
        text = text.replaceAll("\\s+", " ").trim();
        if (text.length() > 60) {
            text = text.substring(0, 60) + "…";
        }
        return StringUtils.hasText(text) ? text : "未命名节点";
    }

    private List<NoteSection> loadSectionsInTreeOrder(Long noteId) {
        List<NoteSection> sections = noteSectionMapper.selectList(new LambdaQueryWrapper<NoteSection>()
                .eq(NoteSection::getNoteId, noteId)
                .eq(NoteSection::getIsDeleted, 0)
                .orderByAsc(NoteSection::getSortOrder)
                .orderByAsc(NoteSection::getId));
        if (sections.isEmpty()) {
            return sections;
        }

        Map<Long, List<NoteSection>> childrenByParent = new LinkedHashMap<>();
        List<NoteSection> roots = new ArrayList<>();
        for (NoteSection section : sections) {
            childrenByParent.computeIfAbsent(section.getParentId(), key -> new ArrayList<>()).add(section);
            if (section.getParentId() == null) {
                roots.add(section);
            }
        }

        List<NoteSection> ordered = new ArrayList<>();
        appendSectionsInTreeOrder(ordered, roots, childrenByParent);
        return ordered;
    }

    private void appendSectionsInTreeOrder(List<NoteSection> ordered,
                                           List<NoteSection> currentLevel,
                                           Map<Long, List<NoteSection>> childrenByParent) {
        for (NoteSection section : currentLevel) {
            ordered.add(section);
            List<NoteSection> children = childrenByParent.get(section.getId());
            if (children != null && !children.isEmpty()) {
                appendSectionsInTreeOrder(ordered, children, childrenByParent);
            }
        }
    }

    private void upsertStructure(Long noteId, String outlineJson) {
        NoteStructure structure = findStructure(noteId);
        if (structure == null) {
            structure = new NoteStructure();
            structure.setNoteId(noteId);
            structure.setUpdatedBy(SecurityUtils.getUserId());
            structure.setOutlineJson(outlineJson);
            noteStructureMapper.insert(structure);
            return;
        }
        structure.setOutlineJson(outlineJson);
        structure.setUpdatedBy(SecurityUtils.getUserId());
        noteStructureMapper.updateById(structure);
    }

    private void upsertMindmap(Long noteId, String mermaid, String title) {
        String finalMermaid = StringUtils.hasText(mermaid) ? mermaid : buildMindmapMermaid(title, Collections.<OutlineNode>emptyList());
        NoteMindmap mindmap = findMindmap(noteId);
        if (mindmap == null) {
            mindmap = new NoteMindmap();
            mindmap.setNoteId(noteId);
            mindmap.setMapType("mermaid");
            mindmap.setMapData(finalMermaid);
            mindmap.setVersion(1);
            noteMindmapMapper.insert(mindmap);
            return;
        }
        mindmap.setMapType("mermaid");
        mindmap.setMapData(finalMermaid);
        mindmap.setVersion(mindmap.getVersion() == null ? 1 : mindmap.getVersion() + 1);
        noteMindmapMapper.updateById(mindmap);
    }

    private void rebuildSections(Long noteId, List<OutlineNode> outline) {
        noteSectionMapper.delete(new LambdaUpdateWrapper<NoteSection>()
                .eq(NoteSection::getNoteId, noteId));
        if (outline == null) {
            return;
        }
        for (int i = 0; i < outline.size(); i++) {
            insertSectionNode(noteId, null, outline.get(i), 1, i);
        }
    }

    private void insertSectionNode(Long noteId, Long parentId, OutlineNode node, int level, int sortOrder) {
        if (node == null || !StringUtils.hasText(node.getTitle())) {
            return;
        }

        NoteSection section = new NoteSection();
        section.setNoteId(noteId);
        section.setParentId(parentId);
        section.setTitle(node.getTitle().trim());
        section.setContent(node.getContent());
        section.setLevel(level);
        section.setSortOrder(sortOrder);
        noteSectionMapper.insert(section);

        if (node.getChildren() == null) {
            return;
        }
        for (int i = 0; i < node.getChildren().size(); i++) {
            insertSectionNode(noteId, section.getId(), node.getChildren().get(i), level + 1, i);
        }
    }

    private String writeOutlineJson(List<OutlineNode> outline) {
        try {
            return objectMapper.writeValueAsString(outline == null ? Collections.emptyList() : outline);
        } catch (IOException e) {
            log.error("Write outline json failed", e);
            throw new BusinessException(ResultCode.SERVER_ERROR, "Save outline failed");
        }
    }

    private List<OutlineNode> parseOutlineJson(String outlineJson) {
        if (!StringUtils.hasText(outlineJson)) {
            return new ArrayList<>();
        }
        try {
            JavaType type = objectMapper.getTypeFactory().constructCollectionType(List.class, OutlineNode.class);
            return objectMapper.readValue(outlineJson, type);
        } catch (IOException e) {
            log.warn("Parse outline json failed: {}", outlineJson, e);
            return new ArrayList<>();
        }
    }

    private NoteStructure findStructure(Long noteId) {
        return noteStructureMapper.selectOne(new LambdaQueryWrapper<NoteStructure>()
                .eq(NoteStructure::getNoteId, noteId)
                .last("LIMIT 1"));
    }

    private NoteMindmap findMindmap(Long noteId) {
        return noteMindmapMapper.selectOne(new LambdaQueryWrapper<NoteMindmap>()
                .eq(NoteMindmap::getNoteId, noteId)
                .last("LIMIT 1"));
    }

    private Note findOwnNote(Long noteId) {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Unauthorized");
        }
        Note note = noteMapper.selectById(noteId);
        if (note == null || !userId.equals(note.getUserId()) || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Note not found");
        }
        return note;
    }

    private Note findReadableNote(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Note not found");
        }

        Long userId = SecurityUtils.getUserId();
        boolean ownNote = userId != null && userId.equals(note.getUserId());
        boolean published = note.getStatus() != null && note.getStatus() == 1;
        if (!ownNote && !published) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Note not found");
        }
        return note;
    }
}
