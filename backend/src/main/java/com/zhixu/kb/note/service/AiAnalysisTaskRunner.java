package com.zhixu.kb.note.service;

import com.zhixu.kb.common.utils.SecurityUtils;
import com.zhixu.kb.system.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * AI 整理异步执行器：独立线程池执行整理任务，不阻塞 HTTP 请求。
 * 异步线程无请求上下文，需手动注入安全上下文供所有权校验使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiAnalysisTaskRunner {

    private final AiAnalysisExecutor aiAnalysisExecutor;

    @Async("aiTaskExecutor")
    public void run(Long userId, Long noteId, AiAnalysisTaskManager manager, long generation, LoginUser loginUser) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (loginUser != null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            context.setAuthentication(authentication);
        }
        SecurityContextHolder.setContext(context);
        try {
            manager.updateStage(noteId, generation, "AI 分析中（调用大模型）");
            aiAnalysisExecutor.execute(userId, noteId);
            manager.complete(noteId, generation, null);
            log.info("AI analysis task finished: noteId={}", noteId);
        } catch (Exception ex) {
            log.error("AI analysis task failed: noteId={}", noteId, ex);
            manager.complete(noteId, generation, friendlyMessage(ex));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    /**
     * 供后台自动触发使用（无 HTTP 登录上下文），通过 ThreadLocal 设置用户 ID，供 AI 引擎路由用户级配置。
     */
    @Async("aiTaskExecutor")
    public void runAuto(Long userId, Long noteId, AiAnalysisTaskManager manager, long generation) {
        SecurityUtils.setUserId(userId);
        try {
            manager.updateStage(noteId, generation, "AI 分析中（调用大模型）");
            aiAnalysisExecutor.execute(userId, noteId);
            manager.complete(noteId, generation, null);
            log.info("AI analysis task finished: noteId={}", noteId);
        } catch (Exception ex) {
            log.error("AI analysis task failed: noteId={}", noteId, ex);
            manager.complete(noteId, generation, friendlyMessage(ex));
        } finally {
            SecurityUtils.clear();
        }
    }

    /**
     * 把底层异常映射为对用户友好的提示（避免展示 402 原文/堆栈等原始信息）。
     */
    private String friendlyMessage(Exception ex) {
        if (ex instanceof com.zhixu.kb.common.exception.BusinessException) {
            return ex.getMessage();
        }
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        String hint = "；可前往「AI 设置」配置你自己的 API Key 后重试";
        if (msg.contains("402") || msg.contains("payment") || msg.contains("credits") || msg.contains("balance")) {
            return "AI 引擎余额不足或额度受限（平台默认额度可能已用尽）" + hint;
        }
        if (msg.contains("401") || msg.contains("403") || msg.contains("api key") || msg.contains("auth")) {
            return "AI 引擎认证失败，请检查 API Key" + hint;
        }
        if (msg.contains("429") || msg.contains("rate limit")) {
            return "AI 引擎请求过于频繁（限流），请稍后重试" + hint;
        }
        if (msg.contains("timeout") || msg.contains("timed out")) {
            return "AI 引擎响应超时，请稍后重试" + hint;
        }
        if (msg.contains("暂时无法调用外部模型")) {
            return "AI 引擎暂时不可用" + hint;
        }
        String raw = ex.getMessage() == null ? "未知错误" : ex.getMessage();
        return (raw.length() > 200 ? raw.substring(0, 200) : raw) + hint;
    }
}
