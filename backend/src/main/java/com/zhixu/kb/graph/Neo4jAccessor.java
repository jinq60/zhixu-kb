package com.zhixu.kb.graph;

import com.zhixu.kb.config.Neo4jProperties;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Neo4j 访问封装：所有操作带可用性保护，Neo4j 不可用时统一返回降级结果而不抛异常。
 */
@Slf4j
@Component
public class Neo4jAccessor {

    private final ObjectProvider<Driver> driverProvider;
    private final Neo4jProperties properties;

    public Neo4jAccessor(ObjectProvider<Driver> driverProvider, Neo4jProperties properties) {
        this.driverProvider = driverProvider;
        this.properties = properties;
    }

    public boolean isAvailable() {
        if (!Boolean.TRUE.equals(properties.getEnabled())) {
            return false;
        }
        return driverProvider.getIfAvailable() != null;
    }

    public <T> T write(String cypher, java.util.Map<String, Object> params, ResultMapper<T> mapper) {
        Driver driver = driverProvider.getIfAvailable();
        if (driver == null) {
            return null;
        }
        try (Session session = driver.session()) {
            return session.writeTransaction(tx -> mapper.map(tx, cypher, params));
        } catch (Exception ex) {
            // 保留堆栈：Neo4j 宕机/超时与"真的没有图谱数据"必须可区分，否则排障困难
            log.warn("Neo4j write failed: {}", ex.getMessage(), ex);
            return null;
        }
    }

    public <T> T read(String cypher, java.util.Map<String, Object> params, ResultMapper<T> mapper) {
        Driver driver = driverProvider.getIfAvailable();
        if (driver == null) {
            return null;
        }
        try (Session session = driver.session()) {
            return session.readTransaction(tx -> mapper.map(tx, cypher, params));
        } catch (Exception ex) {
            log.warn("Neo4j read failed: {}", ex.getMessage(), ex);
            return null;
        }
    }

    @FunctionalInterface
    public interface ResultMapper<T> {
        T map(Transaction tx, String cypher, java.util.Map<String, Object> params);
    }
}
