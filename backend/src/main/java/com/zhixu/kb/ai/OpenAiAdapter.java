package com.zhixu.kb.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.config.AiProperties;
import com.zhixu.kb.ai.service.UserAiConfigService;
import com.zhixu.kb.common.utils.SafeUrlValidator;
import com.zhixu.kb.security.SensitiveDataSanitizer;
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
    }

    /**
     * 解析当前请求生效的云端 API 配置：
     * 用户自配（多厂商）优先；否则从平台端点池轮询选择（失败自动切换）。
     */
    private ResolvedApi resolveApi() {
        java.util.Optional<UserAiConfigService.ResolvedApiConfig> userConfig = userAiConfigService.resolveApiConfig();
        if (userConfig.isPresent()) {
            UserAiConfigService.ResolvedApiConfig cfg = userConfig.get();
            return new ResolvedApi(cfg.getBaseUrl(), cfg.getApiKey(), cfg.getModel(),
                    aiProperties.getApi().getTimeoutMs(), aiProperties.getApi().getMaxRetries(), null);
        }
        AiApiPool.Endpoint endpoint = aiApiPool.select();
        if (endpoint != null) {
            return new ResolvedApi(endpoint.getBaseUrl(), endpoint.getApiKey(), endpoint.getModel(),
                    aiProperties.getApi().getTimeoutMs(), aiProperties.getApi().getMaxRetries(), endpoint);
        }
        ResolvedApi resolved = new ResolvedApi(
                aiProperties.getApi().getBaseUrl(),
                aiProperties.getApi().getApiKey(),
                aiProperties.getApi().getModel(),
                aiProperties.getApi().getTimeoutMs(),
                aiProperties.getApi().getMaxRetries(), null);
        if (StringUtils.hasText(resolved.getBaseUrl())) {
            SafeUrlValidator.validateBeforeRequest(resolved.getBaseUrl());
        }
        return resolved;
    }

    @Override
    public String generateResponse(String prompt, Map<String, Object> parameters) {
        String sanitizedPrompt = SensitiveDataSanitizer.maskText(prompt);
        long start = System.currentTimeMillis();
        ResolvedApi api = resolveApi();
        if (!StringUtils.hasText(api.getApiKey())) {
            return fallback();
        }
        int attempts = Math.max(1, api.getMaxRetries() == null ? 1 : api.getMaxRetries());
        String lastText = fallback();
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String text = requestCompletion(sanitizedPrompt, parameters, api);
                if (!StringUtils.hasText(text)) {
                    throw new IllegalStateException("empty completion response");
                }
                aiApiPool.markSuccess(api.getPoolEndpoint());
                return text;
            } catch (Exception ex) {
                // 端点失败：进入冷却，下一次循环自动切换到池中其他端点
                aiApiPool.markFailure(api.getPoolEndpoint());
                if (attempt < attempts) {
                    api = resolveApi();
                    sleepBackoff(attempt);
                }
            }
        }
        log.warn("traceId={} aiEngine=openapi action=generate all_attempts_failed costMs={}",
                MDC.get("traceId"), System.currentTimeMillis() - start);
        return lastText;
    }

    @Override
    public void generateStreamResponse(String prompt,
                                       Map<String, Object> parameters,
                                       Consumer<String> chunkConsumer) {
        if (chunkConsumer == null) {
            return;
        }
        String sanitizedPrompt = SensitiveDataSanitizer.maskText(prompt);
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
                    boolean streamed = streamCompletion(sanitizedPrompt, parameters, chunkConsumer, api);
                    if (streamed) {
                        aiApiPool.markSuccess(api.getPoolEndpoint());
                        return;
                    }
                } catch (Exception ex) {
                    // 端点失败：冷却并切换池中其他端点
                    aiApiPool.markFailure(api.getPoolEndpoint());
                    if (attempt >= attempts) {
                        break;
                    }
                    api = resolveApi();
                }
                sleepBackoff(attempt);
            }
            streamFallback(chunkConsumer);
        } finally {
            long cost = System.currentTimeMillis() - start;
            log.info("traceId={} aiEngine=openapi action=stream costMs={} promptDigest={}",
                    MDC.get("traceId"),
                    cost,
                    digestText(sanitizedPrompt));
        }
    }

    @Override
    public boolean isHealthy() {
        ResolvedApi api = resolveApi();
        if (!StringUtils.hasText(api.getApiKey())) {
            return false;
        }
        // 健康探测结果缓存 60 秒，避免每次 AI 调用都额外请求 /models
        long now = System.currentTimeMillis();
        if (now - lastHealthProbeMs < HEALTH_PROBE_TTL_MS) {
            return cachedHealthy;
        }
        try {
            String url = api.getBaseUrl() + "/models";
            HttpEntity<Void> entity = new HttpEntity<Void>(buildHeaders(api));
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            boolean healthy = response.getStatusCode().is2xxSuccessful();
            cachedHealthy = healthy;
            lastHealthProbeMs = now;
            return healthy;
        } catch (Exception ex) {
            log.warn("openapi health check failed: url={} err={}", api.getBaseUrl() + "/models", ex.getMessage());
            cachedHealthy = false;
            lastHealthProbeMs = now;
            return false;
        }
    }

    private static final long HEALTH_PROBE_TTL_MS = 60_000L;
    private volatile long lastHealthProbeMs = 0L;
    private volatile boolean cachedHealthy = false;

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
        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
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

    private boolean streamCompletion(String sanitizedPrompt,
                                     Map<String, Object> parameters,
                                     Consumer<String> chunkConsumer,
                                     ResolvedApi api) throws Exception {
        String url = api.getBaseUrl() + "/chat/completions";
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            // SSRF 防护：禁止自动跟随重定向
            connection.setInstanceFollowRedirects(false);
            int timeoutMs = api.getTimeoutMs() == null ? 30000 : api.getTimeoutMs();
            connection.setConnectTimeout(Math.max(1000, timeoutMs));
            connection.setReadTimeout(Math.max(1000, timeoutMs));
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            connection.setRequestProperty(HttpHeaders.AUTHORIZATION, "Bearer " + api.getApiKey());

            Map<String, Object> payload = buildChatPayload(sanitizedPrompt, parameters, true, api);
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            OutputStream outputStream = connection.getOutputStream();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();

            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("stream completion failed: http " + status + " " + readBody(connection.getErrorStream()));
            }

            InputStream stream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            boolean emitted = false;
            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.length() == 0 || !trimmed.startsWith("data:")) {
                    continue;
                }
                String json = trimmed.substring(5).trim();
                if ("[DONE]".equals(json)) {
                    break;
                }
                String delta = extractStreamContent(json);
                if (StringUtils.hasText(delta)) {
                    chunkConsumer.accept(delta);
                    emitted = true;
                }
            }
            reader.close();
            return emitted;
        } finally {
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

        // 允许调用方限制输出长度（加速生成）
        if (parameters != null && parameters.get("max_tokens") != null) {
            try {
                int maxTokens = Integer.parseInt(parameters.get("max_tokens").toString());
                if (maxTokens > 0) {
                    payload.put("max_tokens", maxTokens);
                }
            } catch (Exception ignored) {
                // ignore invalid max_tokens
            }
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

    private String fallback() {
        return "暂时无法调用外部模型，请稍后重试或检查 AI 引擎配置。";
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

        ResolvedApi(String baseUrl, String apiKey, String model, Integer timeoutMs, Integer maxRetries,
                    AiApiPool.Endpoint poolEndpoint) {
            this.baseUrl = baseUrl;
            this.apiKey = apiKey;
            this.model = model;
            this.timeoutMs = timeoutMs;
            this.maxRetries = maxRetries;
            this.poolEndpoint = poolEndpoint;
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
