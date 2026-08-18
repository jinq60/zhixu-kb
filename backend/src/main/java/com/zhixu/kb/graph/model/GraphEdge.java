package com.zhixu.kb.graph.model;

import lombok.Data;

/**
 * 图谱边。
 */
@Data
public class GraphEdge {
    private String source;
    private String target;
    private String relation;
}
