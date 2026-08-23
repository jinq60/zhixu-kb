package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档处理主任务（状态机：PENDING→CLEANING→CHUNKING→EMBEDDING→COMPLETED/FAILED）。
 * 每个阶段结束必须落库（阶段检查点），重启后从 current_stage 恢复。
 */
@Data
@TableName("document_process_task")
public class DocumentProcessTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long noteId;
    private Long fileId;
    private String fileName;
    private String status;
    private String currentStage;
    private Integer progress;
    private String failReason;
    private Integer retryCount;
    private Integer maxRetry;
    private String parsedText;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}