package com.zhixu.kb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Neo4j 知识图谱连接配置。
 */
@ConfigurationProperties(prefix = "neo4j")
public class Neo4jProperties {

    private String uri = "bolt://localhost:7687";
    private String username = "neo4j";
    private String password = "neo4j";
    private Boolean enabled = true;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }
}
