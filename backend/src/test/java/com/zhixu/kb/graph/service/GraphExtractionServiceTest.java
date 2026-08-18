package com.zhixu.kb.graph.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.ai.AIEngineAdapterRouter;
import com.zhixu.kb.graph.model.GraphNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphExtractionServiceTest {

    @Mock
    private AIEngineAdapterRouter adapterRouter;

    private GraphExtractionService extractionService;

    @BeforeEach
    void setUp() {
        extractionService = new GraphExtractionService(adapterRouter, new ObjectMapper());
    }

    @Test
    void extract_emptyText_returnsEmptyResult() {
        Map<String, Object> result = extractionService.extract("  ");

        assertEquals("empty", result.get("source"));
        assertTrue(((List<?>) result.get("entities")).isEmpty());
    }

    @Test
    void extract_aiFails_fallsBackToRuleExtraction() {
        when(adapterRouter.generateResponseResilient(any(), anyMap())).thenReturn("");

        String text = "《中华人民共和国民法典》规定了合同的订立。合同是民事主体之间设立民事法律关系的协议。";
        Map<String, Object> result = extractionService.extract(text);

        assertEquals("rule", result.get("source"));
        List<GraphNode> entities = (List<GraphNode>) result.get("entities");
        assertTrue(entities.stream().anyMatch(e -> "中华人民共和国民法典".equals(e.getName())),
                "应抽取到《》中的法律实体");
    }

    @Test
    void extract_aiSucceeds_usesAiEntities() {
        String aiJson = "{\"entities\":[{\"name\":\"知识图谱\",\"type\":\"概念\",\"description\":\"一种数据结构\"}],"
                + "\"relations\":[{\"source\":\"知识图谱\",\"target\":\"笔记\",\"relation\":\"关联于\"}]}";
        when(adapterRouter.generateResponseResilient(any(), anyMap())).thenReturn("```json\n" + aiJson + "\n```");

        Map<String, Object> result = extractionService.extract("知识图谱相关笔记");

        assertEquals("ai", result.get("source"));
        List<GraphNode> entities = (List<GraphNode>) result.get("entities");
        assertEquals(1, entities.size());
        assertEquals("知识图谱", entities.get(0).getName());
        assertEquals("概念", entities.get(0).getType());
        assertEquals(1, ((List<?>) result.get("relations")).size());
    }
}
