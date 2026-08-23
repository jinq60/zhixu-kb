package com.zhixu.kb.ask.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识问答记录（问题/回答加密存储）。
 */
@Data
@TableName("ask_records")
public class AskRecordEntity {
    @TableId(type = IdType.INPUT)
    private String id;
    private Long userId;
    private String question;
    private String answer;
    private String relatedNotes;
    private String status;
    private String confidenceLevel;
    private String riskFlags;
    private String conversationId;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
