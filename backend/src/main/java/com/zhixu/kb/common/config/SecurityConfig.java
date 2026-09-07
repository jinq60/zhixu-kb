package com.zhixu.kb.common.config;

import com.zhixu.kb.security.JwtAuthenticationFilter;
import com.zhixu.kb.security.RateLimitFilter;
import com.zhixu.kb.security.RequestTraceFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

@Configuration
@RequiredArgsConstructor
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RequestTraceFilter requestTraceFilter;

    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/login/unified",
            "/api/auth/register",
            "/api/auth/email-code/send",
            "/api/auth/email-code/login",
            "/api/auth/sms-code/send",
            "/api/auth/sms-code/login",
            "/api/auth/oauth/**",
            "/login/oauth2/code/**",
            "/api/public/**",
            "/api/health",
            "/api/v1/health",
            // P0-4 修复：Swagger/Actuator 不再默认匿名，仅健康探针公开
            "/actuator/health",
            "/actuator/info"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"code\":401,\"message\":\"未登录或token已过期\"}");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // 文件内容接口匿名放行，Service 层（findReadableFile）仍强制：
                        // 仅自己笔记或已发布笔记的附件可读，禁止匿名遍历自增文件 ID。
                        .requestMatchers(HttpMethod.GET, "/api/files/*/content").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").authenticated()
                        // P0-4 修复：Swagger/Actuator 默认需认证（不限 profile，原先 permitAll 首匹配导致 prod 分支死代码）
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**")
                        .authenticated()
                        .anyRequest().authenticated());

        http.addFilterBefore(requestTraceFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(jwtAuthenticationFilter, RequestTraceFilter.class);
        http.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 三个自定义过滤器为 @Component，Boot 会默认把它们注册到 servlet 链（位于 FilterChainProxy 之后）。
     * 这里显式禁用自动注册，仅保留 Security 链中的单次注册，避免未来改造时过滤器被执行两次。
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration(JwtAuthenticationFilter filter) {
        return disabledRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(RateLimitFilter filter) {
        return disabledRegistration(filter);
    }

    @Bean
    public FilterRegistrationBean<RequestTraceFilter> requestTraceFilterRegistration(RequestTraceFilter filter) {
        return disabledRegistration(filter);
    }

    private <T extends jakarta.servlet.Filter> FilterRegistrationBean<T> disabledRegistration(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // cost 12：默认 10 在单机约几十毫秒，可被高频撞库放大成 CPU 消耗面；
        // 提升至 12（约 4 倍耗时）配合登录失败限流，抬高爆破成本
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
