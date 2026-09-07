package com.zhixu.kb.security;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪过滤器：为每个请求生成 traceId 注入 MDC，便于日志串联与告警定位。
 */
@Component
public class RequestTraceFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private static final int MAX_TRACE_ID_LENGTH = 32;
    private static final String TRACE_ID_PATTERN = "^[A-Za-z0-9_-]+$";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (!isValidTraceId(traceId)) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }

    private boolean isValidTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return false;
        }
        String trimmed = traceId.trim();
        return trimmed.length() <= MAX_TRACE_ID_LENGTH && trimmed.matches(TRACE_ID_PATTERN);
    }
}
