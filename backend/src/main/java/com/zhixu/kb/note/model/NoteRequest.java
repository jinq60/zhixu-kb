package com.zhixu.kb.note.model;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class NoteRequest {
    @NotBlank(message = "标题不能为空")
    private String title;
    private String content;
    private String summary;
    private String keywords;
    private String coverImage;
    private Long categoryId;
    private Integer status;
    private List<OutlineNode> outline;
}
