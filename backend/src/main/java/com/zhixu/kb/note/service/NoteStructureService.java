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
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NoteStructureService {

    private final NoteMapper noteMapper;
    private final NoteStructureMapper noteStructureMapper;
    private final NoteMindmapMapper noteMindmapMapper;
    private final NoteSectionMapper noteSectionMapper;
    private final DeepSeekAIService deepSeekAIService;
    private final ObjectMapper objectMapper;

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
        String mermaid = !outline.isEmpty()
                ? buildMindmapMermaid(note.getTitle(), outline)
                : (mindmap != null ? mindmap.getMapData() : buildMindmapMermaid(note.getTitle(), outline));
        LocalDateTime updateTime = structure != null ? structure.getUpdateTime() : null;
        return new NoteStructureResponse(outline, sections, mermaid, updateTime);
    }

    @Transactional
    public NoteStructureResponse generateStructure(Long noteId) {
        Note note = findOwnNote(noteId);
        String source = StringUtils.hasText(note.getContent()) ? note.getContent() : note.getOcrText();
        if (!StringUtils.hasText(source)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Note content is empty, cannot generate outline");
        }

        List<OutlineNode> outline = deepSeekAIService.generateOutline(note.getTitle(), source);
        String mermaid = buildMindmapMermaid(note.getTitle(), outline);
        saveStructure(noteId, outline, mermaid);
        return getStructure(noteId);
    }

    @Transactional
    public void saveStructure(Long noteId, String outlineJson, String mermaid) {
        List<OutlineNode> outline = parseOutlineJson(outlineJson);
        saveStructure(noteId, outline, mermaid);
    }

    @Transactional
    public void saveStructure(Long noteId, List<OutlineNode> outline, String mermaid) {
        Note note = findOwnNote(noteId);
        String outlineJson = writeOutlineJson(outline);
        String finalMermaid = StringUtils.hasText(mermaid)
                ? mermaid
                : buildMindmapMermaid(note.getTitle(), outline == null ? Collections.emptyList() : outline);
        upsertStructure(noteId, outlineJson);
        upsertMindmap(noteId, finalMermaid, note.getTitle());
        rebuildSections(noteId, outline);
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

    public String buildMindmapMermaid(String title, List<OutlineNode> outline) {
        String rootTitle = sanitizeMermaidLabel(StringUtils.hasText(title) ? title : "学习笔记");
        StringBuilder sb = new StringBuilder("mindmap\n");
        sb.append("  root[\"").append(rootTitle).append("\"]\n");
        int[] sequence = {0};
        for (OutlineNode node : outline == null ? Collections.<OutlineNode>emptyList() : outline) {
            appendMindmapNode(sb, node, 2, sequence);
        }
        return sb.toString();
    }

    private void appendMindmapNode(StringBuilder sb, OutlineNode node, int depth, int[] sequence) {
        if (node == null || !StringUtils.hasText(node.getTitle())) {
            return;
        }
        for (int i = 0; i < depth; i++) {
            sb.append("  ");
        }
        String nodeId = "node" + (++sequence[0]);
        sb.append(nodeId)
                .append("[\"")
                .append(sanitizeMermaidLabel(node.getTitle()))
                .append("\"]\n");

        if (node.getChildren() == null) {
            return;
        }
        for (OutlineNode child : node.getChildren()) {
            appendMindmapNode(sb, child, depth + 1, sequence);
        }
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
