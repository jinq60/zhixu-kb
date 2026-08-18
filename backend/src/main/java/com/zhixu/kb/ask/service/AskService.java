package com.zhixu.kb.ask.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.ai.AIEngineAdapterRouter;
import com.zhixu.kb.ask.entity.AskRecordEntity;
import com.zhixu.kb.ask.mapper.AskRecordMapper;
import com.zhixu.kb.ask.model.AskExportPayload;
import com.zhixu.kb.ask.model.AskRecord;
import com.zhixu.kb.ask.model.RetrievedNote;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.security.CryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 知识问答服务：检索用户自己的笔记（个人知识库）→ 生成回答（SSE 流式 / 同步）。
 */
@Service
@RequiredArgsConstructor
public class AskService {

    private static final String KNOWLEDGE_SCOPE = "回答基于你的个人知识库（笔记）内容生成。";
    private static final String DISCLAIMER = "回答仅供参考，请结合原文笔记核实。";

    private final NoteRetrievalService noteRetrievalService;
    private final AIEngineAdapterRouter adapterRouter;
    private final AskRecordMapper askRecordMapper;
    private final ObjectMapper objectMapper;
    private final CryptoService cryptoService;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    private org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    @javax.annotation.PostConstruct
    public void initTx() {
        this.transactionTemplate = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    /**
     * 注意：AI 调用（可长达分钟级）不包裹在事务中，仅记录插入/更新使用短事务，
     * 避免长时间占用数据库连接池。
     */
    public AskRecord ask(Long userId, String question) {
        return askInternal(userId, question, null);
    }

    public AskRecord askStreaming(Long userId, String question, Consumer<String> chunkSink) {
        return askInternal(userId, question, chunkSink);
    }

    private AskRecord askInternal(Long userId, String question, Consumer<String> chunkSink) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录");
        }

        AskRecordEntity entity = new AskRecordEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setQuestion(cryptoService.encrypt(question));
        entity.setStatus("processing");
        entity.setCreatedAt(LocalDateTime.now());
        transactionTemplate.executeWithoutResult(status -> askRecordMapper.insert(entity));

        List<RetrievedNote> retrieved = noteRetrievalService.search(userId, question, 3);
        boolean knowledgeHit = retrieved != null && !retrieved.isEmpty();
        if (chunkSink != null && !knowledgeHit) {
            chunkSink.accept("[知识库提示] 当前问题未命中你的知识库，以下回答基于模型通用能力，仅供参考。\n\n");
        }

        String prompt = buildPrompt(question, retrieved);
        String answer;
        if (chunkSink != null) {
            StringBuilder streamed = new StringBuilder();
            adapterRouter.generateStreamResponseResilient(prompt, Collections.emptyMap(), chunk -> {
                if (chunk == null || chunk.length() == 0) {
                    return;
                }
                streamed.append(chunk);
                chunkSink.accept(chunk);
            });
            answer = streamed.toString();
        } else {
            answer = adapterRouter.generateResponseResilient(prompt, Collections.emptyMap());
        }
        if (answer == null) {
            answer = "";
        }

        List<String> riskFlags = buildRiskFlags(knowledgeHit);
        if (!riskFlags.isEmpty()) {
            String riskSummary = "\n\n提示：\n- " + String.join("\n- ", riskFlags);
            answer = answer + riskSummary;
            if (chunkSink != null) {
                for (String chunk : chunkAnswer(riskSummary, 30)) {
                    chunkSink.accept(chunk);
                }
            }
        }

        if (!StringUtils.hasText(answer)) {
            answer = DISCLAIMER + "\n" + KNOWLEDGE_SCOPE;
            if (chunkSink != null) {
                chunkSink.accept(answer);
            }
        }

        entity.setAnswer(cryptoService.encrypt(answer));
        entity.setStatus("completed");
        entity.setCompletedAt(LocalDateTime.now());
        entity.setRelatedNotes(toJson(toRelatedNotes(retrieved)));
        entity.setConfidenceLevel(knowledgeHit ? "NORMAL" : "LOW");
        entity.setRiskFlags(toJson(riskFlags));
        transactionTemplate.executeWithoutResult(status -> askRecordMapper.updateById(entity));

