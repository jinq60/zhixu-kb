package com.zhixu.kb.ask.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
@Slf4j
@Service
@RequiredArgsConstructor
public class AskService {

    private static final String KNOWLEDGE_SCOPE = "回答基于你的个人知识库（笔记）内容生成。";
    private static final String DISCLAIMER = "回答仅供参考，请结合原文笔记核实。";
    private static final int MAX_HISTORY_SIZE = 100;
    private static final int MAX_PAGE = 10000;

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
     * 启动时把遗留的 processing 记录标记为 failed（上次进程退出时未完成的问答）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverStaleProcessing() {
        try {
            transactionTemplate.executeWithoutResult(status ->
                    askRecordMapper.update(null, new LambdaUpdateWrapper<AskRecordEntity>()
                            .eq(AskRecordEntity::getStatus, "processing")
                            .set(AskRecordEntity::getStatus, "failed")));
        } catch (Exception ex) {
            log.warn("Recover stale ask records failed: {}", ex.getMessage());
        }
    }

    /**
     * 注意：AI 调用（可长达分钟级）不包裹在事务中，仅记录插入/更新使用短事务，
     * 避免长时间占用数据库连接池。
     */
    public AskRecord ask(Long userId, String question) {
        return askInternal(userId, question, null, null);
    }

    public AskRecord ask(Long userId, String question, String conversationId) {
        return askInternal(userId, question, conversationId, null);
    }

    public AskRecord askStreaming(Long userId, String question, Consumer<String> chunkSink) {
        return askInternal(userId, question, null, chunkSink);
    }

    public AskRecord askStreaming(Long userId, String question, String conversationId, Consumer<String> chunkSink) {
        return askInternal(userId, question, conversationId, chunkSink);
    }

