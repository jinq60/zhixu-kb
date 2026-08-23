package com.zhixu.kb.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 向量数据库配置。
 */
@ConfigurationProperties(prefix = "milvus")
public class MilvusProperties {

    private boolean enabled = true;
    private String uri = "http://localhost:19530";
    private String username;
    private String password;
    /** 向量维度（text-embedding-3-small = 1536） */
    private int dimension = 1536;
    /** 集合名（v2：显式主键 upsert 幂等；v1 为 autoID 已弃用） */
    private String collectionName = "note_embedding_v2";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

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

    public int getDimension() {
        return dimension;
    }

    public void setDimension(int dimension) {
        this.dimension = dimension;
    }

    public String getCollectionName() {
        return collectionName;
    }

    public void setCollectionName(String collectionName) {
        this.collectionName = collectionName;
    }
}