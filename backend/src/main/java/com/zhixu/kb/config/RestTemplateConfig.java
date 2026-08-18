package com.zhixu.kb.config;

import com.zhixu.kb.common.config.NonRedirectingSimpleClientHttpRequestFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置：统一调用实例（长读超时，兼容非流式大模型推理）。
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        NonRedirectingSimpleClientHttpRequestFactory factory = new NonRedirectingSimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        // 本地 CPU 大模型非流式推理耗时较长（可达数分钟），读取超时需放宽
        factory.setReadTimeout(300000);
        return new RestTemplate(factory);
    }
}
