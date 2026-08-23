package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * LLM 清洗明细：粗切块级状态，失败可单独重试（幂等：SUCCESS 跳过）。
 */
@Data
@TableName("clean_chunk_task")
public class CleanChunkTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer chunkIndex;
    private String rawContent;
    private String cleanedContent;
    private String status;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}