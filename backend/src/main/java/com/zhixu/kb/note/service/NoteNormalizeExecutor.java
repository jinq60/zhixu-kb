package com.zhixu.kb.note.service;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;


/**
 * 文档清洗执行器：供同步接口与异步任务共用，
 * 避免 NoteService 与异步 Runner 之间产生循环依赖。
 */
@Slf4j
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
        // 所有权校验（纵深防御）：仅允许操作自己的笔记
        Long userId = SecurityUtils.getUserId();
        if (userId == null || !userId.equals(note.getUserId())) {
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
            // 一致性校验：清洗结果不得大量丢失原文内容（AI 截断/异常输出保护），
            // 压缩比低于阈值时判定异常，保留原文不写回
            if (!isConsistent(plainText, normalized)) {
                log.warn("Normalize result inconsistent (content loss detected), keep original: noteId={} " +
                        "originalLen={} normalizedLen={}", note.getId(), plainText.length(), normalized.length());
                noteHistoryService.recordNoteSnapshot(
                        note.getId(),
                        "NOTE_NORMALIZE",
                        "AI 清洗文档格式（结果不一致，未写回）",
                        "/api/notes/" + note.getId() + "/normalize",
                        null
                );
                return plainText;
            }
            // 清洗后的纯文本转换为结构化 HTML（标题层级 h2-h4 / 列表 / 段落）
            String html = documentNormalizeService.toStructuredHtml(normalized);
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
     * 原文一致性校验：清洗结果的有效文本长度不得低于原文的 50%，
     * 防止 AI 异常输出（截断/空转/幻觉）静默摧毁用户正文。
     */
    private boolean isConsistent(String original, String normalized) {
        if (original == null || normalized == null) {
            return false;
        }
        String compactOriginal = original.replaceAll("\\s+", "");
        String compactNormalized = normalized.replaceAll("\\s+", "");
        if (compactOriginal.length() <= 20) {
            // 原文过短时不做严格比例校验，避免误伤
            return true;
        }
        return compactNormalized.length() >= compactOriginal.length() * 0.5;
    }

    /**
     * 将清洗后的结构化文本转为可读 HTML（确定性，不调用 AI）：
     * 复用 DocumentNormalizeService.toStructuredHtml（标题层级 h2-h4 / 列表 / 段落）。
     */
    private String toStructuredHtml(String text) {
        return documentNormalizeService.toStructuredHtml(text);
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
