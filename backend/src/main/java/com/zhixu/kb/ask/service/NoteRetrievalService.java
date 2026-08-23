package com.zhixu.kb.ask.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhixu.kb.ask.model.RetrievedNote;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.NoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 个人知识库检索：基于当前用户的笔记（标题/摘要/关键词/正文/OCR 文本）
 * 进行关键词 + n-gram 相关性评分，返回命中笔记与相关片段。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoteRetrievalService {

    private static final int MAX_SNIPPET_LENGTH = 400;
    private static final int MAX_SNIPPETS_PER_NOTE = 3;
    private static final int MAX_RECENT_SCAN = 200;
    private static final int MAX_FULLTEXT_HITS = 100;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final NoteMapper noteMapper;
    private final com.zhixu.kb.note.service.NoteEmbeddingService noteEmbeddingService;

    /**
     * 在指定用户的笔记库中检索，返回 topK 篇笔记及其相关片段。
     * 混合召回：
     * 1) 向量召回（语义相似，embedding 可用时）—— 解决同义/近义表达检索不到的问题；
     * 2) MySQL FULLTEXT（ngram 索引）全库命中 —— 老笔记也能被检索到；
     * 3) 最近 MAX_RECENT_SCAN 篇笔记扫描 —— 覆盖全文索引未命中但关键词相关的场景；
     * 合并去重后统一在内存中按关键词 + n-gram 相关性评分。
     */
    public List<RetrievedNote> search(Long userId, String query, int topK) {
        if (userId == null || !StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        int limit = Math.max(1, Math.min(topK, 10));
        String trimmedQuery = query.trim();

        List<Note> notes = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        // 向量召回的有序笔记（相似度降序，searchSimilar 已排序）→ 供 RRF 融合
        List<Long> vectorRankedIds = new ArrayList<>();
        Map<Long, Double> vectorScoreMap = new HashMap<>();

        // 0) 向量召回（语义匹配，优先补充关键词漏掉的笔记；块文本作为精确 snippet）
        Map<Long, String> vectorSnippets = new HashMap<>();
        try {
            List<com.zhixu.kb.note.service.NoteEmbeddingService.VectorHit> vectorHits =
                    noteEmbeddingService.searchSimilar(userId, trimmedQuery, 15);
            if (vectorHits != null && !vectorHits.isEmpty()) {
                for (com.zhixu.kb.note.service.NoteEmbeddingService.VectorHit hit : vectorHits) {
                    if (hit.getNoteId() == null || seen.contains(hit.getNoteId())) {
                        continue;
                    }
                    Note n = noteMapper.selectById(hit.getNoteId());
                    if (n != null && (n.getIsDeleted() == null || n.getIsDeleted() == 0)) {
                        seen.add(n.getId());
                        notes.add(n);
                        vectorRankedIds.add(n.getId());
                        vectorScoreMap.put(n.getId(), hit.getScore());
                        if (StringUtils.hasText(hit.getChunkText()) && !vectorSnippets.containsKey(n.getId())) {
                            vectorSnippets.put(n.getId(), hit.getChunkText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Vector recall failed, skip: {}", e.getMessage());
        }

        // 1) FULLTEXT 全库命中（解决旧笔记检索不到的问题）
        try {
            List<Note> ftHits = noteMapper.selectList(new QueryWrapper<Note>()
                    .select("id", "title", "summary", "keywords", "content", "ocr_text")
                    .eq("user_id", userId)
                    .eq("is_deleted", 0)
                    .apply("MATCH(title, content) AGAINST({0} IN NATURAL LANGUAGE MODE)", trimmedQuery)
                    .orderByDesc("id")
                    .last("LIMIT " + MAX_FULLTEXT_HITS));
            if (ftHits != null) {
                for (Note n : ftHits) {
                    if (seen.add(n.getId())) {
                        notes.add(n);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("FULLTEXT search failed, fallback to recent scan only: {}", e.getMessage());
        }

        // 2) 最近笔记扫描（FULLTEXT 未覆盖 summary/keywords/ocrText 的场景）
        List<Note> recent = noteMapper.selectList(new QueryWrapper<Note>()
                .select("id", "title", "summary", "keywords", "content", "ocr_text")
                .eq("user_id", userId)
                .eq("is_deleted", 0)
                .orderByDesc("id")
                .last("LIMIT " + MAX_RECENT_SCAN));
        if (recent != null) {
            for (Note n : recent) {
                if (seen.add(n.getId())) {
                    notes.add(n);
                }
            }
        }

        if (notes.isEmpty()) {
            return Collections.emptyList();
        }

        String normalizedQuery = normalizeForMatch(query);
        List<String> terms = buildQueryTerms(normalizedQuery);
        if (terms.isEmpty()) {
            return Collections.emptyList();
        }

        List<RetrievedNote> ranked = new ArrayList<>();
        for (Note note : notes) {
            String title = note.getTitle() == null ? "" : note.getTitle();
            String corpus = buildCorpus(note);
            double score = keywordScore(normalizedQuery, terms, title, corpus);
            boolean vectorHit = vectorSnippets.containsKey(note.getId());
            // 关键词 0 分但向量命中的笔记仍保留（语义召回），交由 RRF 融合排序
            if (score <= 0 && !vectorHit) {
                continue;
            }
            RetrievedNote retrieved = new RetrievedNote();
            retrieved.setNoteId(note.getId());
            retrieved.setNoteTitle(title);
            retrieved.setSimilarity(vectorHit ? Math.max(score, vectorScoreMap.getOrDefault(note.getId(), 0D)) : score);
            String vectorSnippet = vectorSnippets.get(note.getId());
            if (StringUtils.hasText(vectorSnippet) && vectorSnippet.length() > 30) {
                // 向量命中：用语义块文本作为片段（更精准）
                List<String> snippets = new ArrayList<>();
                snippets.add(vectorSnippet.length() > MAX_SNIPPET_LENGTH
                        ? vectorSnippet.substring(0, MAX_SNIPPET_LENGTH)
                        : vectorSnippet);
                retrieved.setSnippets(snippets);
            } else {
                retrieved.setSnippets(extractSnippets(normalizedQuery, note, MAX_SNIPPETS_PER_NOTE));
            }
            ranked.add(retrieved);
        }

        // 关键词路排序（RRF 的 rank 依据）
        ranked.sort(Comparator.comparingDouble(RetrievedNote::getSimilarity).reversed());

        // RRF（Reciprocal Rank Fusion）融合向量召回与关键词召回两路排序：
        // 融合分 = Σ 1/(k + rank)，k=60；两个来源都命中的笔记得分更高
        Map<Long, Double> rrfScores = new HashMap<>();
        for (int i = 0; i < vectorRankedIds.size(); i++) {
            rrfScores.merge(vectorRankedIds.get(i), 1.0 / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < ranked.size(); i++) {
            Long noteId = ranked.get(i).getNoteId();
            if (noteId != null) {
                rrfScores.merge(noteId, 1.0 / (RRF_K + i + 1), Double::sum);
            }
        }
        ranked.forEach(r -> {
            Double fused = rrfScores.get(r.getNoteId());
            if (fused != null) {
                r.setSimilarity(fused);
            }
        });
        ranked.sort(Comparator.comparingDouble(RetrievedNote::getSimilarity).reversed());
        List<RetrievedNote> result = ranked.stream().limit(limit).collect(Collectors.toList());
        // 可观测性：记录召回明细（用户ID/两路命中笔记ID/最终输出ID/片段摘要），便于调优检索质量
        String snippetDigest = result.stream()
                .map(r -> r.getNoteId() + ":" + (r.getSnippets() != null && !r.getSnippets().isEmpty()
                        && r.getSnippets().get(0) != null
                        ? r.getSnippets().get(0).replaceAll("\\s+", " ").substring(0, Math.min(50, r.getSnippets().get(0).length()))
                        : ""))
                .collect(Collectors.joining(" | "));
        log.info("retrieval trace: userId={} query={} vector={} keyword={} returned={} snippets=[{}]",
                userId,
                query.length() > 30 ? query.substring(0, 30) : query,
                vectorRankedIds,
                ranked.stream().map(RetrievedNote::getNoteId).collect(Collectors.toList()),
                result.stream().map(RetrievedNote::getNoteId).collect(Collectors.toList()),
                snippetDigest);
        return result;
    }

    /** RRF 融合常数 */
    private static final int RRF_K = 60;

    /**
     * 构建笔记检索语料：标题 + 摘要 + 关键词 + 去标签正文 + OCR 文本。
     * 当正文本身就是 OCR 文本（content 为空时 triggerOCR 会把 OCR 文本写入 content）时去重，避免重复计分。
     */
    private String buildCorpus(Note note) {
        StringBuilder sb = new StringBuilder();
        sb.append(note.getTitle() == null ? "" : note.getTitle()).append(' ');
        sb.append(note.getSummary() == null ? "" : note.getSummary()).append(' ');
        sb.append(note.getKeywords() == null ? "" : note.getKeywords()).append(' ');
        String content = plainText(note.getContent());
        sb.append(content).append(' ');
        String ocrText = note.getOcrText() == null ? "" : note.getOcrText().trim();
        if (StringUtils.hasText(ocrText) && !normalizeForMatch(ocrText).equals(normalizeForMatch(content))) {
            sb.append(ocrText);
        }
        return sb.toString();
    }

    private String plainText(String html) {
        if (!StringUtils.hasText(html)) {
            return "";
        }
        String text = HTML_TAG_PATTERN.matcher(html).replaceAll(" ");
        text = text.replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"");
        return WHITESPACE_PATTERN.matcher(text).replaceAll(" ").trim();
    }

    private double keywordScore(String query, List<String> terms, String title, String corpus) {
        String titleNormalized = normalizeForMatch(title);
        String corpusNormalized = normalizeForMatch(corpus);

        int matched = 0;
        for (String term : terms) {
            if (corpusNormalized.contains(term)) {
                matched++;
            }
        }
        String compactQuery = query.replace(" ", "");
        String compactCorpus = corpusNormalized.replace(" ", "");
        int gramMatched = compactQuery.length() >= 4 ? countMatchedNgrams(compactQuery, compactCorpus, 2, 4, 64) : 0;

        if (matched == 0 && gramMatched == 0) {
            return 0D;
        }

        double coverage = (double) matched / Math.max(1, Math.min(terms.size(), 16));
        if (gramMatched > 0) {
            coverage += Math.min(0.35D, gramMatched * 0.03D);
        }
        if (compactQuery.length() >= 4 && compactCorpus.contains(compactQuery)) {
            coverage += 0.25D;
        }
        // 标题命中加权
        if (titleNormalized.length() > 0 && titleNormalized.contains(compactQuery)) {
            coverage += 0.3D;
        }
        return coverage;
    }

    /**
     * 提取与查询相关的正文片段。
     */
    private List<String> extractSnippets(String query, Note note, int max) {
        List<String> snippets = new ArrayList<>();
        String plain = plainText(note.getContent());
        if (!StringUtils.hasText(plain)) {
            plain = note.getOcrText() == null ? "" : note.getOcrText().trim();
        }
        if (!StringUtils.hasText(plain)) {
            plain = note.getSummary() == null ? "" : note.getSummary().trim();
        }
        if (!StringUtils.hasText(plain)) {
            return snippets;
        }

        String compactQuery = query.replace(" ", "");
        String lowerPlain = plain.toLowerCase(Locale.ROOT);

        int matchIndex = lowerPlain.indexOf(compactQuery.toLowerCase(Locale.ROOT));
        int start = 0;
        if (matchIndex >= 0) {
            start = Math.max(0, matchIndex - 40);
        }

        int end = Math.min(plain.length(), start + MAX_SNIPPET_LENGTH);
        String snippet = plain.substring(start, end).trim();
        if (snippet.length() > 0) {
            snippets.add(snippet);
        }

        if (snippets.size() < max && matchIndex >= 0 && end < plain.length()) {
            int nextStart = end;
            String second = plain.substring(nextStart, Math.min(plain.length(), nextStart + MAX_SNIPPET_LENGTH)).trim();
            if (second.length() > 20) {
                snippets.add(second);
            }
        }
        return snippets;
    }

    private int countMatchedNgrams(String compactQuery, String compactCorpus, int minLen, int maxLen, int maxTerms) {
        if (compactQuery == null || compactCorpus == null || compactQuery.length() < minLen || compactCorpus.length() < minLen) {
            return 0;
        }
        LinkedHashSet<String> grams = new LinkedHashSet<>();
        addNgrams(grams, compactQuery, minLen, maxLen, maxTerms);
        int matched = 0;
        for (String gram : grams) {
            if (gram.length() < minLen) {
                continue;
            }
            if (compactCorpus.contains(gram)) {
                matched++;
            }
        }
        return matched;
    }

    private List<String> buildQueryTerms(String normalizedQuery) {
        if (!StringUtils.hasText(normalizedQuery)) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String[] tokens = normalizedQuery.split("\\s+");
        for (String token : tokens) {
            if (token == null) {
                continue;
            }
            String cleaned = token.trim();
            if (cleaned.length() < 2) {
                continue;
            }
            terms.add(cleaned);
            if (containsChinese(cleaned)) {
                addNgrams(terms, cleaned, 2, 4, 20);
            }
        }
        if (terms.isEmpty()) {
            String compact = normalizedQuery.replace(" ", "");
            if (compact.length() >= 2) {
                terms.add(compact);
                if (containsChinese(compact)) {
                    addNgrams(terms, compact, 2, 4, 20);
                }
            }
        }
        return new ArrayList<>(terms);
    }

    private void addNgrams(Set<String> sink, String text, int minLen, int maxLen, int maxNewTerms) {
        if (text == null || text.length() < minLen || maxNewTerms <= 0) {
            return;
        }
        int added = 0;
        int upper = Math.min(maxLen, text.length());
        for (int n = upper; n >= minLen; n--) {
            for (int i = 0; i + n <= text.length(); i++) {
                String gram = text.substring(i, i + n);
                if (sink.add(gram)) {
                    added++;
                    if (added >= maxNewTerms) {
                        return;
                    }
                }
            }
        }
    }

    private boolean containsChinese(String text) {
        if (text == null || text.length() == 0) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            Character.UnicodeScript script = Character.UnicodeScript.of(text.charAt(i));
            if (script == Character.UnicodeScript.HAN) {
                return true;
            }
        }
        return false;
    }

    private String normalizeForMatch(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean prevSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            Character.UnicodeScript script = Character.UnicodeScript.of(c);
            boolean keep = Character.isLetterOrDigit(c) || script == Character.UnicodeScript.HAN;
            if (keep) {
                out.append(c);
                prevSpace = false;
            } else if (!prevSpace) {
                out.append(' ');
                prevSpace = true;
            }
        }
        return out.toString().trim();
    }
}
