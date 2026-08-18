package com.zhixu.kb.ai;

import com.zhixu.kb.config.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.function.Consumer;

/**
 * AI 引擎路由：统一走云端 OpenAI 兼容 API（用户自配 Key 优先，否则平台默认 Key）。
 * 调用失败时自动重试并返回降级提示，不中断业务流程。
 */
@Component
public class AIEngineAdapterRouter {

    private static final Logger log = LoggerFactory.getLogger(AIEngineAdapterRouter.class);

    private final AiProperties aiProperties;
    private final AIEngineRuntimeSwitchService runtimeSwitchService;
    private final OpenAiAdapter openAiAdapter;

    public AIEngineAdapterRouter(AiProperties aiProperties,
                                 AIEngineRuntimeSwitchService runtimeSwitchService,
                                 OpenAiAdapter openAiAdapter) {
        this.aiProperties = aiProperties;
        this.runtimeSwitchService = runtimeSwitchService;
        this.openAiAdapter = openAiAdapter;
    }

    public AIEngineAdapter current() {
        return openAiAdapter;
    }

    public String engineType() {
        return resolveEngineType();
    }

    public String configuredEngineType() {
        return normalizeEngineType(aiProperties.getEngineType());
    }

    public String overrideEngineType() {
        return runtimeSwitchService.overrideEngineType();
    }

    public boolean overrideEnabled() {
        return runtimeSwitchService.hasOverride();
    }

    public String generateResponseResilient(String prompt, Map<String, Object> parameters) {
        Map<String, Object> safeParameters = parameters == null ? Collections.emptyMap() : parameters;
        AIEngineAdapter adapter = openAiAdapter;
        String text = "";
        if (adapter.isHealthy()) {
            text = requestWithAttempts(adapter, prompt, safeParameters, 2);
        } else {
            log.warn("ai engine unavailable before request");
            text = adapter.generateResponse(prompt, safeParameters);
        }
        if (isSuccessful(text)) {
            return text;
        }
        return text == null ? "" : text;
    }

    public void generateStreamResponseResilient(String prompt,
                                                Map<String, Object> parameters,
                                                Consumer<String> chunkConsumer) {
        if (chunkConsumer == null) {
            return;
        }
        Map<String, Object> safeParameters = parameters == null ? Collections.emptyMap() : parameters;
        try {
            openAiAdapter.generateStreamResponse(prompt, safeParameters, chunkConsumer);
        } catch (Exception ex) {
            // 流式失败直接结束，不重放输出（重放会让客户端收到重复/残缺内容）
            log.warn("ai stream failed: {}, no replay", ex.getClass().getSimpleName());
        }
    }

    private String resolveEngineType() {
        String overrideEngineType = runtimeSwitchService.overrideEngineType();
        if (overrideEngineType != null && overrideEngineType.trim().length() > 0) {
            return normalizeEngineType(overrideEngineType);
        }
        return normalizeEngineType(aiProperties.getEngineType());
    }

    private String normalizeEngineType(String engineType) {
        // 本地模型已移除，仅保留云端 OpenAI 兼容 API
        return "api";
    }

    private String requestWithAttempts(AIEngineAdapter adapter,
                                       String prompt,
                                       Map<String, Object> parameters,
                                       int attempts) {
        if (adapter == null) {
            return "";
        }
        int maxAttempts = Math.max(1, attempts);
        for (int i = 1; i <= maxAttempts; i++) {
            try {
                String text = adapter.generateResponse(prompt, parameters);
                if (isSuccessful(text)) {
                    return text;
                }
                if (i >= maxAttempts) {
                    return text;
                }
            } catch (Exception ex) {
                if (i >= maxAttempts) {
                    log.warn("ai request failed: attempts={} err={}", maxAttempts, ex.getClass().getSimpleName());
                    return "";
                }
            }
            sleepBackoff(i);
        }
        return "";
    }

    private boolean isSuccessful(String text) {
        if (text == null || text.trim().length() == 0) {
            return false;
        }
        String normalized = text.toLowerCase();
        return normalized.indexOf("temporarily unavailable") < 0
                && normalized.indexOf("not available") < 0
                && normalized.indexOf("api key") < 0
                && normalized.indexOf("fallback") < 0
                && normalized.indexOf("暂时无法调用外部模型") < 0
                && normalized.indexOf("当前未配置外部api key") < 0;
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(220L * Math.max(1, attempt));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
