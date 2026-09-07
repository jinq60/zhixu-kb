package com.zhixu.kb.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.common.config.NonRedirectingSimpleClientHttpRequestFactory;
import com.zhixu.kb.config.AiProperties;
import com.zhixu.kb.ai.service.UserAiConfigService;
import com.zhixu.kb.common.utils.SafeUrlValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;

@Component
public class OpenAiAdapter implements AIEngineAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiAdapter.class);

    private final RestTemplate restTemplate;
    /** 非流式补全专用：读超时按配置（默认 120s），避免挂起的端点长期占用线程（共享 RestTemplate 读超时为 300s） */
    private final RestTemplate completionRestTemplate;
    private final AiProperties aiProperties;
    private final ObjectMapper objectMapper;
    private final UserAiConfigService userAiConfigService;
    private final AiApiPool aiApiPool;

    public OpenAiAdapter(RestTemplate restTemplate,
                         AiProperties aiProperties,
                         ObjectMapper objectMapper,
                         UserAiConfigService userAiConfigService,
                         AiApiPool aiApiPool) {
        this.restTemplate = restTemplate;
        this.aiProperties = aiProperties;
        this.objectMapper = objectMapper;
        this.userAiConfigService = userAiConfigService;
        this.aiApiPool = aiApiPool;
        // P0-1 修复：禁用自动重定向，302 到内网不再跟随（SSRF 防护，需与 validateBeforeRequest 配合）
        NonRedirectingSimpleClientHttpRequestFactory completionFactory =
                new NonRedirectingSimpleClientHttpRequestFactory();
        completionFactory.setConnectTimeout(5000);
        int readTimeout = aiProperties.getApi().getTimeoutMs() == null ? 120000 : aiProperties.getApi().getTimeoutMs();
        completionFactory.setReadTimeout(Math.max(10000, readTimeout));
        this.completionRestTemplate = new RestTemplate(completionFactory);
    }

    /**
     * 当前请求线程是否已尝试过用户自配端点（失败后回落平台端点池）。
     * 使用实例字段，避免静态 ThreadLocal 在线程池复用场景下跨请求污染。
     */
    private final ThreadLocal<Boolean> userConfigTried = new ThreadLocal<>();

    /** 最近一次上游失败的 HTTP 状态（用于降级文案给出针对性引导，如 402 余额不足） */
    private final ThreadLocal<Integer> lastErrorStatus = new ThreadLocal<>();

    /**
     * 从异常链中提取上游 HTTP 状态码（RestTemplate 的 HttpStatusCodeException 或
     * streamCompletion 抛出的 "http 402 ..." 消息），无法识别返回 0。
     */
    private int extractHttpStatus(Throwable ex) {
        if (ex instanceof org.springframework.web.client.HttpStatusCodeException) {
            return ((org.springframework.web.client.HttpStatusCodeException) ex).getRawStatusCode();
        }
        Throwable cause = ex;
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("http (\\d{3})").matcher(msg);
                if (m.find()) {
                    return Integer.parseInt(m.group(1));
                }
            }
            cause = cause.getCause();
        }
        return 0;
    }

    /**
     * 解析当前请求生效的云端 API 配置：
     * 用户自配（多厂商）优先；用户端点失败后回落平台端点池（失败自动切换）。
     */
    private ResolvedApi resolveApi() {
        Boolean tried = userConfigTried.get();
        if (tried == null || !tried) {
            java.util.Optional<UserAiConfigService.ResolvedApiConfig> userConfig = userAiConfigService.resolveApiConfig();
            if (userConfig.isPresent()) {
                UserAiConfigService.ResolvedApiConfig cfg = userConfig.get();
                // SSRF 防护：实际发起请求前再次校验用户自配地址
                SafeUrlValidator.validateBeforeRequest(cfg.getBaseUrl());
                return new ResolvedApi(cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModel(),
                        aiProperties.getApi().getTimeoutMs(), aiProperties.getApi().getMaxRetries(), null, true);
            }
        }
        AiApiPool.Endpoint endpoint = aiApiPool.select();
        if (endpoint != null) {
            // SSRF 防护：端点池地址在调用前重新校验（防止保存后 DNS 被重绑定）
            SafeUrlValidator.validateBeforeRequest(endpoint.getBaseUrl());
            return new ResolvedApi(endpoint.getBaseUrl(), endpoint.getApiKey(), endpoint.getModel(),
                    aiProperties.getApi().getTimeoutMs(), aiProperties.getApi().getMaxRetries(), endpoint, false);
        }
        ResolvedApi resolved = new ResolvedApi(
                aiProperties.getApi().getBaseUrl(),
                aiProperties.getApi().getApiKey(),
                aiProperties.getApi().getModel(),
                aiProperties.getApi().getTimeoutMs(),
                aiProperties.getApi().getMaxRetries(), null, false);
        if (StringUtils.hasText(resolved.getBaseUrl())) {
            SafeUrlValidator.validateBeforeRequest(resolved.getBaseUrl());
        }
        return resolved;
    }

    private void markEndpointFailure(ResolvedApi api) {
        if (api == null) {
            return;
        }
        if (api.isUserConfig()) {
            // 用户自配端点失败：标记后下一轮回落平台端点池
            userConfigTried.set(Boolean.TRUE);
            log.warn("user ai config failed, fallback to platform endpoint pool: baseUrl={} model={}",
                    api.getBaseUrl(), api.getModel());
        }
        aiApiPool.markFailure(api.getPoolEndpoint());
    }

    private void clearEndpointState(ResolvedApi api) {
        userConfigTried.remove();
        if (api != null && !api.isUserConfig()) {
            aiApiPool.markSuccess(api.getPoolEndpoint());
        }
    }

    /**
     * 客户端取消流式应答（SseEmitter 断开等）：属于客户端行为，
     * 不得将其归因为上游端点故障（避免污染共享端点池冷却）。
     */
    private static final class ClientStreamCancelledException extends RuntimeException {
        ClientStreamCancelledException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * 上游在输出部分内容后未发送 [DONE] 即断流（截断应答）。
     * 不重试（避免向客户端重放重复内容），仅标记端点失败并追加降级提示。
     */
    private static final class StreamTruncatedException extends IllegalStateException {
        StreamTruncatedException(String message) {
            super(message);
        }
    }

    @Override
    public String generateResponse(String prompt, Map<String, Object> parameters) {
        long start = System.currentTimeMillis();
        ResolvedApi api = resolveApi();
        if (!StringUtils.hasText(api.getApiKey())) {
            return fallback();
        }
        int attempts = Math.max(1, api.getMaxRetries() == null ? 1 : api.getMaxRetries());
        try {
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    String text = requestCompletion(prompt, parameters, api);
                    if (!StringUtils.hasText(text)) {
                        throw new IllegalStateException("empty completion response");
                    }
                    clearEndpointState(api);
                    return text;
                } catch (Exception ex) {
                    // 端点失败：用户端点回落平台池；平台端点进入冷却，下一次循环自动切换
                    lastErrorStatus.set(extractHttpStatus(ex));
                    log.warn("ai request failed: attempt={} err={}", attempt, ex.getMessage());
                    markEndpointFailure(api);
                    if (attempt < attempts) {
                        api = resolveApi();
                        sleepBackoff(attempt);
                    }
                }
            }
            log.warn("traceId={} aiEngine=openapi action=generate all_attempts_failed costMs={}",
                    MDC.get("traceId"), System.currentTimeMillis() - start);
            // 降级文案必须在全部重试结束后生成：此时 lastErrorStatus 才携带本次失败的
            // HTTP 状态（402/401/429 等），提前计算会永远返回通用文案、针对性引导失效
            return fallback();
        } finally {
            // 任何退出路径（含 resolveApi 异常）都必须清理线程本地状态，防止跨请求串用
            userConfigTried.remove();
            lastErrorStatus.remove();
        }
    }

    @Override
    public void generateStreamResponse(String prompt,
                                       Map<String, Object> parameters,
                                       Consumer<String> chunkConsumer) {
        if (chunkConsumer == null) {
            return;
        }
        long start = System.currentTimeMillis();
        ResolvedApi api = resolveApi();
        try {
            if (!StringUtils.hasText(api.getApiKey())) {
                streamFallback(chunkConsumer);
                return;
            }

            int attempts = maxAttempts();
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    boolean streamed = streamCompletion(prompt, parameters, chunkConsumer, api);
                    if (streamed) {
                        clearEndpointState(api);
                        return;
                    }
                } catch (ClientStreamCancelledException ex) {
                    // 客户端主动断开：不算端点故障，不冷却、不降级
                    log.debug("client cancelled ai stream: traceId={}", MDC.get("traceId"));
                    return;
                } catch (StreamTruncatedException ex) {
                    // 截断应答：标记端点失败但不重试（重试会重放已输出内容），追加降级提示后结束
                    log.warn("ai stream truncated: traceId={} err={}", MDC.get("traceId"), ex.getMessage());
                    lastErrorStatus.set(extractHttpStatus(ex));
                    markEndpointFailure(api);
                    break;
                } catch (Exception ex) {
                    // 端点失败：用户端点回落平台池；平台端点进入冷却并切换
                    lastErrorStatus.set(extractHttpStatus(ex));
                    log.warn("ai stream request failed: attempt={} err={}", attempt, ex.getMessage());
                    markEndpointFailure(api);
                    if (attempt >= attempts) {
                        break;
                    }
                    api = resolveApi();
                }
                sleepBackoff(attempt);
            }
            streamFallback(chunkConsumer);
        } finally {
            userConfigTried.remove();
            lastErrorStatus.remove();
            long cost = System.currentTimeMillis() - start;
            log.info("traceId={} aiEngine=openapi action=stream costMs={} promptDigest={}",
                    MDC.get("traceId"),
                    cost,
                    digestText(prompt));
        }
    }

    @Override
    public boolean isHealthy() {
        ResolvedApi api;
        try {
            api = resolveApi();
        } catch (Exception ex) {
            log.warn("openapi health check resolve failed: {}", ex.getMessage());
            return false;
        }
        if (!StringUtils.hasText(api.getApiKey())) {
            return false;
        }
        // 健康探测按端点（baseUrl+model）缓存 60 秒：用户自配端点与平台端点互不污染，
        // 且避免每次 AI 调用都额外请求 /models
        String cacheKey = api.getBaseUrl() + "|" + api.getModel();
        long now = System.currentTimeMillis();
        HealthEntry cached = healthCache.get(cacheKey);
        if (cached != null && now - cached.probedAt < HEALTH_PROBE_TTL_MS) {
            return cached.healthy;
        }
        try {
            String url = api.getBaseUrl() + "/models";
            HttpEntity<Void> entity = new HttpEntity<Void>(buildHeaders(api));
            // 健康探测使用独立短超时（5s/10s），避免慢端点把 health 接口拖住
            ResponseEntity<Map> response = healthRestTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            boolean healthy = response.getStatusCode().is2xxSuccessful();
            putHealthCache(cacheKey, now, healthy);
            return healthy;
        } catch (Exception ex) {
            log.warn("openapi health check failed: url={} err={}", api.getBaseUrl() + "/models", ex.getMessage());
            putHealthCache(cacheKey, now, false);
            return false;
        }
    }

    private static final long HEALTH_PROBE_TTL_MS = 60_000L;
    /** 单次补全输出 token 上限（调用方可更低，不可更高） */
    private static final int MAX_OUTPUT_TOKENS = 4096;

    private final java.util.concurrent.ConcurrentHashMap<String, HealthEntry> healthCache =
            new java.util.concurrent.ConcurrentHashMap<>();

    private void putHealthCache(String key, long probedAt, boolean healthy) {
        // 容量保护：端点数异常膨胀时整体清空，缓存仅用于削峰探测频率
        if (healthCache.size() > 100) {
            healthCache.clear();
        }
        HealthEntry entry = new HealthEntry();
        entry.probedAt = probedAt;
        entry.healthy = healthy;
        healthCache.put(key, entry);
    }

    private static final class HealthEntry {
        volatile long probedAt;
        volatile boolean healthy;
    }

    private static final RestTemplate healthRestTemplate = createHealthRestTemplate();

    private static RestTemplate createHealthRestTemplate() {
        // P0-1 修复：健康探测同样禁重定向
        NonRedirectingSimpleClientHttpRequestFactory factory =
                new NonRedirectingSimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(10000);
        return new RestTemplate(factory);
    }

    private HttpHeaders buildHeaders(ResolvedApi api) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(api.getApiKey());
        return headers;
    }

    private String requestCompletion(String sanitizedPrompt,
                                     Map<String, Object> parameters,
                                     ResolvedApi api) {
        String url = api.getBaseUrl() + "/chat/completions";
        HttpHeaders headers = buildHeaders(api);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<Map<String, Object>>(
                buildChatPayload(sanitizedPrompt, parameters, false, api),
                headers
        );
        ResponseEntity<Map> response = completionRestTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
        Map<String, Object> body = response.getBody();
        if (body == null) {
            return null;
        }
        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List) || ((List) choicesObj).isEmpty()) {
            return null;
        }
        Object first = ((List) choicesObj).get(0);
        if (!(first instanceof Map)) {
            return null;
        }
        Object messageObj = ((Map) first).get("message");
        if (!(messageObj instanceof Map)) {
            return null;
        }
        Object contentObj = ((Map) messageObj).get("content");
        return contentObj == null ? null : contentObj.toString();
    }

    private boolean streamCompletion(String prompt,
                                     Map<String, Object> parameters,
                                     Consumer<String> chunkConsumer,
                                     ResolvedApi api) throws Exception {
        String url = api.getBaseUrl() + "/chat/completions";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        BufferedReader reader = null;
        try {
            // SSRF 防护：禁止自动跟随重定向
            connection.setInstanceFollowRedirects(false);
            int timeoutMs = api.getTimeoutMs() == null ? 30000 : api.getTimeoutMs();
            connection.setConnectTimeout(Math.max(1000, timeoutMs));
            // 流式应答按块间最长等待 120s（模型长思考不会误断流）
            connection.setReadTimeout(120_000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + api.getApiKey());

            Map<String, Object> payload = buildChatPayload(prompt, parameters, true, api);
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(bytes);
                outputStream.flush();
            }

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("stream completion failed: http " + status + " " + readBody(connection.getErrorStream()));
            }

            InputStream stream = connection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean emitted = false;
            boolean done = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.length() == 0 || !trimmed.startsWith("data:")) {
                    continue;
                }
                String json = trimmed.substring(5).trim();
                if ("[DONE]".equals(json)) {
                    done = true;
                    break;
                }
                String delta = extractStreamContent(json);
                if (StringUtils.hasText(delta)) {
                    try {
                        chunkConsumer.accept(delta);
                        emitted = true;
                    } catch (ClientStreamCancelledException e) {
                        throw e;
                    } catch (RuntimeException e) {
                        // 消费方（SseEmitter）异常 = 客户端断开，不视为上游故障
                        throw new ClientStreamCancelledException(e);
                    }
                }
            }
            if (!done && emitted) {
                // 上游未发送 [DONE] 就断流：按截断处理，不吞掉截断答案
                throw new StreamTruncatedException("stream terminated unexpectedly before [DONE]");
            }
            if (!done) {
                return false;
            }
            return true;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                    // ignore close error
                }
            }
            connection.disconnect();
        }
    }

    private Map<String, Object> buildChatPayload(String sanitizedPrompt,
                                                 Map<String, Object> parameters,
                                                 boolean stream,
                                                 ResolvedApi api) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("model", api.getModel());

        double temperature = 0.2;
        if (parameters != null && parameters.get("temperature") != null) {
            try {
                temperature = Double.parseDouble(parameters.get("temperature").toString());
            } catch (Exception ignored) {
                temperature = 0.2;
            }
        }
        payload.put("temperature", temperature);
        payload.put("stream", stream);

        // 输出长度上限：调用方可显式指定；未指定时施加默认上限，
        // 防止补全输出不受控导致平台 Key 配额被单类请求打满（费用滥用面）
        if (parameters != null && parameters.get("max_tokens") != null) {
            try {
                int maxTokens = Integer.parseInt(parameters.get("max_tokens").toString());
                if (maxTokens > 0) {
                    payload.put("max_tokens", Math.min(maxTokens, MAX_OUTPUT_TOKENS));
                }
            } catch (Exception ignored) {
                // ignore invalid max_tokens
            }
        } else {
            payload.put("max_tokens", MAX_OUTPUT_TOKENS);
        }

        Map<String, String> system = new HashMap<String, String>();
        system.put("role", "system");
        system.put("content", "你是知识问答助手，回答准确、简洁，不确定时如实说明。");

        Map<String, String> user = new HashMap<String, String>();
        user.put("role", "user");
        user.put("content", sanitizedPrompt);

        payload.put("messages", Arrays.asList(system, user));
        return payload;
    }

    private String extractStreamContent(String jsonLine) {
        try {
            JsonNode root = objectMapper.readTree(jsonLine);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.size() == 0) {
                return "";
            }
            JsonNode first = choices.get(0);
            String delta = first.path("delta").path("content").asText("");
            if (delta != null && delta.length() > 0) {
                return delta;
            }
            return first.path("message").path("content").asText("");
        } catch (Exception ex) {
            return "";
        }
    }

    private String readBody(InputStream stream) {
        if (stream == null) {
            return "";
        }
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(line.trim());
            }
            reader.close();
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private void streamFallback(Consumer<String> chunkConsumer) {
        String text = fallback();
        for (int i = 0; i < text.length(); i += 24) {
            chunkConsumer.accept(text.substring(i, Math.min(i + 24, text.length())));
        }
    }

    private int maxAttempts() {
        Integer retries = aiProperties.getApi().getMaxRetries();
        if (retries == null || retries < 1) {
            return 1;
        }
        return retries;
    }

    private void sleepBackoff(int attempt) {
        try {
            Thread.sleep(200L * Math.max(1, attempt));
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 降级文案：始终以"暂时无法调用外部模型"开头（清洗/整理链路据此识别并降级到本地规则），
     * 并按失败原因给出针对性引导（402 余额不足 / 401 无效 Key / 429 限流 / 其他），
     * 引导用户到「AI 设置」配置自己的 API Key。
     */
    private String fallback() {
        Integer status = lastErrorStatus.get();
        switch (status == null ? 0 : status) {
            case 402:
                return "暂时无法调用外部模型（AI 引擎不可用）：平台默认 API 额度已用尽或余额不足（HTTP 402）。"
                        + "请前往「AI 设置」配置你自己的 API Key（DeepSeek / OpenAI / 通义千问 / 智谱 / Kimi 等均可），"
                        + "配置成功后即可正常使用 AI 功能。";
            case 401:
            case 403:
                return "暂时无法调用外部模型（AI 引擎不可用）：平台默认 API Key 无效或已失效（HTTP " + lastErrorStatus + "）。"
                        + "请前往「AI 设置」配置你自己的 API Key，配置成功后即可正常使用。";
            case 429:
                return "暂时无法调用外部模型（AI 引擎不可用）：请求过于频繁，已被限流（HTTP 429）。"
                        + "请稍后重试，或前往「AI 设置」配置你自己的 API Key 以获得独立配额。";
            default:
                return "暂时无法调用外部模型（AI 引擎不可用）：平台默认模型暂时无法访问。"
                        + "请前往「AI 设置」配置你自己的 API Key（DeepSeek / OpenAI / 通义千问 / 智谱 / Kimi 等均可），"
                        + "配置成功后即可正常使用 AI 功能。";
        }
    }

    private String digestText(String text) {
        if (text == null) {
            return "null";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "digest-error";
        }
    }

    /**
     * 当前请求生效的云端 API 配置（用户自配优先，否则平台端点池轮询）。
     */
    private static class ResolvedApi {
        private final String baseUrl;
        private final String apiKey;
        private final String model;
        private final Integer timeoutMs;
        private final Integer maxRetries;
        private final AiApiPool.Endpoint poolEndpoint;
        private final boolean userConfig;

        ResolvedApi(String baseUrl, String apiKey, String model, Integer timeoutMs, Integer maxRetries,
                    AiApiPool.Endpoint poolEndpoint, boolean userConfig) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.timeoutMs = timeoutMs;
            this.maxRetries = maxRetries;
            this.poolEndpoint = poolEndpoint;
            this.userConfig = userConfig;
        }

        boolean isUserConfig() {
            return userConfig;
        }

        String getBaseUrl() {
            return baseUrl;
        }

        String getApiKey() {
            return apiKey;
        }

        String getModel() {
            return model;
        }

        Integer getTimeoutMs() {
            return timeoutMs;
        }

        Integer getMaxRetries() {
            return maxRetries;
        }

        AiApiPool.Endpoint getPoolEndpoint() {
            return poolEndpoint;
        }
    }
}
