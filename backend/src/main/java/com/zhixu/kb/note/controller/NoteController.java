package com.zhixu.kb.note.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.model.AIAnalysisResult;
import com.zhixu.kb.note.model.NoteHistoryItem;
import com.zhixu.kb.note.model.NoteRequest;
import com.zhixu.kb.note.model.NoteResponse;
import com.zhixu.kb.note.model.NoteStatsResponse;
import com.zhixu.kb.note.model.NoteStructureResponse;
import com.zhixu.kb.note.model.NoteStructureUpdateRequest;
import com.zhixu.kb.note.model.OCRRequest;
import com.zhixu.kb.note.service.AiAnalysisTaskManager;
import com.zhixu.kb.note.service.DocumentNormalizeTaskManager;
import com.zhixu.kb.note.service.NoteHistoryService;
import com.zhixu.kb.note.service.NoteService;
import com.zhixu.kb.note.service.NoteStructureService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;
    private final NoteStructureService noteStructureService;
    private final NoteHistoryService noteHistoryService;

    @GetMapping
    public Result<Page<Note>> list(@RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "10") int size,
                                   @RequestParam(required = false) Long categoryId) {
        return Result.success(noteService.list(page, size, categoryId));
    }

    @GetMapping("/search")
    public Result<Page<Note>> search(@RequestParam(defaultValue = "1") int page,
                                     @RequestParam(defaultValue = "10") int size,
                                     @RequestParam String keyword,
                                     @RequestParam(required = false) Long categoryId) {
        return Result.success(noteService.search(page, size, keyword, categoryId));
    }

    @GetMapping("/stats")
    public Result<NoteStatsResponse> stats() {
        return Result.success(noteService.stats());
    }

    @GetMapping("/{id}")
    public Result<NoteResponse> detail(@PathVariable Long id) {
        return Result.success(noteService.detail(id));
    }

    @PostMapping
    public Result<Note> create(@Validated @RequestBody NoteRequest request) {
        return Result.success(noteService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Note> update(@PathVariable Long id, @Validated @RequestBody NoteRequest request) {
        return Result.success(noteService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        noteService.softDelete(id);
        return Result.success(null);
    }

    @PostMapping("/{id}/ocr")
    public Result<String> ocr(@PathVariable Long id,
                              @RequestParam(required = false) String engine,
                              @RequestParam(required = false) Long fileId,
                              @RequestBody(required = false) OCRRequest request) {
        String actualEngine = request != null && StringUtils.hasText(request.getEngine())
                ? request.getEngine()
                : engine;
        List<Long> fileIds = request != null && !CollectionUtils.isEmpty(request.getFileIds())
                ? request.getFileIds()
                : (fileId != null ? Collections.singletonList(fileId) : null);
        return Result.success(noteService.triggerOCR(id, actualEngine, fileIds));
    }

    @PostMapping("/{id}/ai-analysis")
    public Result<Map<String, Object>> aiAnalysis(@PathVariable Long id) {
        noteService.submitAIAnalysis(id);
        Map<String, Object> data = new HashMap<>();
        data.put("submitted", true);
        return Result.success("AI 整理已提交，可在页面查看进度", data);
    }

    @GetMapping("/{id}/ai-analysis/status")
    public Result<Map<String, Object>> aiAnalysisStatus(@PathVariable Long id) {
        AiAnalysisTaskManager.TaskState state = noteService.getAIAnalysisStatus(id);
        Map<String, Object> data = new HashMap<>();
        data.put("running", state.isRunning());
        data.put("error", state.getError());
        data.put("startedAt", state.getStartedAt());
        data.put("finishedAt", state.getFinishedAt());
        return Result.success(data);
    }

    @PostMapping("/{id}/normalize")
    public Result<Map<String, Object>> normalize(@PathVariable Long id) {
        noteService.submitNormalize(id);
        Map<String, Object> data = new HashMap<>();
        data.put("submitted", true);
        return Result.success("AI 清洗已提交", data);
    }

    @GetMapping("/{id}/normalize/status")
    public Result<Map<String, Object>> normalizeStatus(@PathVariable Long id) {
        DocumentNormalizeTaskManager.TaskState state = noteService.getNormalizeStatus(id);
        Map<String, Object> data = new HashMap<>();
        data.put("running", state.isRunning());
        data.put("error", state.getError());
        data.put("startedAt", state.getStartedAt());
        data.put("finishedAt", state.getFinishedAt());
        return Result.success(data);
    }

    @GetMapping("/{id}/structure")
    public Result<NoteStructureResponse> structure(@PathVariable Long id) {
        return Result.success(noteStructureService.getStructure(id));
    }

    @PostMapping("/{id}/structure/generate")
    public Result<NoteStructureResponse> generateStructure(@PathVariable Long id) {
        NoteStructureResponse response = noteStructureService.generateStructure(id);
        noteHistoryService.recordNoteSnapshot(
                id,
                "NOTE_OUTLINE_GENERATE",
                "Generate note outline and mindmap",
                "/api/notes/" + id + "/structure/generate",
                null
        );
        return Result.success(response);
    }

    @PutMapping("/{id}/structure")
    public Result<NoteStructureResponse> updateStructure(@PathVariable Long id,
                                                         @RequestBody NoteStructureUpdateRequest request) {
        NoteStructureResponse response = noteStructureService.updateStructure(id, request.getOutline());
        noteHistoryService.recordNoteSnapshot(
                id,
                "NOTE_OUTLINE_EDIT",
                "Edit note outline and mindmap",
                "/api/notes/" + id + "/structure",
                request
        );
        return Result.success(response);
    }

    @GetMapping("/{id}/history")
    public Result<List<NoteHistoryItem>> history(@PathVariable Long id) {
        return Result.success(noteHistoryService.list(id));
    }

    @PostMapping("/{id}/history/{historyId}/restore")
    public Result<Void> restoreHistory(@PathVariable Long id, @PathVariable Long historyId) {
        noteHistoryService.restore(id, historyId);
        return Result.success(null);
    }
}
