package com.zhixu.kb.note.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class OutlineNode {
    private String title;
    private String content;
    private List<OutlineNode> children = new ArrayList<>();
}
