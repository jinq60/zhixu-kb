package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhixu.kb.ai.EmbeddingService;
import com.zhixu.kb.note.mapper.CleanChunkTaskMapper;
import com.zhixu.kb.note.mapper.DocumentProcessTaskMapper;
import com.zhixu.kb.note.mapper.EmbedChunkTaskMapper;
import com.zhixu.kb.note.mapper.NoteMapper;
import com.zhixu.kb.note.model.OutlineNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import javax.annotation.PreDestroy;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * 文档处理状态机调度器。两类任务：
 * 1) 上传任务：PENDING -> PARSING（从文件解析纯文本）-> CLEANING（全文 LLM 清洗）-> 写回笔记正文 -> COMPLETED；
 * 2) 向量化任务：EMBEDDING（对笔记最终正文切块 -> Embedding -> Milvus）-> COMPLETED（由 AI 整理触发）。
 *
 * 关键机制：阶段检查点落库、Chunk 级幂等重试（SUCCESS 跳过，retry<max 才重跑，指数退避）、
 * 启动恢复 + 定时扫描推进、卡死明细重置、并发限流（清洗 3 / 向量 4）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentProcessTaskService implements org.springframework.beans.factory.DisposableBean {

    private static final int MAX_RETRY = 5;
    private static final long RETRY_BACKOFF_MS = 10_000;
    private static final long STUCK_MS = 10 * 60 * 1000L;
    /** 全文 LLM 清洗单片上限（模型上下文安全） */
    private static final int LLM_CLEAN_PART_MAX = 50_000;
    private static final int MAX_VECTOR_CHUNKS = 64;

    private final DocumentProcessTaskMapper taskMapper;
    private final CleanChunkTaskMapper cleanChunkMapper;
    private final EmbedChunkTaskMapper embedChunkMapper;
    private final LlmCleanService llmCleanService;
    private final EmbeddingService embeddingService;
    private final MilvusVectorStore vectorStore;
    private final NoteMapper noteMapper;
    private final com.zhixu.kb.note.service.FileService fileService;
    private final DocumentNormalizeService documentNormalizeService;
    private final NoteStructureService noteStructureService;
    private final TransactionTemplate transactionTemplate;

    /** 清洗并发 */
    private final ExecutorService cleanExecutor = Executors.newFixedThreadPool(3);
    private final Semaphore cleanPermits = new Semaphore(3);
    /** 向量化并发 */
    private final ExecutorService embedExecutor = Executors.newFixedThreadPool(4);
    private final Semaphore embedPermits = new Semaphore(4);
    /** 任务异步推进线程（不阻塞上传/整理调用方） */
    private final ExecutorService advanceExecutor = Executors.newSingleThreadExecutor();

    /** 按 taskId 细粒度锁，避免 advance 全局串行 */
    private final Map<Long, Lock> taskLocks = new ConcurrentHashMap<>();

    /**
     * 创建上传任务（PENDING，解析在任务内异步执行）。上传接口不阻塞。
     * 同一文件若已存在未终态任务则复用，防止前端/网络重试导致重复任务。
     */
    public DocumentProcessTaskEntity createUploadTask(Long userId, Long noteId, Long fileId, String fileName) {
        DocumentProcessTaskEntity existing = taskMapper.selectOne(new LambdaQueryWrapper<DocumentProcessTaskEntity>()
                .eq(DocumentProcessTaskEntity::getFileId, fileId)
                .eq(DocumentProcessTaskEntity::getNoteId, noteId)
                .notIn(DocumentProcessTaskEntity::getStatus, "COMPLETED", "FAILED")
                .orderByDesc(DocumentProcessTaskEntity::getId)
                .last("LIMIT 1"));
        if (existing != null) {
            log.info("Reuse existing upload task: taskId={} fileId={} noteId={}", existing.getId(), fileId, noteId);
            return existing;
        }
        DocumentProcessTaskEntity task = new DocumentProcessTaskEntity();
        task.setUserId(userId);
        task.setNoteId(noteId);
        task.setFileId(fileId);
        task.setFileName(fileName);
        task.setStatus("PENDING");
        task.setCurrentStage("PENDING");
        task.setProgress(5);
        task.setRetryCount(0);
        task.setMaxRetry(MAX_RETRY);
        taskMapper.insert(task);
        asyncAdvance(task.getId());
        return task;
    }

    /**
     * 创建向量化任务（AI 整理触发）：对笔记最终正文切块 -> Embedding -> Milvus。
     */
    public DocumentProcessTaskEntity createVectorizeTask(Long userId, Long noteId, String fileName) {
        DocumentProcessTaskEntity task = new DocumentProcessTaskEntity();
        task.setUserId(userId);
        task.setNoteId(noteId);
        task.setFileName(fileName);
        task.setStatus("EMBEDDING");
        task.setCurrentStage("EMBEDDING");
        task.setProgress(50);
        task.setRetryCount(0);
        task.setMaxRetry(MAX_RETRY);
        taskMapper.insert(task);
        asyncAdvance(task.getId());
        return task;
    }

    /**
     * 清理某笔记的全部任务记录与明细（笔记删除时调用，防僵尸数据）。
     */
    public void deleteByNote(Long noteId) {
        try {
            List<DocumentProcessTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<DocumentProcessTaskEntity>()
                    .eq(DocumentProcessTaskEntity::getNoteId, noteId));
            for (DocumentProcessTaskEntity t : tasks) {
                cleanChunkMapper.delete(new LambdaQueryWrapper<CleanChunkTaskEntity>()
                        .eq(CleanChunkTaskEntity::getTaskId, t.getId()));
                embedChunkMapper.delete(new LambdaQueryWrapper<EmbedChunkTaskEntity>()
                        .eq(EmbedChunkTaskEntity::getTaskId, t.getId()));
                taskMapper.deleteById(t.getId());
            }
        } catch (Exception ex) {
            log.warn("Clean document tasks failed (best-effort): noteId={}", noteId, ex.getMessage());
        }
    }

    private void asyncAdvance(Long taskId) {
        try {
            advanceExecutor.submit(() -> {
                DocumentProcessTaskEntity task = taskMapper.selectById(taskId);
                if (task != null) {
                    advance(task);
                }
            });
        } catch (Exception ex) {
            log.warn("async advance submit failed: taskId={}", taskId, ex.getMessage());
        }
    }

    /**
     * 启动恢复：扫描所有非终态任务，从 current_stage 继续。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        try {
            List<DocumentProcessTaskEntity> unfinished = taskMapper.selectList(new LambdaQueryWrapper<DocumentProcessTaskEntity>()
                    .notIn(DocumentProcessTaskEntity::getStatus, "COMPLETED", "FAILED"));
            for (DocumentProcessTaskEntity task : unfinished) {
                asyncAdvance(task.getId());
            }
            if (!unfinished.isEmpty()) {
                log.info("Document process recovery: resumed {} task(s)", unfinished.size());
            }
        } catch (Exception ex) {
            log.warn("Document process recovery failed: {}", ex.getMessage());
        }
    }

    /**
     * 定时扫描：推进非终态任务 + 重置卡死明细。
     */
    @Scheduled(fixedDelay = 30_000)
    public void scheduledScan() {
        try {
            List<DocumentProcessTaskEntity> active = taskMapper.selectList(new LambdaQueryWrapper<DocumentProcessTaskEntity>()
                    .in(DocumentProcessTaskEntity::getStatus, "PENDING", "PARSING", "CLEANING", "EMBEDDING"));
            for (DocumentProcessTaskEntity task : active) {
                resetStuckChunks(task.getId());
                advance(task);
            }
        } catch (Exception ex) {
            log.warn("Document process scheduled scan failed: {}", ex.getMessage());
        }
    }

    /**
     * 阶段推进（幂等）。终态任务直接跳过，防止并发重复推进。
     * 使用按 taskId 细粒度锁，避免全局串行影响吞吐量。
     */
    public void advance(DocumentProcessTaskEntity task) {
        if (task == null || task.getId() == null || task.getStatus() == null
                || "COMPLETED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            return;
        }
        Lock lock = taskLocks.computeIfAbsent(task.getId(), k -> new ReentrantLock());
        if (!lock.tryLock()) {
            // 已有其他线程在推进本任务，本次直接跳过
            return;
        }
        try {
            switch (task.getCurrentStage()) {
                case "PENDING":
                case "PARSING":
                    advanceParsing(task);
                    break;
                case "CLEANING":
                    advanceCleaning(task);
                    break;
                case "EMBEDDING":
                    advanceEmbedding(task);
                    break;
                default:
                    break;
            }
        } catch (Exception ex) {
            log.warn("Task advance failed: taskId={} stage={} err={}", task.getId(), task.getCurrentStage(), ex.getMessage());
        } finally {
            lock.unlock();
        }
    }

    /**
     * PARSING：从存储文件解析纯文本（PDFBox/POI/txt），存 parsed_text 后进入 CLEANING。
     */
    private void advanceParsing(DocumentProcessTaskEntity task) {
        if (StringUtils.hasText(task.getParsedText())) {
            updateStage(task, "CLEANING", 10);
            advance(task);
            return;
        }
        if (task.getFileId() == null) {
            markFailed(task, "\u65e0\u5173\u8054\u6587\u4ef6\u65e0\u6cd5\u89e3\u6790");
            return;
        }
        try {
            String parsed = fileService.parseTextFromStoredFile(task.getFileId());
            if (!StringUtils.hasText(parsed)) {
                markFailed(task, "\u89e3\u6790\u6587\u672c\u4e3a\u7a7a\uff0c\u6587\u4ef6\u53ef\u80fd\u635f\u574f");
                return;
            }
            // 确定性预处理：过滤文档自带元信息/目录区/推广水印（"方便手机阅读"等）、
            // 剥离 ** 等 Markdown 修饰符、保留 # 标题结构（LLM 清洗前先做规则层清理）
            String preCleaned = documentNormalizeService.normalize(parsed);
            if (StringUtils.hasText(preCleaned)) {
                parsed = preCleaned;
            }
            task.setParsedText(parsed);
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
            updateStage(task, "CLEANING", 10);
            advance(task);
        } catch (Exception ex) {
            markFailed(task, "\u89e3\u6790\u5931\u8d25: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    private void advanceCleaning(DocumentProcessTaskEntity task) {
        List<CleanChunkTaskEntity> parts = ensureCleanParts(task);
        long done = parts.stream().filter(c -> "SUCCESS".equals(c.getStatus())).count();
        if (done == parts.size()) {
            finishCleaning(task, parts);
            return;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (CleanChunkTaskEntity part : parts) {
            if ("SUCCESS".equals(part.getStatus())) {
                continue;
            }
            // PROCESSING：已有执行中的清洗调用，跳过（防止定时扫描重复提交导致同一块并发执行多次）
            if ("PROCESSING".equals(part.getStatus())) {
                continue;
            }
            if ("FAILED".equals(part.getStatus())
                    && (part.getRetryCount() >= task.getMaxRetry() || shouldBackoff(part))) {
                continue;
            }
            futures.add(CompletableFuture.runAsync(() -> cleanOne(part, task.getUserId()), cleanExecutor));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<CleanChunkTaskEntity> after = cleanChunkMapper.selectList(new LambdaQueryWrapper<CleanChunkTaskEntity>()
                .eq(CleanChunkTaskEntity::getTaskId, task.getId()));
        long success = after.stream().filter(c -> "SUCCESS".equals(c.getStatus())).count();
        long failed = after.stream().filter(c -> "FAILED".equals(c.getStatus())).count();
        if (success == after.size()) {
            finishCleaning(task, after);
            return;
        }
        int progress = 10 + (int) (success * 30 / Math.max(1, after.size()));
        updateProgress(task, Math.min(progress, 40));
        // 仅当所有失败块重试次数已耗尽才终态失败；否则保留任务由定时扫描按退避重试
        boolean retriesExhausted = after.stream()
                .filter(c -> "FAILED".equals(c.getStatus()))
                .allMatch(c -> c.getRetryCount() != null && c.getRetryCount() >= task.getMaxRetry());
        if (failed > 0 && retriesExhausted
                && after.stream().allMatch(c -> "SUCCESS".equals(c.getStatus()) || "FAILED".equals(c.getStatus()))) {
            markCleaningFailed(task, after);
        }
    }

    /**
     * 重试退避：AI 引擎不可用（端点冷却/限流）时等 60s（对齐端点冷却），其他错误等 10s。
     */
    private boolean shouldBackoff(CleanChunkTaskEntity part) {
        if (part.getUpdateTime() == null) {
            return false;
        }
        long elapsed = Duration.between(part.getUpdateTime(), LocalDateTime.now()).toMillis();
        long backoff = isAiUnavailable(part.getErrorMsg()) ? 60_000L : RETRY_BACKOFF_MS;
        return elapsed < backoff;
    }

    private boolean isAiUnavailable(String errorMsg) {
        return errorMsg != null && (errorMsg.contains("ai engine unavailable")
                || errorMsg.contains("ai endpoint failed")
                || errorMsg.contains("embedding failed"));
    }

    /**
     * 清洗失败收尾：区分 AI 不可用与其他错误，给出明确的失败原因（前端面板可读）。
     */
    private void markCleaningFailed(DocumentProcessTaskEntity task, List<CleanChunkTaskEntity> parts) {
        long aiFailures = parts.stream()
                .filter(c -> "FAILED".equals(c.getStatus()) && isAiUnavailable(c.getErrorMsg()))
                .count();
        long otherFailures = parts.stream()
                .filter(c -> "FAILED".equals(c.getStatus()) && !isAiUnavailable(c.getErrorMsg()))
                .count();
        if (aiFailures > 0 && otherFailures == 0) {
            markFailed(task, "AI 引擎暂时不可用，文档清洗失败。可先在「AI 设置」中配置你自己的 API Key（平台默认额度可能已用尽），配置后点击“重试”");
        } else {
            markFailed(task, "清洗失败片数: " + otherFailures + "（超过最大重试）");
        }
    }

    /**
     * 任务重试：上传任务重置为 PENDING 重新走解析/清洗流程；
     * 向量化任务（无关联文件）重置为 EMBEDDING 重新向量化（不得清空 parsedText 走解析分支）。
     */
    public boolean retryTask(Long userId, Long taskId) {
        DocumentProcessTaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            return false;
        }
        boolean vectorizeTask = task.getFileId() == null;
        if (vectorizeTask) {
            embedChunkMapper.delete(new LambdaQueryWrapper<EmbedChunkTaskEntity>()
                    .eq(EmbedChunkTaskEntity::getTaskId, taskId));
        } else {
            cleanChunkMapper.delete(new LambdaQueryWrapper<CleanChunkTaskEntity>()
                    .eq(CleanChunkTaskEntity::getTaskId, taskId));
            embedChunkMapper.delete(new LambdaQueryWrapper<EmbedChunkTaskEntity>()
                    .eq(EmbedChunkTaskEntity::getTaskId, taskId));
            task.setParsedText(null);
        }
        task.setStatus(vectorizeTask ? "EMBEDDING" : "PENDING");
        task.setCurrentStage(vectorizeTask ? "EMBEDDING" : "PENDING");
        task.setProgress(vectorizeTask ? 50 : 5);
        task.setRetryCount(0);
        task.setFailReason(null);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        asyncAdvance(taskId);
        return true;
    }

    /**
     * 清洗完成：合并清洗结果写回笔记正文（仅当正文仍是解析原文时覆盖），任务完成。
     * 先检查任务是否已被并发线程收尾（scheduledScan 与异步推进可能同时到达），防止重复写回/双完成。
     */
    private void finishCleaning(DocumentProcessTaskEntity task, List<CleanChunkTaskEntity> parts) {
        DocumentProcessTaskEntity fresh = taskMapper.selectById(task.getId());
        if (fresh == null || isTerminal(fresh.getStatus())) {
            return;
        }
        StringBuilder merged = new StringBuilder();
        for (CleanChunkTaskEntity part : parts) {
            if (StringUtils.hasText(part.getCleanedContent())) {
                merged.append(part.getCleanedContent()).append('\n');
            }
        }
        // 规则层兜底清理：剥离 LLM 输出中残留的 ** 等 Markdown 修饰符、过滤水印行，保留 # 标题结构
        String normalized = documentNormalizeService.normalize(merged.toString());
        String source = StringUtils.hasText(normalized) ? normalized : merged.toString();
        // 清洗后的纯文本转换为结构化 HTML（标题层级 h2-h4 / 列表 / 段落），
        // 编辑器直接以可读格式展示（区分标题层级、无需用户再手动排版）
        String structuredHtml = documentNormalizeService.toStructuredHtml(source);
        writeBackToNote(task, StringUtils.hasText(structuredHtml) ? structuredHtml : source);
        completeTask(task);
    }

    /**
     * 清洗结果写回笔记正文：
     * - 正文为空（上传后尚未写入任何内容）→ 直接写回（文档上传场景）；
     * - 正文与解析原文一致（用户未编辑）→ 覆盖为清洗版；
     * - 用户已编辑正文 → 保留用户内容（清洗文本仅用于 RAG）。
     */
    private void writeBackToNote(DocumentProcessTaskEntity task, String cleanedFullText) {
        if (!StringUtils.hasText(cleanedFullText)) {
            return;
        }
        try {
            com.zhixu.kb.note.entity.Note note = noteMapper.selectById(task.getNoteId());
            if (note == null || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
                return;
            }
            String currentContent = note.getContent() == null ? "" : note.getContent().trim();
            String parsed = task.getParsedText() == null ? "" : task.getParsedText().trim();
            // 编辑器默认空内容可能是 <p><br></p> 等 HTML，需要剥离标签后判断是否有可见文本
            String currentText = stripHtmlForCompare(currentContent);
            String parsedText = stripHtmlForCompare(parsed);
            boolean emptyContent = !StringUtils.hasText(currentText);
            boolean untouched = currentText.equals(parsedText);
            if (emptyContent || untouched) {
                note.setContent(cleanedFullText);
                noteMapper.updateById(note);
                log.info("Clean result written back to note: taskId={} noteId={}", task.getId(), task.getNoteId());
                // 第一轮文档清洗：基于正文标题层级生成目录并落库，保证目录与正文严格对应
                try {
                    List<OutlineNode> outline = noteStructureService.extractOutlineFromContent(cleanedFullText);
                    if (!outline.isEmpty()) {
                        noteStructureService.saveStructure(note.getId(), outline, null);
                        log.info("Outline extracted from cleaned content: taskId={} noteId={} nodes={}",
                                task.getId(), task.getNoteId(), outline.size());
                    }
                } catch (Exception outlineEx) {
                    log.warn("Extract outline from cleaned content failed (skip): taskId={}", task.getId(), outlineEx.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Write clean result back to note failed (skip): taskId={}", task.getId(), ex.getMessage());
        }
    }

    /** 剥离 HTML 标签与空白后得到可比较文本，用于判断正文是否为空或是否被用户编辑过 */
    private String stripHtmlForCompare(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        return html.replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void cleanOne(CleanChunkTaskEntity part, Long userId) {
        if (!cleanPermits.tryAcquire()) {
            return;
        }
        com.zhixu.kb.common.utils.SecurityUtils.setUserId(userId);
        try {
            part.setStatus("PROCESSING");
            cleanChunkMapper.updateById(part);
            String cleaned = llmCleanService.clean(part.getRawContent());
            part.setCleanedContent(cleaned);
            part.setStatus("SUCCESS");
            part.setRetryCount(part.getRetryCount() == null ? 1 : part.getRetryCount() + 1);
            part.setErrorMsg(null);
            cleanChunkMapper.updateById(part);
        } catch (Exception ex) {
            part.setStatus("FAILED");
            part.setRetryCount(part.getRetryCount() == null ? 1 : part.getRetryCount() + 1);
            String msg = ex.getMessage() == null ? "clean failed" : ex.getMessage();
            part.setErrorMsg(msg.length() > 500 ? msg.substring(0, 500) : msg);
            cleanChunkMapper.updateById(part);
        } finally {
            com.zhixu.kb.common.utils.SecurityUtils.clear();
            cleanPermits.release();
        }
    }

    private List<CleanChunkTaskEntity> ensureCleanParts(DocumentProcessTaskEntity task) {
        List<CleanChunkTaskEntity> existing = cleanChunkMapper.selectList(new LambdaQueryWrapper<CleanChunkTaskEntity>()
                .eq(CleanChunkTaskEntity::getTaskId, task.getId()));
        if (!existing.isEmpty()) {
            return existing;
        }
        List<String> parts = splitFullText(task.getParsedText());
        List<CleanChunkTaskEntity> created = new ArrayList<>();
        for (int i = 0; i < parts.size(); i++) {
            CleanChunkTaskEntity part = new CleanChunkTaskEntity();
            part.setTaskId(task.getId());
            part.setChunkIndex(i);
            part.setRawContent(parts.get(i));
            part.setStatus("PENDING");
            part.setRetryCount(0);
            cleanChunkMapper.insert(part);
            created.add(part);
        }
        return created;
    }

    private List<String> splitFullText(String text) {
        List<String> parts = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return parts;
        }
        String remaining = text;
        while (remaining.length() > LLM_CLEAN_PART_MAX) {
            int cut = LLM_CLEAN_PART_MAX;
            for (int i = LLM_CLEAN_PART_MAX; i > LLM_CLEAN_PART_MAX - 500; i--) {
                if (remaining.charAt(i) == '\n') {
                    cut = i + 1;
                    break;
                }
            }
            parts.add(remaining.substring(0, cut).trim());
            remaining = remaining.substring(cut).trim();
        }
        if (remaining.length() > 0) {
            parts.add(remaining);
        }
        return parts;
    }

    /**
     * EMBEDDING（向量化任务）：对笔记最终正文切块 -> Embedding -> Milvus（块级 upsert 幂等）。
     */
    private void advanceEmbedding(DocumentProcessTaskEntity task) {
        try {
            com.zhixu.kb.note.entity.Note note = noteMapper.selectById(task.getNoteId());
            if (note == null) {
                markFailed(task, "\u7b14\u8bb0\u4e0d\u5b58\u5728");
                return;
            }
            String source = StringUtils.hasText(note.getContent()) ? note.getContent() : note.getOcrText();
            List<String> segments = NoteChunkSplitter.split(source);
            if (segments.isEmpty()) {
                markFailed(task, "\u7b14\u8bb0\u5185\u5bb9\u4e3a\u7a7a\uff0c\u65e0\u6cd5\u5411\u91cf\u5316");
                return;
            }
            List<EmbedChunkTaskEntity> chunks = embedChunkMapper.selectList(new LambdaQueryWrapper<EmbedChunkTaskEntity>()
                    .eq(EmbedChunkTaskEntity::getTaskId, task.getId()));
            if (chunks.isEmpty()) {
                List<String> bounded = segments.size() > MAX_VECTOR_CHUNKS
                        ? segments.subList(0, MAX_VECTOR_CHUNKS) : segments;
                for (int i = 0; i < bounded.size(); i++) {
                    EmbedChunkTaskEntity seg = new EmbedChunkTaskEntity();
                    seg.setTaskId(task.getId());
                    seg.setChunkIndex(i);
                    seg.setContent(bounded.get(i));
                    seg.setStatus("PENDING");
                    seg.setRetryCount(0);
                    embedChunkMapper.insert(seg);
                }
                chunks = embedChunkMapper.selectList(new LambdaQueryWrapper<EmbedChunkTaskEntity>()
                        .eq(EmbedChunkTaskEntity::getTaskId, task.getId()));
            }
            long done = chunks.stream().filter(c -> "SUCCESS".equals(c.getStatus())).count();
            if (done == chunks.size()) {
                completeTask(task);
                return;
            }
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (EmbedChunkTaskEntity chunk : chunks) {
                if ("SUCCESS".equals(chunk.getStatus())) {
                    continue;
                }
                // PROCESSING：已有执行中的向量化调用，跳过（防止定时扫描重复提交）
                if ("PROCESSING".equals(chunk.getStatus())) {
                    continue;
                }
                if ("FAILED".equals(chunk.getStatus())
                        && (chunk.getRetryCount() >= task.getMaxRetry()
                        || chunk.getUpdateTime() != null
                        && Duration.between(chunk.getUpdateTime(), LocalDateTime.now()).toMillis()
                        < (isAiUnavailable(chunk.getErrorMsg()) ? 60_000L : RETRY_BACKOFF_MS))) {
                    continue;
                }
                futures.add(CompletableFuture.runAsync(() -> embedOne(task, chunk), embedExecutor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<EmbedChunkTaskEntity> after = embedChunkMapper.selectList(new LambdaQueryWrapper<EmbedChunkTaskEntity>()
                    .eq(EmbedChunkTaskEntity::getTaskId, task.getId()));
            long success = after.stream().filter(c -> "SUCCESS".equals(c.getStatus())).count();
            long failed = after.stream().filter(c -> "FAILED".equals(c.getStatus())).count();
            updateProgress(task, 50 + (int) (success * 40 / Math.max(1, after.size())));
            if (success == after.size()) {
                completeTask(task);
            } else if (failed > 0 && after.stream().allMatch(c -> "SUCCESS".equals(c.getStatus()) || "FAILED".equals(c.getStatus()))) {
                boolean allEmbeddingUnavailable = after.stream()
                        .filter(c -> "FAILED".equals(c.getStatus()))
                        .allMatch(c -> isAiUnavailable(c.getErrorMsg()));
                boolean retriesExhausted = after.stream()
                        .filter(c -> "FAILED".equals(c.getStatus()))
                        .allMatch(c -> c.getRetryCount() != null && c.getRetryCount() >= task.getMaxRetry());
                if (allEmbeddingUnavailable) {
                    // 无可用向量化服务（未配置 embedding 端点/平台池不可用）：
                    // 不报技术错误，任务标记跳过，问答自动降级为关键词检索
                    task.setStatus("SKIPPED");
                    task.setCurrentStage("SKIPPED");
                    task.setFailReason("未配置可用的向量化服务，已跳过向量化（知识问答使用关键词检索）。可在 AI 设置中配置向量化端点后重试");
                    task.setUpdateTime(LocalDateTime.now());
                    taskMapper.updateById(task);
                    log.info("Document process skipped vectorization (no embedding service): taskId={}", task.getId());
                } else if (retriesExhausted) {
                    markFailed(task, "向量化失败块数: " + failed + "（超过最大重试）");
                }
                // 重试未耗尽：保持任务非终态，由定时扫描按退避重试
            }
        } catch (Exception ex) {
            markFailed(task, "向量化失败: " + (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage()));
        }
    }

    private void embedOne(DocumentProcessTaskEntity task, EmbedChunkTaskEntity chunk) {
        if (!embedPermits.tryAcquire()) {
            return;
        }
        com.zhixu.kb.common.utils.SecurityUtils.setUserId(task.getUserId());
        try {
            chunk.setStatus("PROCESSING");
            embedChunkMapper.updateById(chunk);
            float[] vector = embeddingService.embed(chunk.getContent());
            if (vector == null || vector.length == 0) {
                throw new IllegalStateException("empty embedding");
            }
            vectorStore.insertChunk(task.getUserId(), task.getNoteId(),
                    chunk.getChunkIndex() == null ? 0 : chunk.getChunkIndex(),
                    chunk.getContent(), vector);
            chunk.setStatus("SUCCESS");
            chunk.setRetryCount(chunk.getRetryCount() == null ? 1 : chunk.getRetryCount() + 1);
            embedChunkMapper.updateById(chunk);
        } catch (Exception ex) {
            chunk.setStatus("FAILED");
            chunk.setRetryCount(chunk.getRetryCount() == null ? 1 : chunk.getRetryCount() + 1);
            String msg = ex.getMessage() == null ? "embedding failed" : ex.getMessage();
            chunk.setErrorMsg(msg.length() > 500 ? msg.substring(0, 500) : msg);
            embedChunkMapper.updateById(chunk);
        } finally {
            com.zhixu.kb.common.utils.SecurityUtils.clear();
            embedPermits.release();
        }
    }

    private void completeTask(DocumentProcessTaskEntity task) {
        // 并发收尾保护：任务已被其他线程置为终态时跳过，避免重复写回/日志
        DocumentProcessTaskEntity fresh = taskMapper.selectById(task.getId());
        if (fresh == null || isTerminal(fresh.getStatus())) {
            return;
        }
        task.setStatus("COMPLETED");
        task.setCurrentStage("COMPLETED");
        task.setProgress(100);
        task.setFailReason(null);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        log.info("Document process completed: taskId={} noteId={} stage={}", task.getId(), task.getNoteId(), task.getCurrentStage());
    }

    private boolean isTerminal(String status) {
        return "COMPLETED".equals(status) || "FAILED".equals(status) || "SKIPPED".equals(status);
    }

    private void updateStage(DocumentProcessTaskEntity task, String stage, int progress) {
        task.setStatus(stage);
        task.setCurrentStage(stage);
        task.setProgress(Math.max(task.getProgress() == null ? 0 : task.getProgress(), progress));
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void updateProgress(DocumentProcessTaskEntity task, int progress) {
        task.setProgress(progress);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    private void markFailed(DocumentProcessTaskEntity task, String reason) {
        task.setStatus("FAILED");
        task.setFailReason(reason);
        task.setUpdateTime(LocalDateTime.now());
        taskMapper.updateById(task);
        log.warn("Document process failed: taskId={} reason={}", task.getId(), reason);
    }

    private void resetStuckChunks(Long taskId) {
        LocalDateTime threshold = LocalDateTime.now().minus(STUCK_MS, ChronoUnit.MILLIS);
        List<CleanChunkTaskEntity> stuckClean = cleanChunkMapper.selectList(new LambdaQueryWrapper<CleanChunkTaskEntity>()
                .eq(CleanChunkTaskEntity::getTaskId, taskId)
                .eq(CleanChunkTaskEntity::getStatus, "PROCESSING")
                .lt(CleanChunkTaskEntity::getUpdateTime, threshold));
        for (CleanChunkTaskEntity c : stuckClean) {
            c.setStatus("PENDING");
            cleanChunkMapper.updateById(c);
        }
        if (!stuckClean.isEmpty()) {
            log.warn("Reset stuck clean chunks: taskId={} count={}", taskId, stuckClean.size());
        }
    }

    public DocumentProcessTaskEntity getTask(Long taskId) {
        return taskMapper.selectById(taskId);
    }

    public DocumentProcessTaskEntity getLatestTaskByNote(Long noteId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<DocumentProcessTaskEntity>()
                .eq(DocumentProcessTaskEntity::getNoteId, noteId)
                .orderByDesc(DocumentProcessTaskEntity::getId)
                .last("LIMIT 1"));
    }

    public List<DocumentProcessTaskEntity> listActiveTasks(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<DocumentProcessTaskEntity>()
                .eq(DocumentProcessTaskEntity::getUserId, userId)
                .notIn(DocumentProcessTaskEntity::getStatus, "COMPLETED", "FAILED", "SKIPPED")
                .orderByDesc(DocumentProcessTaskEntity::getId)
                .last("LIMIT 20"));
    }

    /**
     * 最近任务（含终态，任务面板展示历史用）。
     */
    public List<DocumentProcessTaskEntity> listRecentTasks(Long userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<DocumentProcessTaskEntity>()
                .eq(DocumentProcessTaskEntity::getUserId, userId)
                .orderByDesc(DocumentProcessTaskEntity::getId)
                .last("LIMIT 10"));
    }

    // ---------- 任务视图（细粒度监控：阶段 + 块级进度 + 耗时，供前端任务中心展示） ----------

    public Map<String, Object> taskView(Long userId, Long taskId) {
        DocumentProcessTaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            return null;
        }
        return buildTaskView(task);
    }

    public Map<String, Object> latestTaskViewByNote(Long userId, Long noteId) {
        DocumentProcessTaskEntity task = getLatestTaskByNote(noteId);
        if (task == null || !task.getUserId().equals(userId)) {
            return null;
        }
        return buildTaskView(task);
    }

    public List<Map<String, Object>> activeTaskViews(Long userId) {
        return listActiveTasks(userId).stream().map(this::buildTaskView).collect(Collectors.toList());
    }

    public List<Map<String, Object>> recentTaskViews(Long userId) {
        return listRecentTasks(userId).stream().map(this::buildTaskView).collect(Collectors.toList());
    }

    /**
     * 删除单条任务及其明细（任务中心"删除任务日志"）。归属校验失败返回 false。
     */
    public boolean deleteTask(Long userId, Long taskId) {
        DocumentProcessTaskEntity task = taskMapper.selectById(taskId);
        if (task == null || !task.getUserId().equals(userId)) {
            return false;
        }
        cleanChunkMapper.delete(new LambdaQueryWrapper<CleanChunkTaskEntity>()
                .eq(CleanChunkTaskEntity::getTaskId, taskId));
        embedChunkMapper.delete(new LambdaQueryWrapper<EmbedChunkTaskEntity>()
                .eq(EmbedChunkTaskEntity::getTaskId, taskId));
        taskMapper.deleteById(taskId);
        log.info("Task deleted: taskId={} userId={}", taskId, userId);
        return true;
    }

    /**
     * 清空当前用户全部任务与明细（任务中心"清空全部"）。返回删除条数。
     */
    public int clearAllTasks(Long userId) {
        List<DocumentProcessTaskEntity> tasks = taskMapper.selectList(new LambdaQueryWrapper<DocumentProcessTaskEntity>()
                .eq(DocumentProcessTaskEntity::getUserId, userId));
        for (DocumentProcessTaskEntity task : tasks) {
            cleanChunkMapper.delete(new LambdaQueryWrapper<CleanChunkTaskEntity>()
                    .eq(CleanChunkTaskEntity::getTaskId, task.getId()));
            embedChunkMapper.delete(new LambdaQueryWrapper<EmbedChunkTaskEntity>()
                    .eq(EmbedChunkTaskEntity::getTaskId, task.getId()));
            taskMapper.deleteById(task.getId());
        }
        if (!tasks.isEmpty()) {
            log.info("All tasks cleared: userId={} count={}", userId, tasks.size());
        }
        return tasks.size();
    }

    private Map<String, Object> buildTaskView(DocumentProcessTaskEntity task) {
        Map<String, Object> v = new HashMap<>();
        v.put("taskId", task.getId());
        v.put("noteId", task.getNoteId());
        v.put("fileName", task.getFileName());
        v.put("subType", task.getFileId() != null ? "文档清洗" : "知识向量化");
        v.put("status", task.getStatus());
        v.put("currentStage", task.getCurrentStage());
        v.put("progress", task.getProgress());
        v.put("failReason", task.getFailReason());
        v.put("createTime", task.getCreateTime());
        v.put("updateTime", task.getUpdateTime());
        LocalDateTime endTime = (task.getUpdateTime() != null && isTerminal(task.getStatus()))
                ? task.getUpdateTime()
                : LocalDateTime.now();
        long elapsed = task.getCreateTime() == null ? 0
                : Math.max(0, Duration.between(task.getCreateTime(), endTime).getSeconds());
        v.put("elapsedSeconds", elapsed);
        // 块级进度统计：清洗明细（上传类任务）与向量化明细（向量化任务）
        if (task.getFileId() != null) {
            List<CleanChunkTaskEntity> clean = cleanChunkMapper.selectList(new LambdaQueryWrapper<CleanChunkTaskEntity>()
                    .eq(CleanChunkTaskEntity::getTaskId, task.getId()));
            v.put("cleanChunks", buildChunkStats(clean.size(),
                    countCleanStatus(clean, "SUCCESS"), countCleanStatus(clean, "FAILED"),
                    countCleanStatus(clean, "PROCESSING")));
        }
        List<EmbedChunkTaskEntity> embed = embedChunkMapper.selectList(new LambdaQueryWrapper<EmbedChunkTaskEntity>()
                .eq(EmbedChunkTaskEntity::getTaskId, task.getId()));
        if (!embed.isEmpty() || task.getFileId() == null) {
            v.put("embedChunks", buildChunkStats(embed.size(),
                    countEmbedStatus(embed, "SUCCESS"), countEmbedStatus(embed, "FAILED"),
                    countEmbedStatus(embed, "PROCESSING")));
        }
        return v;
    }

    private Map<String, Object> buildChunkStats(int total, long success, long failed, long processing) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", total);
        stats.put("success", success);
        stats.put("failed", failed);
        stats.put("processing", processing);
        return stats;
    }

    private long countCleanStatus(List<CleanChunkTaskEntity> chunks, String status) {
        return chunks.stream().filter(c -> status.equals(c.getStatus())).count();
    }

    private long countEmbedStatus(List<EmbedChunkTaskEntity> chunks, String status) {
        return chunks.stream().filter(c -> status.equals(c.getStatus())).count();
    }

    /**
     * 应用关闭时优雅停止线程池，避免正在执行的清洗/向量化任务被强制中断。
     */
    @PreDestroy
    @Override
    public void destroy() {
        shutdownExecutor(advanceExecutor, "advance");
        shutdownExecutor(cleanExecutor, "clean");
        shutdownExecutor(embedExecutor, "embed");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor == null || executor.isShutdown()) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("{} executor did not terminate in 30s, forcing shutdown", name);
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            log.warn("{} executor await interrupted, forcing shutdown", name);
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}