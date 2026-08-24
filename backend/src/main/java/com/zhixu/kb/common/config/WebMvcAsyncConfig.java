package com.zhixu.kb.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 异步支持配置。
 * <p>
 * StreamingResponseBody（SSE 流式问答）默认运行在 SimpleAsyncTaskExecutor 上——
 * 每个请求新建一个线程且无上限；配合 request-timeout=-1，恶意/异常客户端并发
 * 打开大量流式连接会导致线程数失控。这里替换为有界线程池：
 * 核心线程按 CPU 自适应、队列有限、超出即拒绝（由调用方降级提示），保证服务自保。
 */
@Configuration
public class WebMvcAsyncConfig implements WebMvcConfigurer {

    @Bean("mvcAsyncExecutor")
    public ThreadPoolTaskExecutor mvcAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        int cores = Math.max(2, Runtime.getRuntime().availableProcessors());
        executor.setCorePoolSize(cores);
        executor.setMaxPoolSize(cores * 4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("mvc-async-");
        // 有界拒绝：SSE 建立后由该池执行业务，池满说明系统过载，快速失败优于无限扩张
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcAsyncExecutor());
    }
}
