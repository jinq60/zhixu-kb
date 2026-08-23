package com.zhixu.kb.note.service;

import com.zhixu.kb.ai.AIEngineAdapterRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 智能清洗（文档处理管线的核心阶段）：
 * 把解析后的文本片段交给 LLM 重排——修复断行/碎片/乱序，统一为干净的分段纯文本。
 * 硬约束：不删减内容、不改写语义，只调整结构与格式。
 * 降级策略：AI 引擎不可用（平台额度耗尽/未配置 Key）时降级为确定性清洗（毫秒级，
 * 不调用 AI），保证文档上传 → 解析 → 写回正文的管线不中断；瞬时失败仍抛异常由调度器按 Chunk 重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmCleanService {

    private final AIEngineAdapterRouter adapterRouter;
    private final DocumentNormalizeService documentNormalizeService;

    public String clean(String rawChunk) {
        if (!StringUtils.hasText(rawChunk)) {
            return "";
        }
        String prompt = "你是文档清洗助手。请对下面的文档片段做机械性清洗，硬性要求：\n"
                + "1. 保留全部正文信息内容，绝不删减、合并或改写任何信息（包括数字、代码、专有名词）。\n"
                + "2. 修复：断行错位（把被截断的行按语义拼接）、OCR 碎片、多余空行、异常换行。\n"
                + "3. 章节/小节标题行必须全部用 Markdown 标题输出（# 一级标题、## 二级标题、### 三级标题），"
                + "禁止把标题当作普通段落；标题文本去掉编号前缀（如\"1. 简介\"输出为\"# 简介\"）。"
                + "正文段落不要使用任何 Markdown 标记。\n"
                + "4. 删除明显的推广/水印性噪音（如\"适合手机阅读\"\"扫码关注\"\"版权归原作者所有\"等）；"
                + "删除文档开头的聚合说明块（如\"【1阶段-… · 手机阅读版 九篇系列第 1 篇 · 由 7 个主题文档聚合生成（日期）\"、"
                + "\"共 7 个主题 · 全文约 3146 行\"这类描述系列/篇数/行数的信息行），正文其余内容一律保留。\n"
                + "5. 不要添加任何解释、总结或额外文字，直接输出清洗后的文本。\n\n"
                + "文档片段：\n" + rawChunk;
        // 限制输出长度：清洗只需重排结构，控制单次生成时间（挂起的端点也会更快触发超时切换）
        Map<String, Object> params = new HashMap<>();
        params.put("max_tokens", 4096);
        String cleaned = adapterRouter.generateResponseResilient(prompt, params);
        if (!StringUtils.hasText(cleaned)) {
            // 空响应（引擎异常）：抛异常交给调度器按块重试，而非立即降级
            throw new IllegalStateException("llm clean returned empty");
        }
        if (!looksLikeFallback(cleaned)) {
            return cleaned.trim();
        }
        // 引擎不可用（降级文案）：确定性清洗兜底，保证文档管线可用
        String deterministic = documentNormalizeService.normalize(rawChunk);
        if (!StringUtils.hasText(deterministic)) {
            throw new IllegalStateException("ai engine unavailable, deterministic fallback empty, clean skipped");
        }
        log.warn("ai engine unavailable, clean degraded to deterministic normalization");
        return deterministic;
    }

    private boolean looksLikeFallback(String text) {
        if (text == null) {
            return true;
        }
        return text.toLowerCase().startsWith(com.zhixu.kb.ai.AIEngineAdapterRouter.FALLBACK_MARKER);
    }
}