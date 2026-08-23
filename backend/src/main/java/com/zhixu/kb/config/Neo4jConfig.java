package com.zhixu.kb.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Neo4j 驱动配置。
 * 驱动创建不做主动连通性校验（懒连接），Neo4j 未启动时首次使用会失败，
 * 由 Neo4jAccessor 统一捕获并降级；neo4j.enabled=false 时不注册驱动 Bean。
 * 显式配置连接超时与连接池，避免 Neo4j 宕机时接口假死（默认获取连接约 60s）。
 */
@Configuration
public class Neo4jConfig {

    private static final Logger log = LoggerFactory.getLogger(Neo4jConfig.class);

    @Bean(destroyMethod = "close")
    public Driver neo4jDriver(Neo4jProperties properties) {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            log.info("Neo4j disabled by config, graph features unavailable");
            return null;
        }
        try {
            Config config = Config.builder()
                    .withConnectionTimeout(3, TimeUnit.SECONDS)
                    .withConnectionAcquisitionTimeout(5, TimeUnit.SECONDS)
                    .withMaxConnectionPoolSize(20)
                    .build();
            Driver driver = GraphDatabase.driver(
                    properties.getUri(),
                    AuthTokens.basic(properties.getUsername(), properties.getPassword()),
                    config
            );
            log.info("Neo4j driver created: {}", properties.getUri());
            return driver;
        } catch (Exception ex) {
            log.warn("Neo4j driver create failed, graph features will degrade: {}", ex.getMessage());
            return null;
        }
    }
}
