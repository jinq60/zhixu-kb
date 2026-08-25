package com.zhixu.kb.graph.controller;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.graph.model.GraphData;
import com.zhixu.kb.graph.model.GraphNode;
import com.zhixu.kb.graph.model.GraphOverview;
import com.zhixu.kb.graph.service.GraphService;
import com.zhixu.kb.graph.service.GraphTaskManager;
import com.zhixu.kb.graph.service.GraphTaskRunner;
import com.zhixu.kb.note.entity.Category;
import com.zhixu.kb.note.entity.Note;
import com.zhixu.kb.note.mapper.CategoryMapper;
import com.zhixu.kb.note.mapper.NoteMapper;
import com.zhixu.kb.system.model.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 知识图谱接口：笔记/分类/全局图谱异步构建、查询、删除、全局检索、管理端总览、任务中心。
 */
@RestController
@RequiredArgsConstructor
public class GraphController {

    private final GraphService graphService;
    private final GraphTaskManager graphTaskManager;
    private final GraphTaskRunner graphTaskRunner;
    private final NoteMapper noteMapper;
    private final CategoryMapper categoryMapper;

    @PostMapping("/api/notes/{id}/graph/build")
    public Result<Map<String, Object>> build(@PathVariable("id") Long id) {
        Long userId = SecurityUtils.getUserId();
        Note note = noteMapper.selectById(id);
        if (note == null || !Objects.equals(note.getUserId(), userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "笔记不存在");
        }
        String targetName = note.getTitle() == null ? "笔记 #" + id : note.getTitle();
        String taskId = graphTaskManager.tryStart(userId, GraphTaskManager.TaskType.NOTE, String.valueOf(id), targetName);
        if (taskId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该笔记正在构建图谱中，请稍后再试");
        }
        long generation = graphTaskManager.generationOf(taskId);
        LoginUser loginUser = SecurityUtils.getLoginUser();
        graphTaskRunner.submitSafe(userId, taskId, graphTaskManager, generation, () ->
                graphTaskRunner.runNote(userId, id, graphTaskManager, taskId, generation, loginUser));
        return Result.success("图谱构建已提交", buildTaskView(taskId, GraphTaskManager.TaskType.NOTE, String.valueOf(id), targetName));
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
    public Result<Map<String, Object>> buildCategory(@PathVariable("categoryId") Long categoryId) {
        Long userId = SecurityUtils.getUserId();
        // 归属校验：不校验时任意用户可用随机 categoryId 无限制造任务记录撑爆内存
        Category category = categoryMapper.selectById(categoryId);
        if (category == null || !Objects.equals(category.getUserId(), userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "分类不存在");
        }
        String targetName = category.getName() == null ? "分类 #" + categoryId : category.getName();
        String taskId = graphTaskManager.tryStart(userId, GraphTaskManager.TaskType.CATEGORY,
                String.valueOf(categoryId), targetName);
        if (taskId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该分类正在构建图谱中，请稍后再试");
        }
        long generation = graphTaskManager.generationOf(taskId);
        LoginUser loginUser = SecurityUtils.getLoginUser();
        graphTaskRunner.submitSafe(userId, taskId, graphTaskManager, generation, () ->
                graphTaskRunner.runCategory(userId, categoryId, graphTaskManager, taskId, generation, loginUser));
        return Result.success("分类图谱构建已提交", buildTaskView(taskId, GraphTaskManager.TaskType.CATEGORY, String.valueOf(categoryId), targetName));
    }

    @GetMapping("/api/graph/category/{categoryId}")
    public Result<GraphData> getCategory(@PathVariable("categoryId") Long categoryId) {
        return Result.success(graphService.getByCategory(categoryId));
    }

    @PostMapping("/api/graph/global/build")
    public Result<Map<String, Object>> buildGlobal() {
        Long userId = SecurityUtils.getUserId();
        // targetId 按用户命名空间隔离：此前所有用户共享 GLOBAL:global，
        // 单用户提交后其他用户会被"正在构建中"阻塞且看不到任务状态
        String targetId = "user-" + userId;
        String taskId = graphTaskManager.tryStart(userId, GraphTaskManager.TaskType.GLOBAL, targetId, "全局知识体系");
        if (taskId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "全局图谱正在构建中，请稍后再试");
        }
        long generation = graphTaskManager.generationOf(taskId);
        LoginUser loginUser = SecurityUtils.getLoginUser();
        graphTaskRunner.submitSafe(userId, taskId, graphTaskManager, generation, () ->
                graphTaskRunner.runGlobal(userId, graphTaskManager, taskId, generation, loginUser));
        return Result.success("全局图谱构建已提交", buildTaskView(taskId, GraphTaskManager.TaskType.GLOBAL, targetId, "全局知识体系"));
    }