    private AskRecord askInternal(Long userId, String question, String conversationId, Consumer<String> chunkSink) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户未登录");
        }
        String resolvedConversationId = StringUtils.hasText(conversationId)
                ? conversationId.trim()
                : UUID.randomUUID().toString();

        AskRecordEntity entity = new AskRecordEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setQuestion(cryptoService.encrypt(question));
        entity.setStatus("processing");
        entity.setConversationId(resolvedConversationId);
        entity.setCreatedAt(LocalDateTime.now());
        transactionTemplate.executeWithoutResult(status -> askRecordMapper.insert(entity));

        try {
            List<RetrievedNote> retrieved = noteRetrievalService.search(userId, question, 3);
            boolean knowledgeHit = retrieved != null && !retrieved.isEmpty();
            if (chunkSink != null && !knowledgeHit) {
                chunkSink.accept("（未在你的知识库中检索到相关内容，以下为通用知识回答）\n\n");
            }

            String prompt = buildPrompt(question, retrieved, resolvedConversationId, userId);
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
        } catch (Exception ex) {
            // 任何失败都要把记录置为终态，避免历史列表残留 processing 脏数据
            log.error("Ask failed: askId={}", entity.getId(), ex);
            markFailed(entity);
            if (ex instanceof BusinessException) {
                throw (BusinessException) ex;
            }
            throw new BusinessException(ResultCode.SERVER_ERROR, "问答失败，请稍后重试");
        }
    }

    private void markFailed(AskRecordEntity entity) {
        try {
            entity.setStatus("failed");
            entity.setCompletedAt(LocalDateTime.now());
            transactionTemplate.executeWithoutResult(status -> askRecordMapper.updateById(entity));
        } catch (Exception ignored) {
            // 终态写入失败不阻塞原异常抛出
        }
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

    @Transactional(readOnly = true)
    public List<AskRecord> history(Long userId, int page, int size) {
        int safePage = Math.min(Math.max(page, 1), MAX_PAGE);
        int safeSize = Math.min(Math.max(size, 1), MAX_HISTORY_SIZE);
        long offset = (long) (safePage - 1) * safeSize;
        List<AskRecordEntity> entities = askRecordMapper.selectList(
                new LambdaQueryWrapper<AskRecordEntity>()
                        .eq(AskRecordEntity::getUserId, userId)
                        .orderByDesc(AskRecordEntity::getCreatedAt)
                        .last("LIMIT " + safeSize + " OFFSET " + offset));
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

    private static final int MAX_CONTEXT_ROUNDS = 5;
    private static final int MAX_CONTEXT_CHAR_PER_TURN = 200;
    /** 最终 prompt 字符预算：超出时优先截断历史上下文，再截断知识片段 */
    private static final int MAX_PROMPT_CHARS = 10000;
    private static final int MAX_SNIPPET_CHARS = 300;

    private String buildPrompt(String question, List<RetrievedNote> retrieved, String conversationId, Long userId) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("你是个人知识库问答助手。回答要求：\n");
        prompt.append("- 如果提供了知识片段：必须直接基于片段回答；即使你补充通用知识，回答中也禁止出现“（通用知识回答）”字样（只有完全没有任何知识片段时才允许使用该标注）。\n");
        prompt.append("- 片段中没有的信息明确说“知识库中没有相关内容”，不要编造来源。\n");
        prompt.append("- 用户问题宽泛（如“聊聊X”“介绍一下X”）时：基于片段给出结构化的整体概述（分类组织要点），并在结尾简要列出知识库覆盖的子主题，引导用户选择深入方向，不要只复述片段原文。\n");
        prompt.append("- 知识片段中与问题无关的笔记内容（如明显不相关的主题）直接忽略，不要特意提及或解释。\n\n");

        // 多轮对话上下文（同会话最近几轮，帮助理解指代与延续话题）
        String context = buildConversationContext(conversationId, userId);
        if (StringUtils.hasText(context)) {
            prompt.append(context).append("\n\n");
        }

        prompt.append("用户问题：").append(question).append("\n\n");

        // 预算按最终 prompt 总长控制：已写入的指令/上下文/问题计入预算，避免总长溢出
        int snippetBudget = Math.max(0, MAX_PROMPT_CHARS - prompt.length());
        if (retrieved != null && !retrieved.isEmpty()) {
            prompt.append("相关知识片段（必须以此为准）：\n");
            for (RetrievedNote note : retrieved) {
                if (snippetBudget <= 0) {
                    break;
                }
                prompt.append("- 来源笔记《").append(note.getNoteTitle()).append("》：\n");
                if (note.getSnippets() != null) {
                    for (String snippet : note.getSnippets()) {
                        String compact = snippet == null ? "" : snippet.replaceAll("\\s+", " ").trim();
                        if (compact.length() > MAX_SNIPPET_CHARS) {
                            compact = compact.substring(0, MAX_SNIPPET_CHARS) + "…";
                        }
                        prompt.append("  ").append(compact).append("\n");
                        snippetBudget -= compact.length() + 40;
                        if (snippetBudget <= 0) {
                            break;
                        }
                    }
                }
            }
        } else {
            prompt.append("相关知识片段：当前未检索到你的知识库内容。\n");
        }
        return prompt.toString();
    }

    /**
     * 构建同会话最近几轮的对话上下文（用户问题 + 助手回答，截断控制长度）。
     */
    private String buildConversationContext(String conversationId, Long userId) {
        if (!StringUtils.hasText(conversationId) || userId == null) {
            return "";
        }
        try {
            List<AskRecordEntity> history = askRecordMapper.selectList(
                    new LambdaQueryWrapper<AskRecordEntity>()
                            .eq(AskRecordEntity::getUserId, userId)
                            .eq(AskRecordEntity::getConversationId, conversationId)
                            .eq(AskRecordEntity::getStatus, "completed")
                            .orderByDesc(AskRecordEntity::getCreatedAt)
                            .last("LIMIT " + MAX_CONTEXT_ROUNDS));
            if (history == null || history.isEmpty()) {
                return "";
            }
            Collections.reverse(history);
            StringBuilder sb = new StringBuilder("以下是近期对话上下文（仅作参考，回答当前问题时延续语气与指代即可）：\n");
            for (AskRecordEntity h : history) {
                String q = safeDecrypt(h.getQuestion());
                String a = safeDecrypt(h.getAnswer());
                // 解密失败的轮次直接跳过，避免 "[解密失败]" 占位污染 prompt 上下文
                if (StringUtils.hasText(q) && !"[解密失败]".equals(q)) {
                    sb.append("用户：").append(truncateContext(q)).append("\n");
                }
                if (StringUtils.hasText(a) && !"[解密失败]".equals(a)) {
                    sb.append("助手：").append(truncateContext(a)).append("\n");
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            log.warn("Build conversation context failed: {}", e.getMessage());
            return "";
        }
    }

    private String truncateContext(String text) {
        if (text == null) {
            return "";
        }
        String compact = text.replaceAll("\\s+", " ").trim();
        if (compact.length() > MAX_CONTEXT_CHAR_PER_TURN) {
            return compact.substring(0, MAX_CONTEXT_CHAR_PER_TURN) + "…";
        }
        return compact;
    }

    private String safeDecrypt(String cipher) {
        if (cipher == null) return null;
        try {
            return cryptoService.decrypt(cipher);
        } catch (Exception ex) {
            log.warn("Ask record decrypt failed: {}", ex.getMessage());
            return "[解密失败]";
        }
    }

    private List<String> buildRiskFlags(boolean knowledgeHit) {
        List<String> flags = new ArrayList<>();
        if (!knowledgeHit) {
            flags.add("未命中个人知识库：以上为通用知识回答，非你的笔记内容。建议补充相关笔记后重试，可获得基于个人资料的精确回答。");
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
        model.setQuestion(safeDecrypt(entity.getQuestion()));
        model.setAnswer(safeDecrypt(entity.getAnswer()));
        model.setRelatedNotes(parseRelatedNotes(entity.getRelatedNotes()));
        model.setStatus(entity.getStatus());
        model.setConfidenceLevel(entity.getConfidenceLevel());
        model.setRiskFlags(parseRiskFlags(entity.getRiskFlags()));
        model.setConversationId(entity.getConversationId());
        model.setCreatedAt(entity.getCreatedAt());
        model.setCompletedAt(entity.getCompletedAt());
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
