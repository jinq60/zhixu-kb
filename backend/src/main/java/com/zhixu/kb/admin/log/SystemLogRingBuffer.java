package com.zhixu.kb.admin.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 管理端内存日志环形缓冲：由 {@link AdminLogAppender} 在每条日志输出时写入，
 * 供管理后台「系统日志」页实时查询。
 * <p>
 * 静态实现而非 Spring Bean：logback 初始化早于 Spring 容器，
 * 静态单例可避免两者生命周期顺序问题。有界容量防止长期运行内存膨胀。
 */
public final class SystemLogRingBuffer {

    /** 缓冲条数上限（单条约 ≤4KB，总占用 ≤ ~12MB） */
    private static final int CAPACITY = 3000;
    /** 单条消息长度上限：超长堆栈截断，防止撑爆内存与前端渲染 */
    private static final int MAX_MESSAGE_LENGTH = 4000;

    private static final Deque<LogEntry> BUFFER = new ArrayDeque<>(CAPACITY);
    private static final AtomicLong SEQ = new AtomicLong();

    private SystemLogRingBuffer() {
    }

    public static void append(String level, String logger, String thread, String traceId, String message) {
        if (message != null && message.length() > MAX_MESSAGE_LENGTH) {
            message = message.substring(0, MAX_MESSAGE_LENGTH) + "\n…（超长截断，完整内容见 Kibana/文件日志）";
        }
        synchronized (BUFFER) {
            if (BUFFER.size() >= CAPACITY) {
                BUFFER.pollFirst();
            }
            BUFFER.addLast(new LogEntry(SEQ.incrementAndGet(), System.currentTimeMillis(),
                    level, logger, thread, traceId == null ? "" : traceId, message));
        }
    }

    /**
     * 查询日志条目（按时间正序）。
     *
     * @param sinceId 只返回 id 大于该值的增量条目（0 表示从头拉取）
     * @param minLevel 最低严重级别：ALL/INFO/WARN/ERROR（WARN 表示 WARN+ERROR）
     * @param keyword  关键字（匹配 message/logger/traceId，忽略大小写；null/空不过滤）
     * @param limit    最多返回条数
     */
    public static QueryResult query(long sinceId, String minLevel, String keyword, int limit) {
        int rank = levelRank(minLevel);
        String kw = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        List<LogEntry> result = new ArrayList<>();
        long maxId = sinceId;
        int matched = 0;
        synchronized (BUFFER) {
            for (LogEntry entry : BUFFER) {
                if (entry.getId() <= sinceId || levelRank(entry.getLevel()) < rank) {
                    continue;
                }
                if (!kw.isEmpty() && !matches(entry, kw)) {
                    continue;
                }
                matched++;
                if (result.size() < limit) {
                    result.add(entry);
                    maxId = Math.max(maxId, entry.getId());
                }
            }
        }
        return new QueryResult(result, maxId, matched);
    }

    private static boolean matches(LogEntry entry, String kw) {
        return (entry.getMessage() != null && entry.getMessage().toLowerCase(Locale.ROOT).contains(kw))
                || (entry.getLogger() != null && entry.getLogger().toLowerCase(Locale.ROOT).contains(kw))
                || (entry.getTraceId() != null && entry.getTraceId().toLowerCase(Locale.ROOT).contains(kw));
    }

    private static int levelRank(String level) {
        if (level == null) {
            return 0;
        }
        switch (level.trim().toUpperCase(Locale.ROOT)) {
            case "ERROR":
                return 3;
            case "WARN":
                return 2;
            case "INFO":
                return 1;
            default:
                return 0;
        }
    }

    public static class LogEntry {
        private final long id;
        private final long timestamp;
        private final String level;
        private final String logger;
        private final String thread;
        private final String traceId;
        private final String message;

        LogEntry(long id, long timestamp, String level, String logger, String thread, String traceId, String message) {
            this.id = id;
            this.timestamp = timestamp;
            this.level = level;
            this.logger = logger;
            this.thread = thread;
            this.traceId = traceId;
            this.message = message;
        }

        public long getId() {
            return id;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public String getLevel() {
            return level;
        }

        public String getLogger() {
            return logger;
        }

        public String getThread() {
            return thread;
        }

        public String getTraceId() {
            return traceId;
        }

        public String getMessage() {
            return message;
        }
    }

    public static class QueryResult {
        private final List<LogEntry> entries;
        private final long maxId;
        private final int matchedTotal;

        QueryResult(List<LogEntry> entries, long maxId, int matchedTotal) {
            this.entries = entries;
            this.maxId = maxId;
            this.matchedTotal = matchedTotal;
        }

        public List<LogEntry> getEntries() {
            return entries;
        }

        public long getMaxId() {
            return maxId;
        }

        public int getMatchedTotal() {
            return matchedTotal;
        }
    }
}
