package com.zhixu.kb.graph.controller;

import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.graph.model.GraphBuildResult;
import com.zhixu.kb.graph.model.GraphData;
import com.zhixu.kb.graph.model.GraphNode;
import com.zhixu.kb.graph.model.GraphOverview;
import com.zhixu.kb.graph.service.GraphService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识图谱接口：笔记图谱构建/查询/删除、全局检索、管理端总览。
 */
@RestController
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;

    @PostMapping("/api/notes/{id}/graph/build")
    public Result<GraphBuildResult> build(@PathVariable("id") Long id) {
        return Result.success(graphService.build(id));
    }

    @GetMapping("/api/notes/{id}/graph")
    public Result<GraphData> get(@PathVariable("id") Long id) {
        return Result.success(graphService.get(id));
    }

    @DeleteMapping("/api/notes/{id}/graph")
    public Result<Boolean> delete(@PathVariable("id") Long id) {
        return Result.success("图谱已删除", graphService.delete(id));
    }

    @GetMapping("/api/graph/search")
    public Result<List<GraphNode>> search(@RequestParam("keyword") String keyword) {
        return Result.success(graphService.search(keyword));
    }

    @PostMapping("/api/graph/category/{categoryId}/build")
    public Result<GraphBuildResult> buildCategory(@PathVariable("categoryId") Long categoryId) {
        return Result.success(graphService.buildCategory(categoryId));
    }

    @GetMapping("/api/graph/category/{categoryId}")
    public Result<GraphData> getCategory(@PathVariable("categoryId") Long categoryId) {
        return Result.success(graphService.getByCategory(categoryId));
    }

    @PostMapping("/api/graph/global/build")
    public Result<GraphBuildResult> buildGlobal() {
        return Result.success(graphService.buildGlobal());
    }

    @GetMapping("/api/graph/global")
    public Result<GraphData> getGlobal() {
        return Result.success(graphService.getGlobal());
    }

    @GetMapping("/api/v1/admin/graph/overview")
    @PreAuthorize("hasRole('admin')")
    public Result<GraphOverview> overview() {
        return Result.success(graphService.overview());
    }
}
