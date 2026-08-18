package com.zhixu.kb.graph.model;

import lombok.Data;

/**
 * 全库图谱总览（管理端）。
 */
@Data
public class GraphOverview {
    private boolean available;
    private long noteCount;
    private long entityCount;
    private long relationCount;
    private String message;
}
