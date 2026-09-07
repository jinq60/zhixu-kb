package com.zhixu.kb.graph.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 知识图谱构建任务管理（内存）：
 * 提交后立即返回 taskId，后台线程执行构建，前端轮询状态直至完成。
 */
@Component
public class GraphTaskManager {

    /** 任务保留时长（完成/失败后 10 分钟内仍可查询） */
    private static final long KEEP_MS = 10 * 60 * 1000L;
    /** 看门狗：任务运行超过该时长视为失联，自动复位 */
    private static final long STALE_RUNNING_MS = 60 * 60 * 1000L;

    /**
     * 全局单调发放的代数序号：TaskState 被移除重建后 generation 也不会回到旧值，
     * 避免僵尸 runner（持有旧代数）通过新任务的 generation 校验覆盖其状态。
     */
    private static final AtomicLong GENERATION_SEQ = new AtomicLong();

    private final Map<String, TaskState> tasks = new ConcurrentHashMap<>();

    /**
     * 尝试登记任务。返回 taskId；若同一范围已有任务进行中则返回 null。
     */
    public String tryStart(Long userId, TaskType taskType, String targetId, String targetName) {
        sweepExpired();
        // P1-4 修复：任务 ID 按用户隔离，避免不同用户同 noteId/categoryId 互相阻塞/存在性探测
        String taskId = buildTaskId(userId, taskType, targetId);
        TaskState state = tasks.computeIfAbsent(taskId, k -> new TaskState());
        synchronized (state) {
            long now = System.currentTimeMillis();
            if (state.running) {
                if (now - state.startedAt > STALE_RUNNING_MS) {
                    state.reset();
                } else {
                    return null;
                }
            }
            if (state.finishedAt > 0 && now - state.finishedAt > KEEP_MS) {
                state.reset();
            }
            state.running = true;
            state.error = null;
            state.startedAt = now;
            state.finishedAt = 0;
            state.userId = userId;
            state.taskType = taskType;
            state.targetId = targetId;
            state.targetName = targetName;
            state.stage = "排队中";
            state.cancelRequested = false;
            state.generation = GENERATION_SEQ.incrementAndGet();
            return taskId;
        }
    }

    /**
     * 请求取消运行中的任务（笔记粒度：当前篇处理完后中断后续抽取）。
     * 仅任务属主可取消；任务不在运行态返回 false。
     */
    public boolean requestCancel(Long userId, String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null || !userId.equals(state.userId)) {
            return false;
        }
        synchronized (state) {
            if (!state.running) {
                return false;
            }
            state.cancelRequested = true;
        }
        return true;
    }

    /** runner 侧查询取消标志 */
    public boolean isCancelled(String taskId) {
        TaskState state = tasks.get(taskId);
        return state != null && state.cancelRequested;
    }

    /** 清理过期条目：已完成超保留时长、或失联运行超看门狗时长的任务记录 */
    private void sweepExpired() {
        if (tasks.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Map.Entry<String, TaskState> entry : tasks.entrySet()) {
            TaskState state = entry.getValue();
            if (!state.running && state.finishedAt > 0 && now - state.finishedAt > KEEP_MS) {
                tasks.remove(entry.getKey(), state);
            } else if (state.running && now - state.startedAt > STALE_RUNNING_MS) {
                // 失联任务仅复位运行标志并保留记录（供 complete 的代数校验拒绝旧 runner），
                // 不从 map 移除——移除会导致同 key 重建后 generation 撞上僵尸 runner
                synchronized (state) {
                    if (state.running && now - state.startedAt > STALE_RUNNING_MS) {
                        state.running = false;
                        state.error = "任务超时失联，已被系统复位";
                        state.finishedAt = now;
                    }
                }
            }
        }
    }

    public void updateStage(String taskId, long generation, String stage) {
        TaskState state = tasks.get(taskId);
        if (state == null) {
            return;
        }
        synchronized (state) {
            if (state.generation != generation) {
                return;
            }
            state.stage = stage;
        }
    }

    public boolean remove(Long userId, String taskId) {
        TaskState state = tasks.get(taskId);
        if (state == null || !userId.equals(state.userId)) {
            return false;
        }
        tasks.remove(taskId);
        return true;
    }

    public List<TaskState> listActiveTasks(Long userId) {
        sweepExpired();
        List<TaskState> result = new ArrayList<>();
        for (TaskState state : tasks.values()) {
            if (state.running && userId.equals(state.userId)) {
                result.add(state);
            }
        }
        return result;
    }

    public List<TaskState> listRecentTasks(Long userId) {
        sweepExpired();
        long now = System.currentTimeMillis();
        List<TaskState> result = new ArrayList<>();
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

    public long generationOf(String taskId) {
        TaskState state = tasks.get(taskId);
        return state == null ? -1L : state.generation;
    }

    public void complete(String taskId, long generation, String error) {
        TaskState state = tasks.get(taskId);
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

    public void release(String taskId, long generation) {
        TaskState state = tasks.get(taskId);
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

    public TaskState get(String taskId) {
        sweepExpired();
        TaskState state = tasks.get(taskId);
        if (state == null) {
            return emptyState();
        }
        long now = System.currentTimeMillis();
        if (!state.running && state.finishedAt > 0 && now - state.finishedAt > KEEP_MS) {
            tasks.remove(taskId, state);
            return emptyState();
        }
        return state;
    }

    private TaskState emptyState() {
        TaskState empty = new TaskState();
        empty.running = false;
        return empty;
    }

    private String buildTaskId(Long userId, TaskType taskType, String targetId) {
        return (userId == null ? "anon" : userId) + ":" + taskType.name() + ":"
                + (targetId == null ? "global" : targetId);
    }

    public enum TaskType {
        NOTE, CATEGORY, GLOBAL
    }

    public static class TaskState {
        private volatile boolean running;
        private volatile boolean cancelRequested;
        private volatile String error;
        private volatile long startedAt;
        private volatile long finishedAt;
        private volatile long generation;
        private volatile Long userId;
        private volatile TaskType taskType;
        private volatile String targetId;
        private volatile String targetName;
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

        public long getGeneration() {
            return generation;
        }

        public Long getUserId() {
            return userId;
        }

        public TaskType getTaskType() {
            return taskType;
        }

        public String getTargetId() {
            return targetId;
        }

        public String getTargetName() {
            return targetName;
        }

        public String getStage() {
            return stage;
        }
    }
}