    @GetMapping("/api/graph/global")
    public Result<GraphData> getGlobal() {
        return Result.success(graphService.getGlobal());
    }

    @GetMapping("/api/graph/tasks")
    public Result<Map<String, Object>> tasks() {
        Long userId = SecurityUtils.getUserId();
        Map<String, Object> data = new HashMap<>();
        data.put("active", graphTaskManager.listActiveTasks(userId).stream().map(this::toTaskView).collect(Collectors.toList()));
        data.put("recent", graphTaskManager.listRecentTasks(userId).stream().map(this::toTaskView).collect(Collectors.toList()));
        return Result.success(data);
    }

    @GetMapping("/api/graph/tasks/{taskId}")
    public Result<Map<String, Object>> taskStatus(@PathVariable("taskId") String taskId) {
        Long userId = SecurityUtils.getUserId();
        GraphTaskManager.TaskState state = graphTaskManager.get(taskId);
        if (state.getUserId() == null || !Objects.equals(state.getUserId(), userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在或无权操作");
        }
        return Result.success(toTaskView(state));
    }

    @DeleteMapping("/api/graph/tasks/{taskId}")
    public Result<Boolean> deleteTask(@PathVariable("taskId") String taskId) {
        Long userId = SecurityUtils.getUserId();
        // 运行中的任务先请求取消（在笔记粒度中断后续 AI 抽取），再移除记录
        boolean cancelRequested = graphTaskManager.requestCancel(userId, taskId);
        boolean removed = graphTaskManager.remove(userId, taskId);
        if (!removed && !cancelRequested) {
            return Result.error(404, "任务不存在或无权操作");
        }
        return Result.success(cancelRequested ? "已取消任务并删除记录" : "任务记录已删除", Boolean.TRUE);
    }

    @GetMapping("/api/v1/admin/graph/overview")
    @PreAuthorize("hasRole('admin')")
    public Result<GraphOverview> overview() {
        return Result.success(graphService.overview());
    }

    private Map<String, Object> buildTaskView(String taskId, GraphTaskManager.TaskType taskType, String targetId, String targetName) {
        Map<String, Object> view = new HashMap<>();
        view.put("taskId", taskId);
        view.put("taskType", taskType.name());
        view.put("targetId", targetId);
        view.put("targetName", targetName);
        view.put("submitted", true);
        return view;
    }

    private Map<String, Object> toTaskView(GraphTaskManager.TaskState state) {
        Map<String, Object> view = new HashMap<>();
        view.put("taskId", state.getTaskType().name() + ":" + state.getTargetId());
        view.put("taskType", state.getTaskType().name());
        view.put("targetId", state.getTargetId());
        view.put("targetName", state.getTargetName());
        view.put("running", state.isRunning());
        view.put("stage", state.getStage());
        view.put("error", state.getError());
        view.put("startedAt", state.getStartedAt());
        view.put("finishedAt", state.getFinishedAt());
        long elapsed = 0;
        if (state.getStartedAt() > 0) {
            long endAt = state.isRunning() ? System.currentTimeMillis() : state.getFinishedAt();
            if (endAt > 0) {
                elapsed = Math.max(0, (endAt - state.getStartedAt()) / 1000);
            }
        }
        view.put("elapsedSeconds", elapsed);
        return view;
    }
}
