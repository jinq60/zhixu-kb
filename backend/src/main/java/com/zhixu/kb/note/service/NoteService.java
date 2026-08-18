package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.note.entity.Category;
import com.zhixu.kb.note.entity.FileInfo;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.CategoryMapper;
import com.zhixu.kb.note.mapper.FileInfoMapper;
import com.zhixu.kb.note.mapper.NoteMapper;
import com.zhixu.kb.note.model.AIAnalysisResult;
import com.zhixu.kb.note.model.NoteRequest;
import com.zhixu.kb.note.model.NoteResponse;
import com.zhixu.kb.note.model.NoteStatsResponse;
import com.zhixu.kb.note.model.OutlineNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoteService {
    private static final Pattern FILE_CONTENT_PATTERN = Pattern.compile("/api/files/(\\d+)/content");

private final NoteMapper noteMapper;
private final FileInfoMapper fileInfoMapper;
private final CategoryMapper categoryMapper;
private final OCRClientService ocrClientService;
private final DeepSeekAIService deepSeekAIService;
private final NoteStructureService noteStructureService;
private final NoteHistoryService noteHistoryService;
private final AiAnalysisExecutor aiAnalysisExecutor;
private final AiAnalysisTaskManager aiAnalysisTaskManager;
private final AiAnalysisTaskRunner aiAnalysisTaskRunner;
private final DocumentNormalizeTaskManager documentNormalizeTaskManager;
private final DocumentNormalizeTaskRunner documentNormalizeTaskRunner;

    public Page<Note> list(int page, int size, Long categoryId) {
        Long userId = getUserIdOrThrow();
        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(Note::getIsDeleted, 0)
                .orderByDesc(Note::getUpdateTime);
        if (categoryId != null) {
            wrapper.eq(Note::getCategoryId, categoryId);
        }
        return noteMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public Page<Note> search(int page, int size, String keyword, Long categoryId) {
        Long userId = getUserIdOrThrow();
        if (!StringUtils.hasText(keyword)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Search keyword must not be empty");
        }

        LambdaQueryWrapper<Note> wrapper = new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(Note::getIsDeleted, 0)
                .and(w -> w.like(Note::getTitle, keyword)
                        .or()
                        .like(Note::getContent, keyword)
                        .or()
                        .like(Note::getOcrText, keyword)
                        .or()
                        .like(Note::getSummary, keyword)
                        .or()
                        .like(Note::getKeywords, keyword))
                .orderByDesc(Note::getUpdateTime);
        if (categoryId != null) {
            wrapper.eq(Note::getCategoryId, categoryId);
        }
        return noteMapper.selectPage(new Page<>(page, size), wrapper);
    }

    public NoteStatsResponse stats() {
        Long userId = getUserIdOrThrow();
        Long total = noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(Note::getIsDeleted, 0));
        Long draftCount = noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(Note::getIsDeleted, 0)
                .eq(Note::getStatus, 0));
        Long publishedCount = noteMapper.selectCount(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(Note::getIsDeleted, 0)
                .eq(Note::getStatus, 1));

        return new NoteStatsResponse(
                total == null ? 0L : total,
                draftCount == null ? 0L : draftCount,
                publishedCount == null ? 0L : publishedCount
        );
    }

    public NoteResponse detail(Long id) {
        Note note = findOwnNote(id);
        List<FileInfo> files = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getNoteId, id)
                .orderByAsc(FileInfo::getUploadTime)
                .orderByAsc(FileInfo::getId));
        return new NoteResponse(note, files);
    }

    @Transactional
    public Note create(NoteRequest request) {
        Long userId = getUserIdOrThrow();
        Note note = new Note();
        note.setUserId(userId);
        note.setCategoryId(resolveCategoryId(request.getCategoryId(), userId));
        note.setTitle(request.getTitle().trim());
        note.setContent(request.getContent());
        note.setSummary(request.getSummary());
        note.setKeywords(request.getKeywords());
        note.setCoverImage(request.getCoverImage());
        note.setStatus(request.getStatus() == null ? 0 : normalizeStatus(request.getStatus()));
        noteMapper.insert(note);
        if (!CollectionUtils.isEmpty(request.getOutline())) {
            noteStructureService.saveStructure(note.getId(), request.getOutline(), null);
        }

        // 注意：不再在创建时同步调用 AI 元数据分析（本地模型耗时数分钟会阻塞请求），
        // 摘要/关键词/分类由用户显式执行"AI 整理"完成。

        noteHistoryService.recordNoteSnapshot(
                note.getId(),
                "NOTE_CREATE",
                "Create note",
                "/api/notes/" + note.getId(),
                request
        );

        return note;
    }

    @Transactional
    public Note update(Long id, NoteRequest request) {
        Note note = findOwnNote(id);
        note.setCategoryId(resolveCategoryId(request.getCategoryId(), note.getUserId()));
        note.setTitle(request.getTitle().trim());
        note.setContent(request.getContent());
        note.setSummary(request.getSummary());
        note.setKeywords(request.getKeywords());
        note.setCoverImage(request.getCoverImage());
        if (request.getStatus() != null) {
            note.setStatus(normalizeStatus(request.getStatus()));
        }
        noteMapper.updateById(note);
        if (request.getOutline() != null) {
            noteStructureService.saveStructure(note.getId(), request.getOutline(), null);
        }
        noteHistoryService.recordNoteSnapshot(
                note.getId(),
                "NOTE_SAVE",
                "Save note",
                "/api/notes/" + note.getId(),
                request
        );
        return note;
    }

    @Transactional
    public void softDelete(Long id) {
        Note note = findOwnNote(id);
        int rows = noteMapper.deleteById(note.getId());
        if (rows <= 0) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "Delete note failed");
        }
    }

    @Transactional
    public String triggerOCR(Long id, String engine, List<Long> fileIds) {
        Note note = findOwnNote(id);
        List<FileInfo> files = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getNoteId, id)
                .orderByAsc(FileInfo::getUploadTime)
                .orderByAsc(FileInfo::getId));
        if (CollectionUtils.isEmpty(files)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "No image found for OCR");
        }

        Set<Long> embeddedFileIds = extractEmbeddedFileIds(note.getContent());
        List<FileInfo> candidates = files.stream()
                .filter(f -> f.getId() != null && !embeddedFileIds.contains(f.getId()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(candidates)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "No OCR candidate image found");
        }

        try {
            List<FileInfo> targets = resolveOcrTargets(files, candidates, embeddedFileIds, fileIds);
            String text = runBatchOCR(targets, engine);
            note.setOcrText(text);
            if (!StringUtils.hasText(note.getContent())) {
                note.setContent(text);
            }
            noteMapper.updateById(note);
            noteHistoryService.recordNoteSnapshot(
                    note.getId(),
                    "NOTE_OCR",
                    "Run OCR on note image",
                    "/api/notes/" + note.getId() + "/ocr",
                    fileIds
            );
            return text;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "Read file failed");
        }
    }

    /**
     * 提交文档 AI 清洗任务（异步执行，立即返回）。同一笔记任务进行中时抛出业务异常。
     */
    public boolean submitNormalize(Long id) {
        Note note = findOwnNote(id);
        if (!documentNormalizeTaskManager.tryStart(note.getId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该笔记正在清洗中，请稍候");
        }
        documentNormalizeTaskRunner.run(note.getId(), documentNormalizeTaskManager, SecurityUtils.getLoginUser());
        return true;
    }

    public DocumentNormalizeTaskManager.TaskState getNormalizeStatus(Long id) {
        findOwnNote(id);
        return documentNormalizeTaskManager.get(id);
    }

    private String stripHtml(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return HtmlUtils.htmlUnescape(html)
                .replaceAll("<br\\s*/?>", "\n")
                .replaceAll("</p>", "\n")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private List<FileInfo> resolveOcrTargets(List<FileInfo> files,
                                             List<FileInfo> candidates,
                                             Set<Long> embeddedFileIds,
                                             List<Long> fileIds) {
        if (CollectionUtils.isEmpty(fileIds)) {
            return Collections.singletonList(candidates.get(0));
        }

        List<FileInfo> targets = new ArrayList<>();
        Set<Long> orderedIds = new LinkedHashSet<>(fileIds);
        for (Long fileId : orderedIds) {
            if (fileId == null) {
                continue;
            }

            FileInfo selected = files.stream()
                    .filter(f -> fileId.equals(f.getId()))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ResultCode.BAD_REQUEST, "Selected OCR image not found"));
            if (embeddedFileIds.contains(fileId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "Selected image is already embedded in note content");
            }
            targets.add(selected);
        }

        if (targets.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "No OCR image selected");
        }
        return targets;
    }

    private String runBatchOCR(List<FileInfo> targets, String engine) throws IOException {
        List<String> pageTexts = new ArrayList<>();
        boolean multiplePages = targets.size() > 1;

        for (int index = 0; index < targets.size(); index++) {
            FileInfo target = targets.get(index);
            byte[] bytes = Files.readAllBytes(Paths.get(target.getFilePath()));
            List<String> lines = ocrClientService.recognize(bytes, engine);
            String pageText = String.join("\n", lines).trim();
            if (!StringUtils.hasText(pageText)) {
                continue;
            }

            if (multiplePages) {
                pageTexts.add("第" + (index + 1) + "页\n" + pageText);
            } else {
                pageTexts.add(pageText);
            }
        }

        return String.join("\n\n", pageTexts);
    }

    @Transactional
    public AIAnalysisResult triggerAIAnalysis(Long id) {
        Note note = findOwnNote(id);
        Long userId = getUserIdOrThrow();
        return aiAnalysisExecutor.execute(userId, note.getId());
    }

    /**
     * 提交 AI 整理任务（异步执行，立即返回）。同一笔记任务进行中时抛出业务异常。
     */
    public boolean submitAIAnalysis(Long id) {
        Note note = findOwnNote(id);
        Long userId = getUserIdOrThrow();
        if (!aiAnalysisTaskManager.tryStart(note.getId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该笔记正在整理中，请稍候");
        }
        aiAnalysisTaskRunner.run(userId, note.getId(), aiAnalysisTaskManager, SecurityUtils.getLoginUser());
        return true;
    }

    public AiAnalysisTaskManager.TaskState getAIAnalysisStatus(Long id) {
        findOwnNote(id);
        return aiAnalysisTaskManager.get(id);
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

    private Long resolveCategoryId(Long categoryId, Long userId) {
        if (categoryId == null) {
            return null;
        }

        Category category = categoryMapper.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getId, categoryId)
                .eq(Category::getUserId, userId)
                .eq(Category::getIsDeleted, 0)
                .last("LIMIT 1"));
        if (category == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Selected category does not exist");
        }
        return category.getId();
    }

    private Integer normalizeStatus(Integer status) {
        if (status != 0 && status != 1) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "Invalid note status");
        }
        return status;
    }

    private String normalizeCategoryName(String rawCategoryName) {
        if (!StringUtils.hasText(rawCategoryName)) {
            return null;
        }
        return rawCategoryName.trim();
    }

    private Long getUserIdOrThrow() {
        Long userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "Unauthorized");
        }
        return userId;
    }

    private Note findOwnNote(Long id) {
        Long userId = getUserIdOrThrow();
        Note note = noteMapper.selectById(id);
        if (note == null || !note.getUserId().equals(userId) || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "Note not found");
        }
        return note;
    }

    private Set<Long> extractEmbeddedFileIds(String contentHtml) {
        Set<Long> ids = new HashSet<>();
        if (!StringUtils.hasText(contentHtml)) {
            return ids;
        }

        Matcher matcher = FILE_CONTENT_PATTERN.matcher(contentHtml);
        while (matcher.find()) {
            try {
                ids.add(Long.parseLong(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // Ignore malformed IDs in HTML.
            }
        }
        return ids;
    }
}
