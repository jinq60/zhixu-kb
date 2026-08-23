package com.zhixu.kb.note.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicNoteDetailResponse {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String title;
    private String content;
    private String ocrText;
    private String summary;
    private String keywords;
    private String coverImage;
    private Integer status;
    private String categoryName;
    private String authorName;
    private Long authorId;
    private Boolean editable;
    private Boolean published;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
