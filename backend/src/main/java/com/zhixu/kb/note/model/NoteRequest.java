package com.zhixu.kb.note.model;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

@Data
public class NoteRequest {
    @NotBlank(message = "标题不能为空")
    @Size(max = 255, message = "标题过长（最多 255 个字符）")
    private String title;
    private String content;
    private String summary;
    private String keywords;
    private String coverImage;
    private Long categoryId;
    private Integer status;
    private List<OutlineNode> outline;
}
