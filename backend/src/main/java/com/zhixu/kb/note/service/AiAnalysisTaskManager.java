package com.zhixu.kb.note.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 整理任务状态管理（内存）：
 * 提交后立即返回，后台线程执行，前端轮询状态直至完成。
 */
@Component
public class AiAnalysisTaskManager {

    /** 任务保留时长（完成/失败后 10 分钟内仍可查询） */
    private static final long KEEP_MS = 10 * 60 * 1000L;

    private final Map<Long, TaskState> tasks = new ConcurrentHashMap<>();

    /**
     * 尝试登记任务。返回 true 表示本次调用成功启动任务；
     * 返回 false 表示该笔记已有任务在进行中。
     */
    public boolean tryStart(Long noteId) {
        TaskState state = tasks.computeIfAbsent(noteId, k -> new TaskState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (state.running) {
                return false;
            }
            // 清理过期的已完成任务
            if (state.finishedAt > 0 && now - state.finishedAt > KEEP_MS) {
                state.reset();
            }
            state.running = true;
            state.error = null;
            state.startedAt = now;
            state.finishedAt = 0;
            return true;
        }
    }

    public void complete(Long noteId, String error) {
        TaskState state = tasks.get(noteId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            state.running = false;
            state.error = error;
            state.finishedAt = System.currentTimeMillis();
        }
    }

    public TaskState get(Long noteId) {
        TaskState state = tasks.get(noteId);
        if (state == null) {
            TaskState empty = new TaskState();
            empty.running = false;
            return empty;
        }
        // 完成/失败后超过 KEEP_MS 自动清理，避免 Map 无限增长
        if (!state.running && state.finishedAt > 0
                && System.currentTimeMillis() - state.finishedAt > KEEP_MS) {
            tasks.remove(noteId);
            TaskState empty = new TaskState();
            empty.running = false;
            return empty;
        }
        return state;
    }

    public static class TaskState {
        private volatile boolean running;
        private volatile String error;
        private volatile long startedAt;
        private volatile long finishedAt;

        void reset() {
            running = false;
            error = null;
            startedAt = 0;
            finishedAt = 0;
        }

        public boolean isRunning() {
            return running;
        }

        public String getError() {
            return error;
        }

        public long getStartedAt() {
            return startedAt;
        }

        public long getFinishedAt() {
            return finishedAt;
        }
    }
}
