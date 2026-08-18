package com.zhixu.kb.note.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteHistoryItem {
    private Long id;
    private String operationType;
    private String operationDesc;
    private LocalDateTime createTime;
    private String snapshotTitle;
    private Boolean hasOutline;
    private Boolean hasMindmap;
}
