package com.zhixu.kb.note.model;

import com.zhixu.kb.note.entity.NoteSection;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NoteStructureResponse {
    private List<OutlineNode> outline;
    private List<NoteSection> sections;
    private String mermaid;
    private LocalDateTime updateTime;
}
