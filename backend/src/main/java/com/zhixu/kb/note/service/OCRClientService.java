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

@Slf4j
@Service
@RequiredArgsConstructor
public class OCRClientService {

    private final OCRClientProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> recognize(byte[] imageBytes) {
        return recognize(imageBytes, properties.getEngine());
    }

    public List<String> recognize(byte[] imageBytes, String requestedEngine) {
        String primaryEngine = normalizeEngine(requestedEngine);
        if (!StringUtils.hasText(primaryEngine)) {
            primaryEngine = normalizeEngine(properties.getEngine());
        }
        if (!StringUtils.hasText(primaryEngine)) {
            primaryEngine = "auto";
        }

        try {
            String body = invokeRecognize(imageBytes, primaryEngine);
            return parseRecognizeResponse(body);
        } catch (BusinessException primaryErr) {
            String fallbackEngine = normalizeEngine(properties.getFallbackEngine());
            boolean canRetry = StringUtils.hasText(fallbackEngine)
                    && !fallbackEngine.equalsIgnoreCase(primaryEngine)
                    && ResultCode.SERVICE_UNAVAILABLE.getCode().equals(primaryErr.getCode());
            if (!canRetry) {
                throw primaryErr;
            }

            log.warn("OCR primary engine '{}' failed, retrying fallback '{}'", primaryEngine, fallbackEngine);
            String body = invokeRecognize(imageBytes, fallbackEngine);
            return parseRecognizeResponse(body);
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
            ResponseEntity<String> response = buildRestTemplate().exchange(url, HttpMethod.POST, entity, String.class);
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

    private RestTemplate buildRestTemplate() {
        int timeout = properties.getTimeout() == null ? 60000 : properties.getTimeout();
        if (timeout <= 0) {
            timeout = 60000;
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        return new RestTemplate(factory);
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
