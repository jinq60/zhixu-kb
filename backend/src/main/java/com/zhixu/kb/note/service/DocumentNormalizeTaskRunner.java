package com.zhixu.kb.note.service;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.system.model.LoginUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 文档清洗异步执行器：独立线程池执行清洗任务，不阻塞 HTTP 请求。
 * 异步线程无请求上下文，需手动注入安全上下文供所有权校验使用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentNormalizeTaskRunner {

    private final NoteNormalizeExecutor noteNormalizeExecutor;

    @Async("aiTaskExecutor")
    public void run(Long noteId, DocumentNormalizeTaskManager manager, long generation, LoginUser loginUser) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        if (loginUser != null) {
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities());
            context.setAuthentication(authentication);
        }
        SecurityContextHolder.setContext(context);
        try {
            noteNormalizeExecutor.execute(noteId);
            manager.complete(noteId, generation, null);
            log.info("document normalize task finished: noteId={}", noteId);
        } catch (Exception ex) {
            // 业务异常用其文案；技术异常返回用户可读的通用信息（不泄露内部路径/堆栈）
            log.error("document normalize task failed: noteId={}", noteId, ex);
            String message = ex instanceof BusinessException && ex.getMessage() != null
                    ? ex.getMessage()
                    : "清洗失败，请稍后重试";
            manager.complete(noteId, generation, message);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
