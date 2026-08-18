package com.zhixu.kb.graph.model;

import lombok.Data;

/**
 * 图谱构建结果。
 */
@Data
public class GraphBuildResult {
    private Long noteId;
    private int entityCount;
    private int relationCount;
    private String extractionSource;
    private String message;
}
