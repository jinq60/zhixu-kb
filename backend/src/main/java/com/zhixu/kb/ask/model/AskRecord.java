package com.zhixu.kb.ask.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识问答记录视图。
 */
@Data
public class AskRecord {
    private String id;
    private Long userId;
    private String question;
    private String answer;
    private List<RelatedNote> relatedNotes;
    private String status;
    private String confidenceLevel;
    private List<String> riskFlags;
    private String conversationId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;

    /**
     * 回答引用的知识来源（笔记）。
     */
    @Data
    public static class RelatedNote {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long noteId;
        private String noteTitle;
        private Double similarity;
    }
}
