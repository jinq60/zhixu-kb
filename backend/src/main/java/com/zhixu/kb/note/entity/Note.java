package com.zhixu.kb.note.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("note")
public class Note {

    /**
     * 雪花 ID（ASSIGN_ID）：对外不可枚举、不暴露业务规模；
     * JSON 序列化为字符串，避免超出 JS Number.MAX_SAFE_INTEGER 丢精度。
     */
    @TableId(type = IdType.ASSIGN_ID)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private Long userId;
    private Long categoryId;
    private String title;
    private String content;
    private String ocrText;
    private String summary;
    private String keywords;
    private String coverImage;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer isDeleted;
}
