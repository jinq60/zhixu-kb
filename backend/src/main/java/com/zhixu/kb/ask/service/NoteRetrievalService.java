package com.zhixu.kb.ask.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    private static final int MAX_SNIPPET_LENGTH = 300;
    private static final int MAX_SNIPPETS_PER_NOTE = 2;
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

    private final NoteMapper noteMapper;

    /**
     * 在指定用户的笔记库中检索，返回 topK 篇笔记及其相关片段。
     */
    public List<RetrievedNote> search(Long userId, String query, int topK) {
        if (userId == null || !StringUtils.hasText(query)) {
            return Collections.emptyList();
        }
        int limit = Math.max(1, Math.min(topK, 10));

        List<Note> notes = noteMapper.selectList(new LambdaQueryWrapper<Note>()
                .eq(Note::getUserId, userId)
                .eq(Note::getIsDeleted, 0)
                .last("LIMIT 200"));

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
            if (score <= 0) {
                continue;
            }
            RetrievedNote retrieved = new RetrievedNote();
            retrieved.setNoteId(note.getId());
            retrieved.setNoteTitle(title);
            retrieved.setSimilarity(score);
            retrieved.setSnippets(extractSnippets(normalizedQuery, note, MAX_SNIPPETS_PER_NOTE));
            ranked.add(retrieved);
        }

        ranked.sort(Comparator.comparingDouble(RetrievedNote::getSimilarity).reversed());
        return ranked.stream().limit(limit).collect(Collectors.toList());
    }

    /**
     * 构建笔记检索语料：标题 + 摘要 + 关键词 + 去标签正文 + OCR 文本。
     */
    private String buildCorpus(Note note) {
        StringBuilder sb = new StringBuilder();
        sb.append(note.getTitle() == null ? "" : note.getTitle()).append(' ');
        sb.append(note.getSummary() == null ? "" : note.getSummary()).append(' ');
        sb.append(note.getKeywords() == null ? "" : note.getKeywords()).append(' ');
        sb.append(plainText(note.getContent())).append(' ');
        sb.append(note.getOcrText() == null ? "" : note.getOcrText());
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
