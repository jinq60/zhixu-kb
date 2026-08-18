package com.zhixu.kb.system.auth;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 认证适配器注册中心，按 method 分发到具体 Provider。
 */
@Component
public class AuthProviderRegistry {

    private final Map<String, AuthProvider> providers;

    public AuthProviderRegistry(Collection<AuthProvider> providers) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(AuthProvider::method, Function.identity(),
                        (a, b) -> {
                            throw new IllegalStateException("重复的认证方式: " + a.method());
                        }));
    }

    public AuthProvider get(String method) {
        AuthProvider provider = providers.get(method);
        if (provider == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的认证方式: " + method);
        }
        return provider;
    }

    public List<String> availableMethods() {
        return new ArrayList<>(providers.keySet());
    }
}
