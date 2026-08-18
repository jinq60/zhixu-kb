package com.zhixu.kb.ai;

import com.zhixu.kb.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class AIEngineRuntimeSwitchService {

    private final AtomicReference<String> overrideEngineType = new AtomicReference<String>();

    public String overrideEngineType() {
        return overrideEngineType.get();
    }

    public boolean hasOverride() {
        return StringUtils.hasText(overrideEngineType.get());
    }

    public String setOverrideEngineType(String engineType) {
        String normalized = validateAndNormalize(engineType);
        overrideEngineType.set(normalized);
        return normalized;
    }

    public void clearOverride() {
        overrideEngineType.set(null);
    }

    public String validateAndNormalize(String engineType) {
        String value = engineType == null ? "" : engineType.trim();
        if ("api".equalsIgnoreCase(value)) {
            return "api";
        }
        throw new BusinessException("INVALID_PARAMS", "engineType only supports api");
    }
}
