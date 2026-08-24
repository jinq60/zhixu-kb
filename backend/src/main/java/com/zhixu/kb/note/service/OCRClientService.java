package com.zhixu.kb.note.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.note.config.OCRClientProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class OCRClientService {

    /** 熔断：连续失败 N 次后开路，冷却期内直接快速失败，避免每次请求阻塞 60-120s */
    private static final int CIRCUIT_FAILURE_THRESHOLD = 3;
    private static final long CIRCUIT_OPEN_MS = 30_000;

    private final OCRClientProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile long circuitOpenedAt = 0;
    private volatile RestTemplate restTemplate;

    public List<String> recognize(byte[] imageBytes) {
        return recognize(imageBytes, properties.getEngine());
    }

    public List<String> recognize(byte[] imageBytes, String requestedEngine) {
        checkCircuit();

        String primaryEngine = normalizeEngine(requestedEngine);
        if (!StringUtils.hasText(primaryEngine)) {
            primaryEngine = normalizeEngine(properties.getEngine());
        }
        if (!StringUtils.hasText(primaryEngine)) {
            primaryEngine = "auto";
        }

        try {
            String body = invokeRecognize(imageBytes, primaryEngine);
            List<String> result = parseRecognizeResponse(body);
            onSuccess();
            return result;
        } catch (BusinessException primaryErr) {
            onFailure();
            String fallbackEngine = normalizeEngine(properties.getFallbackEngine());
            boolean canRetry = StringUtils.hasText(fallbackEngine)
                    && !fallbackEngine.equalsIgnoreCase(primaryEngine)
                    && ResultCode.SERVICE_UNAVAILABLE.getCode().equals(primaryErr.getCode());
            if (!canRetry) {
                throw primaryErr;
            }

            log.warn("OCR primary engine '{}' failed, retrying fallback '{}'", primaryEngine, fallbackEngine);
            try {
                String body = invokeRecognize(imageBytes, fallbackEngine);
                List<String> fallbackResult = parseRecognizeResponse(body);
                onSuccess();
                return fallbackResult;
            } catch (BusinessException fallbackErr) {
                // 主引擎与 fallback 均失败：各计入一次连续失败，保证熔断阈值语义准确
                onFailure();
                throw fallbackErr;
            }
        }
    }

    /**
     * 熔断检查：开路期间直接快速失败，避免 OCR 服务不可用时每个请求都阻塞至超时。
     */
    private void checkCircuit() {
        long openedAt = circuitOpenedAt;
        if (openedAt > 0) {
            long elapsed = System.currentTimeMillis() - openedAt;
            if (elapsed < CIRCUIT_OPEN_MS) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "OCR 服务暂不可用，请稍后再试");
            }
            circuitOpenedAt = 0;
            consecutiveFailures.set(0);
        }
    }

    private void onSuccess() {
        circuitOpenedAt = 0;
        consecutiveFailures.set(0);
    }

    private void onFailure() {
        if (consecutiveFailures.incrementAndGet() >= CIRCUIT_FAILURE_THRESHOLD) {
            circuitOpenedAt = System.currentTimeMillis();
            log.warn("OCR 服务连续失败 {} 次，熔断 {} ms", CIRCUIT_FAILURE_THRESHOLD, CIRCUIT_OPEN_MS);
        }
    }

    private String invokeRecognize(byte[] imageBytes, String engine) {
        String url = UriComponentsBuilder
                .fromHttpUrl(properties.getUrl())
                .path("/ocr/recognize")
                .queryParam("engine", StringUtils.hasText(engine) ? engine : "auto")
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> bodyBuilder = new LinkedMultiValueMap<String, Object>();
        bodyBuilder.add("file", new ByteArrayResource(imageBytes) {
            @Override
            public String getFilename() {
                return "note.png";
            }
        });

        HttpEntity<?> entity = new HttpEntity<Object>(bodyBuilder, headers);

        try {
            ResponseEntity<String> response = restTemplate().exchange(url, HttpMethod.POST, entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "OCR service unavailable");
            }
            return response.getBody();
        } catch (RestClientResponseException ex) {
            String responseBody = ex.getResponseBodyAsString();
            String detail = extractErrorText(responseBody);
            String message = "OCR service call failed"
                    + (StringUtils.hasText(detail) ? (": " + detail) : "")
                    + " [HTTP " + ex.getRawStatusCode() + "]";
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, message);
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "OCR service call failed: " + ex.getMessage());
        }
    }

    private RestTemplate restTemplate() {
        RestTemplate existing = restTemplate;
        if (existing != null) {
            return existing;
        }
        int timeout = properties.getTimeout() == null ? 60000 : properties.getTimeout();
        if (timeout <= 0) {
            timeout = 60000;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        RestTemplate created = new RestTemplate(factory);
        restTemplate = created;
        return created;
    }

    private List<String> parseRecognizeResponse(String body) {
        try {
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            if (root.has("lines") && root.get("lines").isArray()) {
                List<String> lines = new ArrayList<String>();
                for (JsonNode node : root.get("lines")) {
                    String line = node.asText("").trim();
                    if (!line.isEmpty()) {
                        lines.add(line);
                    }
                }
                return lines;
            }

            if (root.has("text")) {
                String text = root.get("text").asText("");
                List<String> lines = new ArrayList<String>();
                for (String line : text.split("\\n")) {
                    String normalized = line.trim();
                    if (!normalized.isEmpty()) {
                        lines.add(normalized);
                    }
                }
                return lines;
            }

            String error = extractErrorText(body);
            if (StringUtils.hasText(error)) {
                throw new BusinessException(ResultCode.SERVER_ERROR, error);
            }
            throw new BusinessException(ResultCode.SERVER_ERROR, "OCR response is empty");
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Parse OCR response failed: {}", body, ex);
            throw new BusinessException(ResultCode.SERVER_ERROR, "Parse OCR result failed");
        }
    }

    private String extractErrorText(String body) {
        if (!StringUtils.hasText(body)) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("error")) {
                return root.get("error").asText("");
            }
            if (root.has("message")) {
                return root.get("message").asText("");
            }
            if (root.has("details") && root.get("details").isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode node : root.get("details")) {
                    if (sb.length() > 0) {
                        sb.append(" | ");
                    }
                    sb.append(node.asText(""));
                }
                return sb.toString();
            }
        } catch (Exception ignore) {
            // ignore parse error
        }
        return body;
    }

    private String normalizeEngine(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String value = raw.trim().toLowerCase();
        if ("auto".equals(value) || "paddle".equals(value) || "deepseek".equals(value)) {
            return value;
        }
        return "";
    }
}
