package com.zhixu.kb.note.model;

import lombok.Data;

import java.util.List;

@Data
public class AIAnalysisResult {
    private String suggestedCategory;
    private List<String> tags;
    private String summary;
    private String keywords;
    private List<OutlineNode> outline;
}
