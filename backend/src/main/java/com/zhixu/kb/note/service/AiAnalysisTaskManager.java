package com.zhixu.kb.note.service;

import org.springframework.stereotype.Component;

import java.util.List;
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
    /** 看门狗：任务运行超过该时长视为失联（进程重启/线程池异常），自动复位 */
    private static final long STALE_RUNNING_MS = 30 * 60 * 1000L;

    private final Map<Long, TaskState> tasks = new ConcurrentHashMap<>();

    /**
     * 尝试登记任务。返回 true 表示本次调用成功启动任务；
     * 返回 false 表示该笔记已有任务在进行中。
     */
    public boolean tryStart(Long userId, Long noteId, String noteTitle) {
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
            // 清理过期的已完成任务
            if (state.finishedAt > 0 && now - state.finishedAt > KEEP_MS) {
                state.reset();
            }
            state.running = true;
            state.error = null;
            state.startedAt = now;
            state.finishedAt = 0;
            state.userId = userId;
            state.noteId = noteId;
            state.noteTitle = noteTitle;
            state.stage = "排队中";
            state.generation++;
            return true;
        }
    }

    /**
     * 更新任务阶段描述（细粒度监控展示：排队中 → AI 分析中 → 写入结果）。
     * 仅当代际匹配时生效，防止旧任务覆盖新任务状态。
     */
    public void updateStage(Long noteId, long generation, String stage) {
        TaskState state = tasks.get(noteId);
        if (state == null || state.generation != generation) {
            return;
        }
        state.stage = stage;
    }

    /**
     * 删除指定笔记的 AI 整理任务记录（任务中心"删除任务日志"）。归属校验失败返回 false。
     */
    public boolean remove(Long userId, Long noteId) {
        TaskState state = tasks.get(noteId);
        if (state == null || !userId.equals(state.userId)) {
            return false;
        }
        tasks.remove(noteId);
        return true;
    }

    /**
     * 按用户列出进行中的 AI 整理任务（任务面板展示）。
     */
    public List<TaskState> listActiveTasks(Long userId) {
        long now = System.currentTimeMillis();
        List<TaskState> result = new java.util.ArrayList<>();
        for (TaskState state : tasks.values()) {
            if (state.running && userId.equals(state.userId)) {
                result.add(state);
            }
        }
        return result;
    }

    /**
     * 按用户列出最近完成/失败的 AI 整理任务（保留期内，任务面板展示历史与重试入口）。
     */
    public List<TaskState> listRecentTasks(Long userId) {
        long now = System.currentTimeMillis();
        List<TaskState> result = new java.util.ArrayList<>();
        for (TaskState state : tasks.values()) {
            if (!state.running && state.finishedAt > 0
                    && userId.equals(state.userId)
                    && now - state.finishedAt <= KEEP_MS) {
                result.add(state);
            }
        }
        result.sort((a, b) -> Long.compare(b.finishedAt, a.finishedAt));
        return result;
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
        private volatile Long userId;
        private volatile Long noteId;
        private volatile String noteTitle;
        private volatile String stage;

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

        public Long getUserId() {
            return userId;
        }

        public Long getNoteId() {
            return noteId;
        }

        public String getNoteTitle() {
            return noteTitle;
        }

        public String getStage() {
            return stage;
        }
    }
}
