package com.zhixu.kb.note.service;

import com.zhixu.kb.ai.EmbeddingService;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 笔记向量化异步执行器：独立线程池执行，不阻塞笔记保存/编辑。
 * 启动时全量补算存量笔记向量（幂等：Milvus 先删后插）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoteVectorizeTaskRunner {

    private final NoteEmbeddingService noteEmbeddingService;
    private final NoteMapper noteMapper;
    private final EmbeddingService embeddingService;
    private final MilvusVectorStore vectorStore;

    /**
     * 自身代理引用（@Lazy）：启动回填通过代理调用 run()，
     * 避免同类内自调用绕过 @Async 导致回填同步阻塞启动线程。
     */
    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private NoteVectorizeTaskRunner self;

    @Async("aiTaskExecutor")
    public void run(Long noteId) {
        try {
            boolean ok = noteEmbeddingService.vectorize(noteId);
            if (ok) {
                log.info("note vectorized: noteId={}", noteId);
            }
        } catch (Exception ex) {
            log.warn("note vectorize task failed (fallback to keyword): noteId={}", noteId, ex.getMessage());
        }
    }

    /**
     * 启动后补算存量笔记向量：
     * - 通过代理异步提交，不阻塞启动线程；
     * - 跳过已在 Milvus 中存在向量的笔记，避免每次重启全库重嵌（幂等但昂贵）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void backfillOnStartup() {
        if (!embeddingService.isEnabled() || !vectorStore.isEnabled()) {
            return;
        }
        try {
            vectorStore.ensureCollection();
        } catch (Exception ex) {
            log.warn("Milvus unavailable, skip backfill: {}", ex.getMessage());
            return;
        }
        try {
            List<Note> pending = noteMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Note>()
                    .select(Note::getId)
                    .eq(Note::getIsDeleted, 0));
            int submitted = 0;
            int skipped = 0;
            for (Note note : pending) {
                if (note.getId() == null) {
                    continue;
                }
                if (vectorStore.hasVectors(note.getId())) {
                    skipped++;
                    continue;
                }
                self.run(note.getId());
                submitted++;
            }
            log.info("Note embedding backfill: submitted={} skipped={}", submitted, skipped);
        } catch (Exception ex) {
            log.warn("Note embedding backfill failed: {}", ex.getMessage());
        }
    }
}