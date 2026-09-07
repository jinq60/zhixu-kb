package com.zhixu.kb;

import com.zhixu.kb.config.AiProperties;
import com.zhixu.kb.config.AppProperties;
import com.zhixu.kb.config.MilvusProperties;
import com.zhixu.kb.config.Neo4jProperties;
import com.zhixu.kb.config.OAuthProperties;
import com.zhixu.kb.note.config.FileStorageProperties;
import com.zhixu.kb.note.config.OCRClientProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// Neo4j 健康探针自动配置已排除：neo4j.enabled=false 时驱动 Bean 为 null，
// Boot 的 Reactive 健康贡献器遇到空驱动直接抛错导致启动失败（README 承诺可关闭）。
// 图谱可用性由业务层（Neo4jAccessor 降级 + 管理端总览）保障，不依赖 actuator 探针。
@SpringBootApplication(exclude = {
        org.springframework.boot.actuate.autoconfigure.neo4j.Neo4jHealthContributorAutoConfiguration.class
})
@MapperScan("com.zhixu.kb.**.mapper")
@EnableConfigurationProperties({
        FileStorageProperties.class,
        OCRClientProperties.class,
        AiProperties.class,
        AppProperties.class,
        Neo4jProperties.class,
        OAuthProperties.class,
        MilvusProperties.class
})
public class ZhixuApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZhixuApplication.class, args);
    }

}
