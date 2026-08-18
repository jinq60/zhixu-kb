package com.zhixu.kb.note.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteStatsResponse {
    private Long totalCount;
    private Long draftCount;
    private Long publishedCount;
}
