package com.zhixu.kb.graph.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.ai.AIEngineAdapterRouter;
import com.zhixu.kb.graph.model.GraphEdge;
import com.zhixu.kb.graph.model.GraphNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 图谱实体关系抽取：AI（DeepSeek 结构化 JSON）优先，失败降级为规则抽取。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphExtractionService {

    private static final int MAX_ENTITIES = 50;
    private static final int MAX_RELATIONS = 100;
    private static final Pattern LAW_PATTERN = Pattern.compile("《([^》]{2,30})》");
    private static final Set<String> STOPWORDS = new HashSet<>();

    private final AIEngineAdapterRouter adapterRouter;
    private final ObjectMapper objectMapper;

    static {
        String[] words = {"这个", "以及", "其中", "包括", "可以", "应当", "不得", "进行", "规定", "根据", "如果", "同时",
                "以下", "上述", "如下", "相关", "但是", "或者", "并且", "对于", "通过", "以及", "或者", "以及"};
        Collections.addAll(STOPWORDS, words);
    }

    public Map<String, Object> extract(String text) {
        if (!StringUtils.hasText(text)) {
            return emptyResult();
        }
        String trimmed = text.length() > 8000 ? text.substring(0, 8000) : text;
        Map<String, Object> aiResult = extractByAi(trimmed);
        if (aiResult != null && !((List<?>) aiResult.get("entities")).isEmpty()) {
            return aiResult;
        }
        return extractByRules(trimmed);
    }

    private Map<String, Object> extractByAi(String text) {
        try {
            String prompt = "请从下面文本中抽取知识图谱实体与关系。\n"
                    + "1. 实体 entities：概念、术语、法律/法规名称、人物、机构、方法等，字段：name(实体名), type(类型枚举：概念/法律/人物/机构/方法/其他), description(一句话解释，不超过30字)\n"
                    + "2. 关系 relations：实体间的语义关系，字段：source(源实体名), target(目标实体名), relation(关系描述，2-8个汉字，如\"包含\"\"依据\"\"提出\"\"相关于\")\n"
                    + "只输出 JSON，不要输出任何其他文字，格式：{\"entities\":[{\"name\":\"\",\"type\":\"\",\"description\":\"\"}],\"relations\":[{\"source\":\"\",\"target\":\"\",\"relation\":\"\"}]}\n\n"
                    + "文本：\n" + text;
            String response = adapterRouter.generateResponseResilient(prompt, new HashMap<>());
            if (!StringUtils.hasText(response)) {
                return null;
            }
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);
            List<GraphNode> entities = new ArrayList<>();
            List<GraphEdge> relations = new ArrayList<>();

            JsonNode entityNodes = root.path("entities");
            if (entityNodes.isArray()) {
                Set<String> seen = new HashSet<>();
                for (JsonNode node : entityNodes) {
                    if (entities.size() >= MAX_ENTITIES) {
                        break;
                    }
                    String name = node.path("name").asText("").trim();
                    if (name.length() == 0 || name.length() > 30 || !seen.add(name)) {
                        continue;
                    }
                    GraphNode n = new GraphNode();
                    n.setName(name);
                    n.setType(sanitizeType(node.path("type").asText("其他")));
                    n.setDescription(node.path("description").asText("").trim());
                    entities.add(n);
                }
            }

            JsonNode relationNodes = root.path("relations");
            if (relationNodes.isArray()) {
                Set<String> seen = new HashSet<>();
                for (JsonNode node : relationNodes) {
                    if (relations.size() >= MAX_RELATIONS) {
                        break;
                    }
                    String source = node.path("source").asText("").trim();
                    String target = node.path("target").asText("").trim();
                    String relation = node.path("relation").asText("").trim();
                    if (source.length() == 0 || target.length() == 0 || relation.length() == 0) {
                        continue;
                    }
                    if (source.length() > 30 || target.length() > 30 || relation.length() > 20) {
                        continue;
                    }
                    if (seen.add(source + "|" + relation + "|" + target)) {
                        GraphEdge edge = new GraphEdge();
                        edge.setSource(source);
                        edge.setTarget(target);
                        edge.setRelation(relation);
                        relations.add(edge);
                    }
                }
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("entities", entities);
            result.put("relations", relations);
            result.put("source", "ai");
            return result;
        } catch (Exception ex) {
            log.warn("graph AI extraction failed, fallback to rules: {}", ex.getMessage());
            return null;
        }
    }

    private Map<String, Object> extractByRules(String text) {
        List<GraphNode> entities = new ArrayList<>();
        List<GraphEdge> relations = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        Matcher lawMatcher = LAW_PATTERN.matcher(text);
        while (lawMatcher.find() && entities.size() < MAX_ENTITIES) {
            String name = lawMatcher.group(1).trim();
            if (name.length() < 2 || !seen.add(name)) {
                continue;
            }
            GraphNode n = new GraphNode();
            n.setName(name);
            n.setType("法律");
            n.setDescription("法律法规");
            entities.add(n);
        }

        Map<String, Integer> freq = new LinkedHashMap<>();
        String normalized = text.replaceAll("[\\s\\p{Punct}]", "");
        for (int i = 0; i + 2 <= normalized.length(); i++) {
            String gram2 = normalized.substring(i, i + 2);
            freq.put(gram2, freq.getOrDefault(gram2, 0) + 1);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(freq.entrySet());
        entries.sort((a, b) -> b.getValue() - a.getValue());
        int added = 0;
        for (Map.Entry<String, Integer> entry : entries) {
            if (entities.size() >= MAX_ENTITIES || added >= 12) {
                break;
            }
            String word = entry.getKey();
            if (entry.getValue() < 3 || STOPWORDS.contains(word) || word.length() < 2) {
                continue;
            }
            if (isNumberLike(word) || isPurelyPunct(word) || !seen.add(word)) {
                continue;
            }
            GraphNode n = new GraphNode();
            n.setName(word);
            n.setType("概念");
            n.setDescription("高频概念");
            entities.add(n);
            added++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entities", entities);
        result.put("relations", relations);
        result.put("source", "rule");
        return result;
    }

    private Map<String, Object> emptyResult() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entities", Collections.emptyList());
        result.put("relations", Collections.emptyList());
        result.put("source", "empty");
        return result;
    }

    private String extractJson(String response) {
        int start = response.indexOf('{');
        int end = response.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return response.substring(start, end + 1);
        }
        return response;
    }

    private String sanitizeType(String raw) {
        String value = raw == null ? "" : raw.trim();
        if (value.length() == 0 || value.length() > 10) {
            return "其他";
        }
        return value;
    }

    private boolean isNumberLike(String word) {
        return word.matches(".*\\d.*");
    }

    private boolean isPurelyPunct(String word) {
        return !word.matches(".*[\\u4E00-\\u9FA5A-Za-z].*");
    }
}
