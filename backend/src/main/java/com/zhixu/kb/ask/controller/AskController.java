package com.zhixu.kb.ask.controller;

import com.zhixu.kb.ask.model.AskExportPayload;
import com.zhixu.kb.ask.model.AskRecord;
import com.zhixu.kb.ask.model.AskRequest;
import com.zhixu.kb.ask.service.AskService;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.Result;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 知识问答接口：基于个人知识库（笔记）的问答，支持 SSE 流式回答。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/ask")
public class AskController {

    private final AskService askService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    /**
     * 同步问答并发护栏：单次 AI 调用含重试最长可挂起数分钟，
     * 无界并发会占死 Tomcat worker 线程拖垮全站。流式接口走 mvc-async 有界池，不受此限。
     */
    private static final int SYNC_ASK_MAX_CONCURRENT = 4;
    private static final long SYNC_ASK_ACQUIRE_TIMEOUT_SECONDS = 3;
    private final Semaphore syncAskPermits = new Semaphore(SYNC_ASK_MAX_CONCURRENT);

    @PostMapping
    public Result<AskRecord> ask(@Valid @RequestBody AskRequest request) {
        boolean acquired = false;
        try {
            acquired = syncAskPermits.tryAcquire(SYNC_ASK_ACQUIRE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "请求被中断，请重试");
        }
        if (!acquired) {
            throw new BusinessException(ResultCode.TOO_MANY_REQUESTS, "当前同步问答并发已满，请使用流式问答或稍后再试");
        }
        try {
            return Result.success(askService.ask(SecurityUtils.getUserId(), request.getQuestion(), request.getConversationId()));
        } finally {
            syncAskPermits.release();
        }
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> askStream(@Valid @RequestBody AskRequest request) {
        Long userId = SecurityUtils.getUserId();
        StreamingResponseBody body = outputStream -> {
            try {
                // 首帧心跳（SSE 注释帧，前端按规范跳过非 data: 行）：
                // 重置 Nginx 默认 60s proxy_read_timeout，避免上游模型首 token 前连接被掐断
                outputStream.write(": ping\n\n".getBytes(StandardCharsets.UTF_8));
                outputStream.flush();
                askService.askStreaming(userId, request.getQuestion(), request.getConversationId(), chunk -> {
                    try {
                        // SSE 帧：chunk 统一 JSON 编码后发送，换行/空白不会被帧分隔符拆散；
                        // 前端 JSON.parse 还原原始文本
                        outputStream.write(("data: " + objectMapper.writeValueAsString(chunk) + "\n\n")
                                .getBytes(StandardCharsets.UTF_8));
                        outputStream.flush();
                    } catch (Exception ex) {
                        if (isClientAbort(ex)) {
                            throw new ClientDisconnectedException(ex);
                        }
                        throw new RuntimeException(ex);
                    }
                });
            } catch (ClientDisconnectedException ex) {
                log.info("stream closed by client: {}", ex.getMessage());
            }
        };
        return ResponseEntity.ok()
                .header("X-Accel-Buffering", "no")
                // 标准 SSE Content-Type：网关/代理按流式响应处理，避免缓冲或按纯文本截断
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(body);
    }

    @GetMapping("/{id}")
    public Result<AskRecord> detail(@PathVariable("id") String id) {
        return Result.success(askService.getById(SecurityUtils.getUserId(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable("id") String id) {
        askService.deleteById(SecurityUtils.getUserId(), id);
        return Result.success("问答记录删除成功", Boolean.TRUE);
    }

    @GetMapping("/{id}/export")
    public Result<Map<String, String>> export(@PathVariable("id") String id) {
        AskExportPayload payload = askService.exportById(SecurityUtils.getUserId(), id);
        Map<String, String> data = new HashMap<>();
        data.put("fileName", payload.getFileName());
        data.put("content", payload.getContent());
        return Result.success("导出内容已生成", data);
    }

    @GetMapping
    public Result<List<AskRecord>> history(@RequestParam(value = "page", defaultValue = "1") Integer page,
                                           @RequestParam(value = "size", defaultValue = "20") Integer size) {
        return Result.success(askService.history(SecurityUtils.getUserId(), page, size));
    }

    private boolean isClientAbort(Throwable ex) {
        Throwable cursor = ex;
        while (cursor != null) {
            String className = cursor.getClass().getName();
            if (className.contains("ClientAbortException")
                    || className.contains("AsyncRequestTimeoutException")
                    || className.contains("EOFException")) {
                return true;
            }
            String message = cursor.getMessage();
            if (message != null && containsAbortMessage(message)) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private boolean containsAbortMessage(String message) {
        String normalized = message.toLowerCase();
        return normalized.contains("broken pipe")
                || normalized.contains("connection reset")
                || normalized.contains("current thread was interrupted")
                || normalized.contains("pipe has been ended")
                || normalized.contains("forcibly closed");
    }

    private static class ClientDisconnectedException extends RuntimeException {
        ClientDisconnectedException(Throwable cause) {
            super(cause);
        }
    }
}
