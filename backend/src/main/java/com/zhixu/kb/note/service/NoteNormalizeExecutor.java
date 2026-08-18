package com.zhixu.kb.note.service;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;


/**
 * 文档清洗执行器：供同步接口与异步任务共用，
 * 避免 NoteService 与异步 Runner 之间产生循环依赖。
 */
@Service
@RequiredArgsConstructor
public class NoteNormalizeExecutor {

    private final NoteMapper noteMapper;
    private final DocumentNormalizeService documentNormalizeService;
    private final NoteHistoryService noteHistoryService;

    /**
     * 执行文档 AI 清洗并写回笔记（事务由调用方保证）。
     * 原文一致性校验不通过时保留原文（不写回）。
     *
     * @return 清洗后的纯文本（未变时返回原文）
     */
    @Transactional
    public String execute(Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        return doNormalize(note);
    }

    private String doNormalize(Note note) {
        String plainText = stripHtml(note.getContent());
        if (!StringUtils.hasText(plainText)) {
            plainText = note.getOcrText();
        }
        if (!StringUtils.hasText(plainText)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "笔记内容为空，无法清洗");
        }

        String normalized = documentNormalizeService.normalize(plainText);
        if (StringUtils.hasText(normalized)) {
            String html = toStructuredHtml(normalized);
            if (StringUtils.hasText(html)) {
                note.setContent(html);
                noteMapper.updateById(note);
            }
        }
        noteHistoryService.recordNoteSnapshot(
                note.getId(),
                "NOTE_NORMALIZE",
                "AI 清洗文档格式",
                "/api/notes/" + note.getId() + "/normalize",
                null
        );
        return normalized;
    }

    /**
     * 将清洗后的结构化文本转为 HTML：
     * # 行 → h2、## → h3、### → h4（保留标题层级，与目录结构对应），其余段落 → p。
     */
    private String toStructuredHtml(String text) {
        StringBuilder sb = new StringBuilder();
        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!StringUtils.hasText(trimmed)) {
                continue;
            }
            String escaped = HtmlUtils.htmlEscape(trimmed);
            if (trimmed.startsWith("### ")) {
                sb.append("<h4>").append(escaped.substring(4)).append("</h4>");
            } else if (trimmed.startsWith("## ")) {
                sb.append("<h3>").append(escaped.substring(3)).append("</h3>");
            } else if (trimmed.startsWith("# ")) {
                sb.append("<h2>").append(escaped.substring(2)).append("</h2>");
            } else {
                sb.append("<p>").append(escaped).append("</p>");
            }
        }
        return sb.toString();
    }

    private String stripHtml(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return HtmlUtils.htmlUnescape(html)
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</p>", "\n")
                .replaceAll("</div>", "\n")
                .replaceAll("</h[1-6]>", "\n")
                .replaceAll("</li>", "\n")
                .replaceAll("<[^>]+>", " ")
                // 只压缩水平空白，保留换行（段落结构）
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{2,}", "\n")
                .trim();
    }
}