        return toModel(entity);
    }

    @Transactional(readOnly = true)
    public AskRecord getById(Long userId, String askId) {
        AskRecordEntity entity = askRecordMapper.selectOne(new LambdaQueryWrapper<AskRecordEntity>()
                .eq(AskRecordEntity::getId, askId)
                .eq(AskRecordEntity::getUserId, userId));
        if (entity == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "问答记录不存在");
        }
        return toModel(entity);
    }

    private static final int MAX_HISTORY_SIZE = 100;

    @Transactional(readOnly = true)
    public List<AskRecord> history(Long userId, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), MAX_HISTORY_SIZE);
        List<AskRecordEntity> entities = askRecordMapper.selectList(
                new LambdaQueryWrapper<AskRecordEntity>()
                        .eq(AskRecordEntity::getUserId, userId)
                        .orderByDesc(AskRecordEntity::getCreatedAt)
                        .last("LIMIT " + safeSize + " OFFSET " + (safePage - 1) * safeSize));
        return entities.stream().map(this::toModel).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public AskExportPayload exportById(Long userId, String askId) {
        AskRecord record = getById(userId, askId);
        StringBuilder content = new StringBuilder();
        content.append("# 知识问答导出\n\n");
        content.append("问答ID：").append(record.getId()).append("\n");
        content.append("创建时间：").append(record.getCreatedAt()).append("\n");
        content.append("可信度：").append(record.getConfidenceLevel() == null ? "UNKNOWN" : record.getConfidenceLevel()).append("\n\n");
        content.append("## 用户问题\n");
        content.append(record.getQuestion() == null ? "" : record.getQuestion()).append("\n\n");
        content.append("## 系统回答\n");
        content.append(record.getAnswer() == null ? "" : record.getAnswer()).append("\n\n");
        content.append("## 引用笔记\n");
        List<AskRecord.RelatedNote> related = record.getRelatedNotes();
        if (related == null || related.isEmpty()) {
            content.append("- 无\n");
        } else {
            for (AskRecord.RelatedNote note : related) {
                content.append("- ").append(note.getNoteTitle() == null ? "" : note.getNoteTitle()).append("\n");
            }
        }
        AskExportPayload payload = new AskExportPayload();
        payload.setFileName("ask-" + record.getId() + ".md");
        payload.setContent(content.toString());
        return payload;
    }

    @Transactional
    public void deleteById(Long userId, String askId) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录");
        }
        int rows = askRecordMapper.delete(new LambdaQueryWrapper<AskRecordEntity>()
                .eq(AskRecordEntity::getId, askId)
                .eq(AskRecordEntity::getUserId, userId));
        if (rows <= 0) {
            throw new BusinessException(ResultCode.NOT_FOUND, "问答记录不存在");
        }
    }

    private String buildPrompt(String question, List<RetrievedNote> retrieved) {
        StringBuilder context = new StringBuilder();
        if (retrieved != null) {
            for (RetrievedNote note : retrieved) {
                context.append("- 来源笔记《").append(note.getNoteTitle()).append("》：\n");
                if (note.getSnippets() != null) {
                    for (String snippet : note.getSnippets()) {
                        context.append("  ").append(snippet).append("\n");
                    }
                }
            }
        }
        if (context.length() == 0) {
            context.append("当前未检索到知识库内容；你必须明确告知该限制，避免编造来源。\n");
        }
        return "你是个人知识库问答助手。请优先基于以下知识片段回答用户问题；"
                + "知识片段不足时明确说明，不要编造内容。\n\n"
                + "用户问题：" + question + "\n\n"
                + "相关知识片段：\n" + context;
    }

    private List<String> buildRiskFlags(boolean knowledgeHit) {
        List<String> flags = new ArrayList<>();
        if (!knowledgeHit) {
            flags.add("低可信：未命中个人知识库，建议补充相关笔记后重试。");
        }
        return flags;
    }

    private List<AskRecord.RelatedNote> toRelatedNotes(List<RetrievedNote> retrieved) {
        List<AskRecord.RelatedNote> out = new ArrayList<>();
        if (retrieved == null) {
            return out;
        }
        for (RetrievedNote note : retrieved) {
            AskRecord.RelatedNote related = new AskRecord.RelatedNote();
            related.setNoteId(note.getNoteId());
            related.setNoteTitle(note.getNoteTitle());
            related.setSimilarity(note.getSimilarity());
            out.add(related);
        }
        return out;
    }

    private AskRecord toModel(AskRecordEntity entity) {
        AskRecord model = new AskRecord();
        model.setId(entity.getId());
        model.setUserId(entity.getUserId());
        model.setQuestion(cryptoService.decrypt(entity.getQuestion()));
        model.setAnswer(cryptoService.decrypt(entity.getAnswer()));
        model.setRelatedNotes(parseRelatedNotes(entity.getRelatedNotes()));
        model.setStatus(entity.getStatus());
        model.setCreatedAt(entity.getCreatedAt());
        model.setCompletedAt(entity.getCompletedAt());
        model.setConfidenceLevel(entity.getConfidenceLevel());
        model.setRiskFlags(parseRiskFlags(entity.getRiskFlags()));
        return model;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private List<AskRecord.RelatedNote> parseRelatedNotes(String json) {
        if (json == null || json.trim().length() == 0) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<AskRecord.RelatedNote>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private List<String> parseRiskFlags(String json) {
        if (json == null || json.trim().length() == 0) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private List<String> chunkAnswer(String answer, int chunkSize) {
        if (answer == null || answer.length() == 0) {
            return Collections.singletonList("");
        }
        int size = Math.max(chunkSize, 1);
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < answer.length(); i += size) {
            chunks.add(answer.substring(i, Math.min(i + size, answer.length())));
        }
        return chunks;
    }
}
