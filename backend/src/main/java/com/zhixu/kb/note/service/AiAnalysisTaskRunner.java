package com.zhixu.kb.note.service;

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
    public void run(Long userId, Long noteId, AiAnalysisTaskManager manager, LoginUser loginUser) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (loginUser != null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            context.setAuthentication(authentication);
        }
        SecurityContextHolder.setContext(context);
        try {
            aiAnalysisExecutor.execute(userId, noteId);
            manager.complete(noteId, null);
            log.info("AI analysis task finished: noteId={}", noteId);
        } catch (Exception ex) {
            log.error("AI analysis task failed: noteId={}", noteId, ex);
            manager.complete(noteId, ex.getMessage());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
