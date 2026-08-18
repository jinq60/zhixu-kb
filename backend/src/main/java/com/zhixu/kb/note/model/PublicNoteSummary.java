package com.zhixu.kb.note.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PublicNoteSummary {
    private Long id;
    private String title;
    private String summary;
    private String keywords;
    private String coverImage;
    private String categoryName;
    private String authorName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
