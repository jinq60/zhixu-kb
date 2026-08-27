package com.zhixu.kb.common.config;

import com.zhixu.kb.security.JwtAuthenticationFilter;
import com.zhixu.kb.security.RateLimitFilter;
import com.zhixu.kb.security.RequestTraceFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;

@Configuration
@RequiredArgsConstructor
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitFilter rateLimitFilter;
    private final RequestTraceFilter requestTraceFilter;
    private final Environment environment;
    private final ObjectProvider<com.zhixu.kb.system.auth.DesktopAutoLoginFilter> desktopAutoLoginFilterProvider;

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
            "/api/app-config",
            // 设备授权公开端点：exchange 凭一次性码、validate/refresh 凭设备凭证自身鉴权
            "/api/device/exchange",
            "/api/device/validate",
            "/api/device/refresh",
            // 仅存在于桌面版 jar（服务器版无对应 Controller，配置保留无害）
            "/api/auth/desktop-token",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/**"
    };

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .exceptionHandling()
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"未登录或token已过期\"}");
                })
                .and()
                .authorizeRequests()
                .antMatchers(PUBLIC_ENDPOINTS).permitAll()
                .antMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                // 文件内容接口匿名放行，Service 层（findReadableFile）仍强制：
                // 仅自己笔记或已发布笔记的附件可读，禁止匿名遍历自增文件 ID。
                .antMatchers(HttpMethod.GET, "/api/files/*/content").permitAll()
                .antMatchers(HttpMethod.GET, "/api/categories/**").authenticated();

        // 生产环境：Swagger/Actuator 需要登录后才能访问，避免接口信息泄露
        if (isProdProfile()) {
            http.authorizeRequests()
                    .antMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/actuator/**")
                    .authenticated();
        }

        // 桌面版：后端直接托管前端静态资源与 SPA history 路由——
        // 非幂等的 API 读接口先锁 authenticated，其余非 API 的 GET（静态资源/前端路由）放行；
        // /desktop/** 为本地激活端点（仅 127.0.0.1 可达），GET/POST 均放行
        if (isDesktopProfile()) {
            http.authorizeRequests()
                    .antMatchers("/desktop/**").permitAll()
                    .antMatchers(HttpMethod.GET, "/api/**").authenticated()
                    .antMatchers("/", "/index.html", "/favicon.ico", "/assets/**", "/logo/**").permitAll()
                    .antMatchers(HttpMethod.GET,
                            "/{path:[^\\.]*}", "/{p1}/{path2:[^\\.]*}", "/{p1}/{p2}/{path3:[^\\.]*}")
                    .permitAll();
        }

        http.authorizeRequests()
                .anyRequest().authenticated();

        http.addFilterBefore(requestTraceFilter, UsernamePasswordAuthenticationFilter.class);
        http.addFilterAfter(jwtAuthenticationFilter, RequestTraceFilter.class);
        http.addFilterAfter(rateLimitFilter, JwtAuthenticationFilter.class);
        // 桌面版（desktop profile）：JWT 过滤器之后挂自动登录过滤器（服务器版该 Bean 不存在，跳过）
        com.zhixu.kb.system.auth.DesktopAutoLoginFilter desktopAutoLoginFilter =
                desktopAutoLoginFilterProvider.getIfAvailable();
        if (desktopAutoLoginFilter != null) {
            http.addFilterAfter(desktopAutoLoginFilter, JwtAuthenticationFilter.class);
        }
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

    private <T extends javax.servlet.Filter> FilterRegistrationBean<T> disabledRegistration(T filter) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    private boolean isProdProfile() {
        return environment != null && Arrays.asList(environment.getActiveProfiles()).contains("prod");
    }

    private boolean isDesktopProfile() {
        return environment != null && Arrays.asList(environment.getActiveProfiles()).contains("desktop");
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
