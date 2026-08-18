package com.zhixu.kb.graph.model;

import lombok.Data;

import java.util.List;

/**
 * 单篇笔记的知识图谱（前端可视化数据）。
 */
@Data
public class GraphData {
    private Long noteId;
    private String noteTitle;
    private List<GraphNode> nodes;
    private List<GraphEdge> edges;
    private String extractionSource;
}
