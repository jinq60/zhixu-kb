package com.zhixu.kb.admin.log;

import com.zhixu.kb.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * 管理端系统日志：内存环形缓冲实时查询（免翻 docker logs）。
 * 深度分析（历史/聚合/图表）仍走 Kibana（ELK 栈，profile: logs）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/system")
@PreAuthorize("hasRole('admin')")
public class AdminLogController {

    private static final int MAX_LIMIT = 1000;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    @GetMapping("/logs")
    public Result<Map<String, Object>> logs(
            @RequestParam(value = "level", defaultValue = "ALL") String level,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "sinceId", defaultValue = "0") long sinceId,
            @RequestParam(value = "limit", defaultValue = "300") Integer limit) {
        int safeLimit = Math.max(1, Math.min(MAX_LIMIT, limit == null ? 300 : limit));
        SystemLogRingBuffer.QueryResult result =
                SystemLogRingBuffer.query(sinceId, level, keyword, safeLimit);
        Map<String, Object> data = new HashMap<>();
        data.put("entries", result.getEntries());
        data.put("maxId", result.getMaxId());
        data.put("matchedTotal", result.getMatchedTotal());
        return Result.success(data);
    }

    /** 导出当前缓冲为文本（控制台同款格式），便于离线排查/归档 */
    @GetMapping("/logs/download")
    public ResponseEntity<String> download() {
        SystemLogRingBuffer.QueryResult result = SystemLogRingBuffer.query(0, "ALL", null, MAX_LIMIT);
        StringBuilder sb = new StringBuilder();
        for (SystemLogRingBuffer.LogEntry entry : result.getEntries()) {
            sb.append(TIME_FORMAT.format(Instant.ofEpochMilli(entry.getTimestamp())))
                    .append(" [").append(entry.getThread()).append("] [")
                    .append(entry.getTraceId()).append("] ")
                    .append(entry.getLevel()).append(' ')
                    .append(entry.getLogger()).append(" - ")
                    .append(entry.getMessage()).append('\n');
        }
        String filename = "zhixu-backend-logs-"
                + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                + ".log";
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.TEXT_PLAIN)
                .body(sb.toString());
    }
}
