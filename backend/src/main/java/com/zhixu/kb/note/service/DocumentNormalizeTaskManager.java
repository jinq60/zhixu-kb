package com.zhixu.kb.note.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 文档清洗任务状态管理（内存）：提交后立即返回，后台执行，前端轮询直至完成。
 */
@Component
public class DocumentNormalizeTaskManager {

    private static final long KEEP_MS = 10 * 60 * 1000L;
    /** 看门狗：任务运行超过该时长视为失联（进程重启/线程池异常），自动复位 */
    private static final long STALE_RUNNING_MS = 30 * 60 * 1000L;

    private final Map<Long, TaskState> tasks = new ConcurrentHashMap<>();

    public boolean tryStart(Long noteId) {
        TaskState state = tasks.computeIfAbsent(noteId, k -> new TaskState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (state.running) {
                if (now - state.startedAt > STALE_RUNNING_MS) {
                    state.reset();
                } else {
                    return false;
                }
            }
            if (state.finishedAt > 0 && now - state.finishedAt > KEEP_MS) {
                state.reset();
            }
            state.running = true;
            state.error = null;
            state.startedAt = now;
            state.finishedAt = 0;
            state.generation++;
            return true;
        }
    }

    /**
     * 返回指定笔记当前任务代际（tryStart 成功后调用方保存，
     * 完成/释放时携带，防止旧任务回调覆盖新任务状态）。
     */
    public long generationOf(Long noteId) {
        TaskState state = tasks.get(noteId);
        return state == null ? -1L : state.generation;
    }

    public void complete(Long noteId, long generation, String error) {
        TaskState state = tasks.get(noteId);
        if (state == null || state.generation != generation) {
            return;
        }
        synchronized (state) {
            if (state.generation != generation) {
                return;
            }
            state.running = false;
            state.error = error;
            state.finishedAt = System.currentTimeMillis();
        }
    }

    /**
     * 释放任务状态（异步提交被拒绝时回滚 running 标记）。
     */
    public void release(Long noteId, long generation) {
        TaskState state = tasks.get(noteId);
        if (state == null || state.generation != generation) {
            return;
        }
        synchronized (state) {
            if (state.generation != generation) {
                return;
            }
            state.running = false;
            state.error = null;
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
        // 看门狗：长时间 running 视为失联，自动复位
        if (state.running && System.currentTimeMillis() - state.startedAt > STALE_RUNNING_MS) {
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
        private volatile long generation;

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
