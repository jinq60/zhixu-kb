package com.zhixu.kb.note.service;

import com.zhixu.kb.ai.EmbeddingService;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记向量化与相似检索（RAG 向量召回，存储与检索基于 Milvus）。
 * 写入：笔记内容变化后异步提交向量化（语义切分：按标题切块 + 重叠，批量向量化）。
 * 查询：Milvus 按 user_id 过滤 + 余弦相似度 topK。
 * Milvus / embedding 不可用时安全降级（返回空），不影响关键词检索。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteEmbeddingService {

    private static final int MAX_CHUNKS = 64;

    private final EmbeddingService embeddingService;
    private final MilvusVectorStore vectorStore;
    private final NoteMapper noteMapper;

    /**
     * 向量化一篇笔记并写入 Milvus（先删旧块再插入新块）。
     * 失败记录告警并静默，不阻塞主流程。
     * 注意：本方法仅一次只读查询 + 外部网络调用（Embedding/Milvus），
     * 不加 @Transactional，避免秒级网络 IO 长时间占用数据库连接池。
     */
    public boolean vectorize(Long noteId) {
        if (!embeddingService.isEnabled() || !vectorStore.isEnabled()) {
            return false;
        }
        Note note = noteMapper.selectById(noteId);
        if (note == null || note.getUserId() == null) {
            return false;
        }
        String source = StringUtils.hasText(note.getContent()) ? note.getContent() : note.getOcrText();
        List<String> chunks = NoteChunkSplitter.split(source);
        if (chunks.isEmpty()) {
            return false;
        }
        try {
            List<String> bounded = chunks.size() > MAX_CHUNKS ? chunks.subList(0, MAX_CHUNKS) : chunks;
            List<float[]> vectors = embeddingService.embedBatch(bounded);
            if (vectors == null || vectors.size() != bounded.size()) {
                return false;
            }
            // 替换旧块：先删后插
            vectorStore.deleteByNote(noteId);
            vectorStore.insert(note.getUserId(), noteId, bounded, vectors);
            return true;
        } catch (Exception ex) {
            log.warn("Note vectorize failed (fallback to keyword retrieval): noteId={} err={}", noteId, ex.getMessage());
            return false;
        }
    }

    /**
     * 向量召回：Milvus 检索（user_id 过滤 + 余弦相似度）。
     */
    public List<VectorHit> searchSimilar(Long userId, String query, int topK) {
        if (!embeddingService.isEnabled() || !vectorStore.isEnabled() || userId == null || !StringUtils.hasText(query)) {
            return new ArrayList<>();
        }
        try {
            float[] queryVector = embeddingService.embed(query.trim());
            if (queryVector == null || queryVector.length == 0) {
                return new ArrayList<>();
            }
            List<MilvusVectorStore.VectorSearchHit> hits = vectorStore.search(userId, queryVector, topK);
            List<VectorHit> result = new ArrayList<>();
            for (MilvusVectorStore.VectorSearchHit hit : hits) {
                if (hit.getNoteId() == null) {
                    continue;
                }
                result.add(new VectorHit(hit.getNoteId(), hit.getScore(), hit.getChunkText()));
            }
            return result;
        } catch (Exception ex) {
            log.warn("Vector search failed (fallback to keyword): {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    public static class VectorHit {
        private final Long noteId;
        private final double score;
        private final String chunkText;

        public VectorHit(Long noteId, double score, String chunkText) {
            this.noteId = noteId;
            this.score = score;
            this.chunkText = chunkText;
        }

        public Long getNoteId() {
            return noteId;
        }

        public double getScore() {
            return score;
        }

        public String getChunkText() {
            return chunkText;
        }
    }
}