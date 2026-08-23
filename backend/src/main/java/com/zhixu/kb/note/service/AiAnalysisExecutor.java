package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.note.entity.Category;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.CategoryMapper;
import com.zhixu.kb.note.mapper.NoteMapper;
import com.zhixu.kb.note.model.AIAnalysisResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * AI 整理执行器：供同步接口与异步任务共用，避免 NoteService 与异步 Runner 之间产生循环依赖。
 * <p>
 * 设计原则：AI 只做元数据分析（摘要/关键词/分类）与大纲生成（目录/导图），
 * 绝不改写正文——正文保持"文档清洗后的可读文章"原貌（确定性格式化，不润色不生成）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAnalysisExecutor {

    private final NoteMapper noteMapper;
    private final CategoryMapper categoryMapper;
    private final DeepSeekAIService deepSeekAIService;
    private final NoteHistoryService noteHistoryService;
    private final DocumentProcessTaskService documentProcessTaskService;
    private final TransactionTemplate transactionTemplate;

    /**
     * 执行 AI 整理：AI 调用（最长可达数分钟）在事务外执行；
     * 结果写回使用短事务，避免长时间占用数据库连接。
     * 写回前校验笔记未被删除；正文始终不被 AI 改写。
     */
    public AIAnalysisResult execute(Long userId, Long noteId) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || !note.getUserId().equals(userId)
                || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        // 仅"上传类"任务（解析/清洗正文，fileId 非空）阻塞 AI 整理；
        // 向量化任务（fileId 为空，失败重试可延续数分钟）不阻塞：两者互不依赖，可先后独立完成
        DocumentProcessTaskEntity task = documentProcessTaskService.getLatestTaskByNote(noteId);
        if (task != null && task.getFileId() != null && task.getStatus() != null
                && !"COMPLETED".equals(task.getStatus())
                && !"FAILED".equals(task.getStatus())
                && !"SKIPPED".equals(task.getStatus())) {
            String stageText = "PARSING".equals(task.getCurrentStage()) ? "解析" : "清洗";
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "文档正在" + stageText + "处理中（进度 "
                            + (task.getProgress() == null ? 0 : task.getProgress())
                            + "%），完成后正文自动更新，请稍后再执行 AI 整理");
        }
        String contentToAnalyze = note.getContent();
        if (!StringUtils.hasText(contentToAnalyze)) {
            contentToAnalyze = note.getOcrText();
        }
        if (!StringUtils.hasText(contentToAnalyze)) {
            throw new BusinessException(ResultCode.BAD_REQUEST,
                    "笔记内容为空，请先上传文档并等待清洗完成，或手动编辑正文后重试");
        }

        AIAnalysisResult analysis = deepSeekAIService.analyzeNoteContent(note.getTitle(), contentToAnalyze);
        return transactionTemplate.execute(status -> persistAnalysis(userId, noteId, analysis));
    }

    private AIAnalysisResult persistAnalysis(Long userId, Long noteId, AIAnalysisResult analysis) {
        Note note = noteMapper.selectById(noteId);
        if (note == null || !note.getUserId().equals(userId)
                || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        // 只写元数据：分类/摘要/关键词（正文原样保留）
        applyAIAnalysis(note, analysis, userId);
        noteMapper.updateById(note);
        // AI 整理后触发向量化任务（切块+Embedding+Milvus，顶部任务面板可见进度）
        try {
            documentProcessTaskService.createVectorizeTask(userId, noteId, note.getTitle());
        } catch (Exception e) {
            log.warn("submit vectorize task failed: noteId={}", noteId, e.getMessage());
        }

        // 第二轮 AI 整理：只写元数据（分类/摘要/关键词）并触发向量化，不再生成目录；
        // 目录由第一轮文档清洗从正文标题层级提取，确保与正文严格对应。
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
}