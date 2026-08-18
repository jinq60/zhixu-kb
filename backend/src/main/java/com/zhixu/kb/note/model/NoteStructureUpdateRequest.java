package com.zhixu.kb.note.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class NoteStructureUpdateRequest {
    private List<OutlineNode> outline = new ArrayList<>();
}
