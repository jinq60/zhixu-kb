package com.zhixu.kb.note.controller;

import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.note.entity.FileInfo;
import com.zhixu.kb.note.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;
    private final com.zhixu.kb.note.service.DocumentProcessTaskService documentProcessTaskService;

    /** 任务状态查询（前端轮询进度）：细粒度视图（阶段/进度/块级统计/耗时） */
    @GetMapping("/tasks/{taskId}")
    public Result<Map<String, Object>> taskStatus(@PathVariable Long taskId) {
        Long userId = com.zhixu.kb.common.utils.SecurityUtils.getUserId();
        Map<String, Object> view = documentProcessTaskService.taskView(userId, taskId);
        if (view == null) {
            return Result.error(404, "任务不存在");
        }
        return Result.success(view);
    }

    /** 按笔记查询最新任务（编辑页恢复进度展示） */
    @GetMapping("/tasks/note/{noteId}")
    public Result<Map<String, Object>> taskByNote(@PathVariable Long noteId) {
        Long userId = com.zhixu.kb.common.utils.SecurityUtils.getUserId();
        Map<String, Object> view = documentProcessTaskService.latestTaskViewByNote(userId, noteId);
        if (view == null) {
            return Result.error(404, "任务不存在");
        }
        return Result.success(view);
    }

    /** 进行中任务列表（全局任务角标/任务面板） */
    @GetMapping("/tasks/active")
    public Result<List<Map<String, Object>>> activeTasks() {
        Long userId = com.zhixu.kb.common.utils.SecurityUtils.getUserId();
        return Result.success(documentProcessTaskService.activeTaskViews(userId));
    }

    /** 最近任务（含完成/失败，供任务面板展示历史） */
    @GetMapping("/tasks/recent")
    public Result<List<Map<String, Object>>> recentTasks() {
        Long userId = com.zhixu.kb.common.utils.SecurityUtils.getUserId();
        return Result.success(documentProcessTaskService.recentTaskViews(userId));
    }

    /** 任务重试（AI 恢复后重新处理失败任务） */
    @PostMapping("/tasks/{taskId}/retry")
    public Result<Boolean> retryTask(@PathVariable Long taskId) {
        boolean ok = documentProcessTaskService.retryTask(
                com.zhixu.kb.common.utils.SecurityUtils.getUserId(), taskId);
        if (!ok) {
            return Result.error(404, "任务不存在或无权操作");
        }
        return Result.success("已重新提交处理", Boolean.TRUE);
    }

    /** 删除任务日志（任务中心），级联删除明细 */
    @DeleteMapping("/tasks/{taskId}")
    public Result<Boolean> deleteTask(@PathVariable Long taskId) {
        boolean ok = documentProcessTaskService.deleteTask(
                com.zhixu.kb.common.utils.SecurityUtils.getUserId(), taskId);
        if (!ok) {
            return Result.error(404, "任务不存在或无权操作");
        }
        return Result.success("任务已删除", Boolean.TRUE);
    }

    /** 清空当前用户全部任务日志 */
    @DeleteMapping("/tasks")
    public Result<Integer> clearAllTasks() {
        int count = documentProcessTaskService.clearAllTasks(
                com.zhixu.kb.common.utils.SecurityUtils.getUserId());
        return Result.success("已清空 " + count + " 条任务记录", count);
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                              @RequestParam(value = "noteId", required = false) Long noteId,
                                              @RequestParam(value = "normalize", defaultValue = "true") Boolean normalize) {
        // 只保存文件并创建文档处理任务（解析/清洗异步执行），上传接口不阻塞
        FileInfo info = fileService.store(file, noteId);
        Map<String, Object> data = new HashMap<>();
        data.put("file", info);
        if (noteId != null && info != null && info.getId() != null) {
            try {
                Long taskId = documentProcessTaskService.createUploadTask(
                        com.zhixu.kb.common.utils.SecurityUtils.getUserId(),
                        noteId,
                        info.getId(),
                        file.getOriginalFilename()).getId();
                data.put("taskId", taskId);
                data.put("taskStatus", "PENDING");
            } catch (Exception ex) {
                log.warn("create document process task failed: {}", ex.getMessage());
            }
        }
        return Result.success(data);
    }

    /**
     * 分片上传：接收单个分片并暂存（大文件上传，前端按片上传后调用 merge 合并）。
     */
    @PostMapping("/upload-chunk")
    public Result<Void> uploadChunk(@RequestParam("file") MultipartFile file,
                                    @RequestParam("identifier") String identifier,
                                    @RequestParam("chunkIndex") Integer chunkIndex,
                                    @RequestParam("totalChunks") Integer totalChunks) {
        fileService.storeChunk(file, identifier, chunkIndex, totalChunks);
        return Result.success(null);
    }

    /**
     * 分片合并：全部片上传完成后合并为完整文件并走正常存储/解析流程。
     */
    @PostMapping("/upload-chunk/merge")
    public Result<Map<String, Object>> mergeChunks(@RequestParam("identifier") String identifier,
                                                   @RequestParam("fileName") String fileName,
                                                   @RequestParam("totalChunks") Integer totalChunks,
                                                   @RequestParam(value = "noteId", required = false) Long noteId,
                                                   @RequestParam(value = "normalize", defaultValue = "true") Boolean normalize) {
        FileService.UploadPayload payload = fileService.mergeAndStore(
                identifier, fileName, totalChunks, noteId, Boolean.TRUE.equals(normalize));
        Map<String, Object> data = new HashMap<>();
        data.put("file", payload.getFile());
        data.put("extractedText", payload.getExtractedText());
        data.put("normalizedText", payload.getNormalizedText());
        // 与单文件上传一致：创建后台解析/清洗任务，前端按 taskId 轮询进度
        if (noteId != null && payload.getFile() != null && payload.getFile().getId() != null) {
            try {
                Long taskId = documentProcessTaskService.createUploadTask(
                        com.zhixu.kb.common.utils.SecurityUtils.getUserId(),
                        noteId,
                        payload.getFile().getId(),
                        fileName).getId();
                data.put("taskId", taskId);
                data.put("taskStatus", "PENDING");
            } catch (Exception ex) {
                log.warn("create document process task failed (merge path): {}", ex.getMessage());
            }
        }
        return Result.success(data);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<ByteArrayResource> content(@PathVariable Long id) {
        FileService.FileContent content = fileService.loadContent(id);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.getMimeType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        ByteArrayResource resource = new ByteArrayResource(content.getBytes());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(content.getFilename()).build().toString())
                .body(resource);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        fileService.delete(id);
        return Result.success(null);
    }
}
