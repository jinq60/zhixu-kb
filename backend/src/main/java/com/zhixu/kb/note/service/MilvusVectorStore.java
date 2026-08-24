package com.zhixu.kb.note.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.zhixu.kb.config.MilvusProperties;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.ConsistencyLevel;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.AddFieldReq;
import io.milvus.v2.service.collection.request.CreateCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import io.milvus.v2.service.collection.request.LoadCollectionReq;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.UpsertReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Milvus 向量库访问封装（V2 SDK）：集合管理（建集合/索引/加载）、写入（插入/删除）、向量检索。
 * 不可用时抛异常，由调用方降级为关键词检索。
 */
@Slf4j
@Component
public class MilvusVectorStore {

    private final MilvusProperties properties;
    private volatile MilvusClientV2 client;

    public MilvusVectorStore(MilvusProperties properties) {
        this.properties = properties;
    }

    private MilvusClientV2 client() {
        MilvusClientV2 c = client;
        if (c == null) {
            synchronized (this) {
                if (client == null) {
                    client = createClient();
                }
                c = client;
            }
        }
        return c;
    }

    private MilvusClientV2 createClient() {
        io.milvus.v2.client.ConnectConfig.ConnectConfigBuilder<?, ?> builder = ConnectConfig.builder()
                .uri(properties.getUri());
        if (StringUtils.hasText(properties.getUsername())) {
            builder.username(properties.getUsername());
        }
        if (StringUtils.hasText(properties.getPassword())) {
            builder.password(properties.getPassword());
        }
        return new MilvusClientV2(builder.build());
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * 读取现有集合向量字段的维度（无法确定返回 -1，跳过重建判断）。
     */
    private int readCollectionDimension(String name) {
        try {
            io.milvus.v2.service.collection.response.DescribeCollectionResp resp = client().describeCollection(
                    io.milvus.v2.service.collection.request.DescribeCollectionReq.builder()
                            .collectionName(name).build());
            if (resp == null || resp.getCollectionSchema() == null) {
                return -1;
            }
            java.util.List<String> vectorFields = resp.getVectorFieldNames();
            String vectorFieldName = (vectorFields == null || vectorFields.isEmpty()) ? "vector" : vectorFields.get(0);
            io.milvus.v2.service.collection.request.CreateCollectionReq.FieldSchema field =
                    resp.getCollectionSchema().getField(vectorFieldName);
            if (field != null && field.getDimension() != null) {
                return field.getDimension();
            }
        } catch (Exception ex) {
            log.warn("Describe milvus collection failed (skip dimension check): {}", ex.getMessage());
        }
        return -1;
    }

    /**
     * 启动时确保集合与索引存在（幂等）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void ensureCollection() {
        if (!isEnabled()) {
            return;
        }
        try {
            ensureCollectionInternal();
        } catch (Exception ex) {
            log.warn("Milvus ensure collection failed (vector search degraded to keyword): {}", ex.getMessage());
        }
    }

    private void ensureCollectionInternal() {
        String name = properties.getCollectionName();
        boolean exists = Boolean.TRUE.equals(client().hasCollection(HasCollectionReq.builder()
                .collectionName(name).build()));
        if (exists) {
            // 维度校验：embedding 模型更换导致维度变化时自动重建集合（向量数据由启动回填/笔记编辑自动恢复）
            int existingDim = readCollectionDimension(name);
            if (existingDim > 0 && existingDim != properties.getDimension()) {
                log.warn("Milvus collection dimension mismatch (existing={} configured={}), dropping and recreating: {}",
                        existingDim, properties.getDimension(), name);
                try {
                    client().dropCollection(io.milvus.v2.service.collection.request.DropCollectionReq.builder()
                            .collectionName(name).build());
                    exists = false;
                } catch (Exception ex) {
                    log.warn("Drop milvus collection failed: {}", ex.getMessage());
                }
            }
        }
        if (!exists) {
            CreateCollectionReq.CollectionSchema schema = client().createSchema();
            schema.addField(AddFieldReq.builder()
                    .fieldName("id").dataType(DataType.Int64).isPrimaryKey(true).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("note_id").dataType(DataType.Int64).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("user_id").dataType(DataType.Int64).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("chunk_index").dataType(DataType.Int64).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("chunk_text").dataType(DataType.VarChar).maxLength(65535).build());
            schema.addField(AddFieldReq.builder()
                    .fieldName("vector").dataType(DataType.FloatVector).dimension(properties.getDimension()).build());

            client().createCollection(CreateCollectionReq.builder()
                    .collectionName(name)
                    .collectionSchema(schema)
                    .build());
            // FLAT 暴力检索：个人知识库为小数据集（单用户通常 <1 万块向量），
            // Milvus 官方建议此规模用 FLAT 保证 100% 召回；IVF_FLAT(nlist=128) 在
            // 小集合 + user_id 过滤场景下受 nprobe 影响可能漏召回甚至空结果。
            // 数据量增长到百万级时再评估切换 IVF/HNSW。
            client().createIndex(CreateIndexReq.builder()
                    .collectionName(name)
                    .indexParams(Collections.singletonList(IndexParam.builder()
                            .indexName("vector_idx")
                            .fieldName("vector")
                            .indexType(IndexParam.IndexType.FLAT)
                            .metricType(IndexParam.MetricType.COSINE)
                            .build()))
                    .build());
            log.info("Milvus collection created (FLAT index): {}", name);
        } else {
            ensureFlatIndex(name);
        }
        client().loadCollection(LoadCollectionReq.builder().collectionName(name).build());
    }

    /**
     * 存量集合索引类型检查：历史版本创建的 IVF_FLAT 集合迁移为 FLAT（重建索引，向量保留），
     * 保证召回率与本项目目标规模匹配。失败仅告警，不阻塞服务。
     */
    private void ensureFlatIndex(String name) {
        try {
            java.util.List<String> indexNames = client().listIndexes(
                    io.milvus.v2.service.index.request.ListIndexesReq.builder().collectionName(name).build());
            if (indexNames == null || indexNames.isEmpty()) {
                return;
            }
            io.milvus.v2.service.index.response.DescribeIndexResp indexDesc = client().describeIndex(
                    io.milvus.v2.service.index.request.DescribeIndexReq.builder()
                            .collectionName(name)
                            .indexName(indexNames.get(0))
                            .build());
            String currentType = null;
            if (indexDesc != null) {
                io.milvus.v2.service.index.response.DescribeIndexResp.IndexDesc desc =
                        indexDesc.getIndexDescByFieldName("vector");
                if (desc != null && desc.getIndexType() != null) {
                    currentType = String.valueOf(desc.getIndexType());
                }
            }
            if ("FLAT".equalsIgnoreCase(currentType)) {
                return;
            }
            log.warn("Milvus collection {} uses legacy index ({}), rebuilding as FLAT for full recall",
                    name, currentType);
            // Milvus 约束：集合处于 loaded 状态时禁止删除索引，需先释放
            try {
                client().releaseCollection(io.milvus.v2.service.collection.request.ReleaseCollectionReq.builder()
                        .collectionName(name)
                        .build());
            } catch (Exception ignored) {
                // 释放失败不阻塞（可能本就未加载）
            }
            client().dropIndex(io.milvus.v2.service.index.request.DropIndexReq.builder()
                    .collectionName(name)
                    .indexName(indexNames.get(0))
                    .build());
            client().createIndex(CreateIndexReq.builder()
                    .collectionName(name)
                    .indexParams(Collections.singletonList(IndexParam.builder()
                            .indexName("vector_idx")
                            .fieldName("vector")
                            .indexType(IndexParam.IndexType.FLAT)
                            .metricType(IndexParam.MetricType.COSINE)
                            .build()))
                    .build());
            log.info("Milvus index rebuilt as FLAT: {}", name);
        } catch (Exception ex) {
            log.warn("Milvus ensure flat index failed (keep existing): {}", ex.getMessage());
        }
    }

    /**
     * 插入/覆盖多块向量（显式主键 id = noteId*100000 + chunkIndex，幂等 upsert）。
     * 注意：主键必须基于真实 chunkIndex，否则不同块会互相覆盖导致笔记只剩一块向量。
     */
    public void insert(Long userId, Long noteId, List<String> chunkTexts, List<float[]> vectors) {
        if (!isEnabled() || chunkTexts == null || vectors == null || chunkTexts.size() != vectors.size()) {
            return;
        }
        List<JsonObject> rows = new ArrayList<>();
        for (int i = 0; i < chunkTexts.size(); i++) {
            float[] v = vectors.get(i);
            if (v == null || v.length == 0) {
                continue;
            }
            rows.add(buildRow(userId, noteId, i, chunkTexts.get(i), v));
        }
        upsert(rows);
    }

    /**
     * 插入单块向量（任务流按块推进时使用）：主键使用真实 chunkIndex，
     * 避免多块同主键互相覆盖。
     */
    public void insertChunk(Long userId, Long noteId, int chunkIndex, String chunkText, float[] vector) {
        if (!isEnabled() || userId == null || noteId == null || vector == null || vector.length == 0) {
            return;
        }
        upsert(Collections.singletonList(buildRow(userId, noteId, chunkIndex, chunkText, vector)));
    }

    private JsonObject buildRow(Long userId, Long noteId, int chunkIndex, String chunkText, float[] v) {
        JsonObject row = new JsonObject();
        row.addProperty("id", primaryKey(noteId, chunkIndex));
        row.addProperty("note_id", noteId);
        row.addProperty("user_id", userId);
        row.addProperty("chunk_index", chunkIndex);
        row.addProperty("chunk_text", chunkText);
        JsonArray vector = new JsonArray();
        for (float f : v) {
            vector.add(f);
        }
        row.add("vector", vector);
        return row;
    }

    /**
     * 稳定向量主键：noteId 与 chunkIndex 的确定性 62 位混合。
     * 不能用 noteId*100000+chunkIndex：雪花 ID 为 19 位，乘法必然溢出 Int64。
     * 混合结果确定 => 同 (noteId, chunkIndex) 幂等 upsert；62 位空间下碰撞概率可忽略，
     * 且按笔记删除走 note_id 字段过滤，不依赖主键结构。
     */
    private static long primaryKey(long noteId, long chunkIndex) {
        long h = noteId * 0x9E3779B97F4A7C15L + chunkIndex * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        h *= 0x94D049BB133111EBL;
        h ^= h >>> 29;
        return h & 0x3FFFFFFFFFFFFFFFL;
    }

    private void upsert(List<JsonObject> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        try {
            client().upsert(UpsertReq.builder()
                    .collectionName(properties.getCollectionName())
                    .data(rows)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("milvus upsert failed: " + ex.getMessage(), ex);
        }
    }

    /**
     * 删除某笔记的全部向量块。
     */
    public void deleteByNote(Long noteId) {
        deleteByNote(noteId, null);
    }

    /**
     * 删除某笔记的向量块（保留前 keepChunks 块）。
     * 用于"先插新块、再清旧块"的替换流程：只清理 chunk_index >= keepChunks 的残留块，
     * 避免误删刚写入的新向量。keepChunks 为 null 时删除全部。
     */
    public void deleteByNote(Long noteId, Integer keepChunks) {
        if (!isEnabled() || noteId == null) {
            return;
        }
        String filter = "note_id == " + noteId
                + (keepChunks == null ? "" : " && chunk_index >= " + keepChunks);
        try {
            client().delete(DeleteReq.builder()
                    .collectionName(properties.getCollectionName())
                    .filter(filter)
                    .build());
        } catch (Exception ex) {
            log.warn("Milvus delete failed (ignore): {}", ex.getMessage());
        }
    }

    /**
     * 笔记是否已存在向量（用于启动回填跳过已向量化笔记，避免每次重启全库重嵌）。
     * 查询失败按"无向量"处理，由回填重新嵌入（幂等）。
     */
    public boolean hasVectors(Long noteId) {
        if (!isEnabled() || noteId == null) {
            return false;
        }
        return !existingNoteIds(Collections.singletonList(noteId)).isEmpty();
    }

    /**
     * 批量查询已存在向量的笔记 ID（note_id in [...] 分批过滤），
     * 供启动回填使用，替代逐笔记一次网络往返的 hasVectors（N 次调用 → N/200 次）。
     */
    public java.util.Set<Long> existingNoteIds(List<Long> noteIds) {
        java.util.Set<Long> result = new java.util.HashSet<>();
        if (!isEnabled() || noteIds == null || noteIds.isEmpty()) {
            return result;
        }
        int batch = 200;
        for (int i = 0; i < noteIds.size(); i += batch) {
            List<Long> sub = noteIds.subList(i, Math.min(i + batch, noteIds.size()));
            StringBuilder filter = new StringBuilder("note_id in [");
            for (int j = 0; j < sub.size(); j++) {
                if (j > 0) {
                    filter.append(',');
                }
                filter.append(sub.get(j));
            }
            filter.append(']');
            try {
                io.milvus.v2.service.vector.response.QueryResp resp = client().query(
                        io.milvus.v2.service.vector.request.QueryReq.builder()
                                .collectionName(properties.getCollectionName())
                                .filter(filter.toString())
                                .outputFields(Collections.singletonList("note_id"))
                                .limit(Math.min(16000, sub.size() * MAX_CHUNKS_PER_NOTE))
                                .build());
                if (resp != null && resp.getQueryResults() != null) {
                    for (io.milvus.v2.service.vector.response.QueryResp.QueryResult qr : resp.getQueryResults()) {
                        Object v = qr.getEntity() == null ? null : qr.getEntity().get("note_id");
                        if (v instanceof Number) {
                            result.add(((Number) v).longValue());
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("Milvus existingNoteIds query failed (treat as none): err={}", ex.getMessage());
            }
        }
        return result;
    }

    /** 单篇笔记最大块数（与 NoteEmbeddingService.MAX_CHUNKS 对齐的批量查询 limit 估算基数） */
    private static final int MAX_CHUNKS_PER_NOTE = 64;

    /**
     * 向量检索：按 user_id 过滤 + 余弦相似度 topK。
     */
    public List<VectorSearchHit> search(Long userId, float[] queryVector, int topK) {
        if (!isEnabled() || userId == null || queryVector == null || queryVector.length == 0) {
            return Collections.emptyList();
        }
        SearchResp resp = client().search(SearchReq.builder()
                .collectionName(properties.getCollectionName())
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .filter("user_id == " + userId)
                .topK(Math.max(1, Math.min(topK, 100)))
                .outputFields(java.util.Arrays.asList("note_id", "chunk_text", "chunk_index"))
                .metricType(IndexParam.MetricType.COSINE)
                // BOUNDED 一致性足够：RAG 检索容忍秒级可见性延迟，STRONG 会显著增加检索延迟
                .consistencyLevel(ConsistencyLevel.BOUNDED)
                .build());
        if (resp == null || resp.getSearchResults() == null || resp.getSearchResults().isEmpty()) {
            return Collections.emptyList();
        }
        List<SearchResp.SearchResult> results = resp.getSearchResults().get(0);
        List<VectorSearchHit> hits = new ArrayList<>();
        for (SearchResp.SearchResult result : results) {
            Map<String, Object> entity = result.getEntity();
            Object noteIdObj = entity == null ? null : entity.get("note_id");
            if (!(noteIdObj instanceof Number)) {
                continue;
            }
            Long noteId = ((Number) noteIdObj).longValue();
            Object textObj = entity == null ? null : entity.get("chunk_text");
            String chunkText = textObj == null ? "" : String.valueOf(textObj);
            hits.add(new VectorSearchHit(noteId, result.getScore(), chunkText));
        }
        return hits;
    }

    public static class VectorSearchHit {
        private final Long noteId;
        private final double score;
        private final String chunkText;

        public VectorSearchHit(Long noteId, double score, String chunkText) {
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