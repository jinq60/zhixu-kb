package com.zhixu.kb.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CORS 配置：生产环境必须显式枚举可信域名，禁止通配符与 credentials 共用。
 */
@Slf4j
@Configuration
public class CorsConfig {

    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origins = parseOrigins();
        if (origins.isEmpty()) {
            // 未配置时仅允许本机访问（安全默认），生产环境必须显式配置
            origins = Collections.singletonList("http://localhost:5173");
        }
        config.setAllowedOriginPatterns(origins);
        config.setAllowCredentials(true);
        config.addAllowedHeader("*");
        config.addAllowedMethod("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    private List<String> parseOrigins() {
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .peek(this::rejectWildcard)
                .collect(Collectors.toList());
    }

    private void rejectWildcard(String origin) {
        if ("*".equals(origin) || origin.contains("*")) {
            throw new IllegalArgumentException(
                    "CORS origin cannot be wildcard '*' or contain '*'. " +
                            "Explicitly enumerate trusted origins and do not use credentials with wildcard.");
        }
    }
}
