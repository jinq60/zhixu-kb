package com.zhixu.kb.ask.service;

import com.zhixu.kb.ask.model.RetrievedNote;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.NoteMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class NoteRetrievalServiceTest {

    @Mock
    private NoteMapper noteMapper;

    private NoteRetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new NoteRetrievalService(noteMapper);
    }

    private Note note(Long id, String title, String content, String summary) {
        Note n = new Note();
        n.setId(id);
        n.setUserId(1L);
        n.setTitle(title);
        n.setContent(content);
        n.setSummary(summary);
        n.setIsDeleted(0);
        return n;
    }

    @Test
    void search_nullUserId_returnsEmpty() {
        assertTrue(retrievalService.search(null, "机器学习", 5).isEmpty());
    }

    @Test
    void search_blankQuery_returnsEmpty() {
        assertTrue(retrievalService.search(1L, "  ", 5).isEmpty());
    }

    @Test
    void search_matchingKeyword_returnsRankedNotes() {
        Note hit = note(1L, "机器学习入门指南", "本文介绍机器学习的基础概念", "机器学习概述");
        Note miss = note(2L, "烹饪食谱", "西红柿炒鸡蛋的做法", "家常菜");

        doReturn(Arrays.asList(hit, miss)).when(noteMapper).selectList(any());

        List<RetrievedNote> result = retrievalService.search(1L, "机器学习", 5);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getNoteId());
        assertTrue(result.get(0).getSimilarity() > 0);
        assertNotNull(result.get(0).getSnippets());
    }

    @Test
    void search_titleMatch_scoresHigherThanContentMatch() {
        Note titleHit = note(1L, "知识图谱概述", "这是一段无关的正文内容", "");
        Note contentHit = note(2L, "无关标题", "正文中详细讲解知识图谱的构建方法", "");

        doReturn(Arrays.asList(contentHit, titleHit)).when(noteMapper).selectList(any());

        List<RetrievedNote> result = retrievalService.search(1L, "知识图谱", 5);

        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getNoteId(), "标题命中的笔记应排在首位");
    }

    @Test
    void search_noMatch_returnsEmpty() {
        Note note = note(1L, "烹饪食谱", "西红柿炒鸡蛋的做法", "家常菜");

        doReturn(Collections.singletonList(note)).when(noteMapper).selectList(any());

        assertTrue(retrievalService.search(1L, "量子计算", 5).isEmpty());
    }

    @Test
    void search_respectsTopK() {
        Note n1 = note(1L, "笔记一 机器学习", "机器学习相关内容", "");
        Note n2 = note(2L, "笔记二 机器学习", "机器学习相关内容", "");
        Note n3 = note(3L, "笔记三 机器学习", "机器学习相关内容", "");

        doReturn(Arrays.asList(n1, n2, n3)).when(noteMapper).selectList(any());

        List<RetrievedNote> result = retrievalService.search(1L, "机器学习", 2);

        assertEquals(2, result.size());
    }

    @Test
    void search_ocTextIsIndexed() {
        Note note = note(1L, "图片笔记", "", "");
        note.setOcrText("照片中的文字包含机器学习这个关键词");

        doReturn(Collections.singletonList(note)).when(noteMapper).selectList(any());

        List<RetrievedNote> result = retrievalService.search(1L, "机器学习", 5);

        assertEquals(1, result.size());
    }
}
