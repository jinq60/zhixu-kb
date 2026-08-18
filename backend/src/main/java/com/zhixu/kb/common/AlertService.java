package com.zhixu.kb.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixu.kb.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 告警服务：内存环形队列 + Redis 持久化，支持去重冷却。
 */
@Slf4j
@Service
public class AlertService {

    private static final String REDIS_ALERT_KEY = "alerts:recent";

    private final Deque<AlertEvent> recentAlerts = new LinkedList<>();
    private final Map<String, Long> dedupeState = new ConcurrentHashMap<>();
    private final AppProperties appProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redisTemplate;

    public AlertService(AppProperties appProperties,
                        ObjectMapper objectMapper,
                        ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.appProperties = appProperties;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    public void emit(String type, String severity, String message, Map<String, Object> metadata) {
        AlertEvent event = new AlertEvent();
        event.setId(UUID.randomUUID().toString());
        event.setType(type);
        event.setSeverity(severity);
        event.setMessage(message);
        event.setCreatedAt(Instant.now());
        event.setMetadata(metadata == null ? Collections.emptyMap() : metadata);

        synchronized (recentAlerts) {
            recentAlerts.addFirst(event);
            int keep = Math.max(1, appProperties.getMonitor().getAlertRetention());
            while (recentAlerts.size() > keep) {
                recentAlerts.removeLast();
            }
        }

        persistToRedis(event);
        log.warn("alert type={} severity={} message={} meta={}", type, severity, message, event.getMetadata());
    }

    public void emitIfDue(String dedupeKey,
                          long cooldownMs,
                          String type,
                          String severity,
                          String message,
                          Map<String, Object> metadata) {
        long now = System.currentTimeMillis();
        Long last = dedupeState.get(dedupeKey);
        if (last != null && now - last < Math.max(0L, cooldownMs)) {
            return;
        }
        dedupeState.put(dedupeKey, now);
        emit(type, severity, message, metadata);
    }

    public List<AlertEvent> recent(int limit) {
        int safeLimit = Math.max(1, limit);
        List<AlertEvent> out = new ArrayList<>();
        synchronized (recentAlerts) {
            for (AlertEvent event : recentAlerts) {
                if (out.size() >= safeLimit) {
                    break;
                }
                out.add(event);
            }
        }
        if (!out.isEmpty() || redisTemplate == null) {
            return out;
        }

        try {
            List<String> rows = redisTemplate.opsForList().range(REDIS_ALERT_KEY, 0, safeLimit - 1);
            if (rows == null) {
                return out;
            }
            for (String row : rows) {
                if (row == null || row.trim().length() == 0) {
                    continue;
                }
                out.add(objectMapper.readValue(row, new TypeReference<AlertEvent>() {
                }));
            }
        } catch (Exception ignored) {
            return out;
        }
        return out;
    }

    private void persistToRedis(AlertEvent event) {
        if (redisTemplate == null) {
            return;
        }
        try {
            String raw = objectMapper.writeValueAsString(event);
            redisTemplate.opsForList().leftPush(REDIS_ALERT_KEY, raw);
            int keep = Math.max(1, appProperties.getMonitor().getAlertRetention());
            redisTemplate.opsForList().trim(REDIS_ALERT_KEY, 0, keep - 1);
        } catch (Exception ignored) {
            // no-op
        }
    }
}
