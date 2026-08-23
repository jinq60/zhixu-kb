package com.zhixu.kb.note.service;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 向量化明细：最终切割块级状态，失败可单独重试。
 */
@Data
@TableName("embed_chunk_task")
public class EmbedChunkTaskEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long taskId;
    private Integer chunkIndex;
    private String content;
    private String status;
    private String milvusId;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime updateTime;
}