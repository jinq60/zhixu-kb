package com.zhixu.kb.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AiTextCleanerTest {

    @Test
    void clean_stripsThinkBlock() {
        String text = "<think>这是内部推理，不应展示</think>最终回答内容";
        assertEquals("最终回答内容", AiTextCleaner.clean(text));
    }

    @Test
    void clean_withoutThinkBlock_returnsOriginal() {
        String text = "普通回答，无推理块";
        assertEquals(text, AiTextCleaner.clean(text));
    }

    @Test
    void clean_null_returnsNull() {
        assertNull(AiTextCleaner.clean(null));
    }

    @Test
    void clean_onlyThinkBlock_returnsOriginal() {
        String text = "<think>只有推理</think>";
        assertEquals(text, AiTextCleaner.clean(text));
    }

    @Test
    void streamCleaner_stripsThinkAcrossChunks() {
        AiTextCleaner.StreamCleaner cleaner = new AiTextCleaner.StreamCleaner();
        List<String> out = new ArrayList<>();

        cleaner.feed("<thin", out::add);
        cleaner.feed("k>内部推理内容", out::add);
        cleaner.feed("</think>", out::add);
        cleaner.feed("最终答案", out::add);
        cleaner.flush(out::add);

        assertEquals("最终答案", String.join("", out));
    }

    @Test
    void streamCleaner_noThinkBlock_passesThrough() {
        AiTextCleaner.StreamCleaner cleaner = new AiTextCleaner.StreamCleaner();
        List<String> out = new ArrayList<>();

        cleaner.feed("这是", out::add);
        cleaner.feed("正常", out::add);
        cleaner.feed("输出", out::add);
        cleaner.flush(out::add);

        assertEquals("这是正常输出", String.join("", out));
    }
}
