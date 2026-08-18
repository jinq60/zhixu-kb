package com.zhixu.kb.note.model;

import lombok.Data;

@Data
public class NoteHistorySnapshot {
    private Long noteId;
    private String title;
    private String content;
    private String ocrText;
    private String summary;
    private String keywords;
    private String coverImage;
    private Long categoryId;
    private Integer status;
    private String outlineJson;
    private String mermaid;
}
