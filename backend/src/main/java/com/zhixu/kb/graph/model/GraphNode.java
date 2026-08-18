package com.zhixu.kb.graph.model;

import lombok.Data;

/**
 * 图谱节点。
 */
@Data
public class GraphNode {
    private String id;
    private String name;
    private String type;
    private String description;
}
