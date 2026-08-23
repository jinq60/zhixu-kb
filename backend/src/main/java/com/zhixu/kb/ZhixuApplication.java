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

@SpringBootApplication
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
