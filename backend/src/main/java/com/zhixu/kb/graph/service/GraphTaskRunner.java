package com.zhixu.kb.graph.service;

import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.graph.model.GraphBuildResult;
import com.zhixu.kb.system.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 知识图谱构建异步执行器：独立线程池执行构建，不阻塞 HTTP 请求。
 * 异步线程无请求上下文，需手动注入安全上下文供所有权校验使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphTaskRunner {

    private final GraphService graphService;

    /**
     * 提交单篇笔记图谱构建任务。提交前已由 GraphTaskManager 完成并发控制。
     */
    @Async("graphTaskExecutor")
    public void runNote(Long userId, Long noteId, GraphTaskManager manager, String taskId, long generation, LoginUser loginUser) {
        setupSecurityContext(loginUser);
        try {
            manager.updateStage(taskId, generation, "AI 抽取与写入图谱中");
            GraphBuildResult result = graphService.build(noteId);
            manager.complete(taskId, generation, result.getEntityCount() == 0
                    ? (result.getMessage() == null ? "未抽取到实体或关系" : result.getMessage())
                    : null);
            log.info("Graph build task finished: taskId={} noteId={} entities={} relations={}",
                    taskId, noteId, result.getEntityCount(), result.getRelationCount());
        } catch (Exception ex) {
            log.error("Graph build task failed: taskId={} noteId={}", taskId, noteId, ex);
            manager.complete(taskId, generation, friendlyMessage(ex));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 提交分类/全局图谱构建任务。
     */
    @Async("graphTaskExecutor")
    public void runCategory(Long userId, Long categoryId, GraphTaskManager manager, String taskId, long generation, LoginUser loginUser) {
        setupSecurityContext(loginUser);
        try {
            manager.updateStage(taskId, generation, "批量抽取与写入图谱中");
            GraphBuildResult result = graphService.buildCategory(categoryId);
            manager.complete(taskId, generation, result.getEntityCount() == 0
                    ? (result.getMessage() == null ? "未抽取到实体或关系" : result.getMessage())
                    : null);
            log.info("Graph build task finished: taskId={} categoryId={} entities={} relations={}",
                    taskId, categoryId, result.getEntityCount(), result.getRelationCount());
        } catch (Exception ex) {
            log.error("Graph build task failed: taskId={} categoryId={}", taskId, categoryId, ex);
            manager.complete(taskId, generation, friendlyMessage(ex));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 提交全局图谱构建任务。
     */
    @Async("graphTaskExecutor")
    public void runGlobal(Long userId, GraphTaskManager manager, String taskId, long generation, LoginUser loginUser) {
        runCategory(userId, null, manager, taskId, generation, loginUser);
    }

    /**
     * 安全提交任务，若线程池拒绝则释放任务状态并抛出业务异常。
     */
    public void submitSafe(Long userId, String taskId, GraphTaskManager manager, long generation, Runnable submitter) {
        try {
            submitter.run();
        } catch (TaskRejectedException e) {
            manager.release(taskId, generation);
            throw new com.zhixu.kb.common.exception.BusinessException(
                    com.zhixu.kb.common.result.ResultCode.BAD_REQUEST, "系统繁忙，请稍后再试");
        }
    }

    private void setupSecurityContext(LoginUser loginUser) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (loginUser != null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            context.setAuthentication(authentication);
        }
        SecurityContextHolder.setContext(context);
    }

    private String friendlyMessage(Exception ex) {
        if (ex instanceof com.zhixu.kb.common.exception.BusinessException) {
            return ex.getMessage();
        }
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return "AI 引擎响应超时，请稍后重试";
        }
        if (msg.contains("暂时无法调用外部模型")) {
            return "AI 引擎暂时不可用";
        }
        if (msg.contains("neo4j") || msg.contains("connection refused")) {
            return "图谱存储服务（Neo4j）不可用，请检查配置";
        }
        String raw = ex.getMessage() == null ? "未知错误" : ex.getMessage();
        return raw.length() > 200 ? raw.substring(0, 200) : raw;
    }
}
