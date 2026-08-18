package com.zhixu.kb.ask.model;

import lombok.Data;

import java.util.List;

/**
 * 从笔记检索到的知识片段。
 */
@Data
public class RetrievedNote {
    private Long noteId;
    private String noteTitle;
    private Double similarity;
    private List<String> snippets;
}
