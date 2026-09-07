package com.zhixu.kb.admin.status;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ListContainersCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.sql.DataSource;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 运行状态采集：主机指标（CPU/内存/磁盘/JVM）+ 中间件探活（并行、短超时）
 * + Docker 容器状态（经 docker socket，10s 缓存）。
 * 供管理后台「运行状态」页轮询。
 */
@Slf4j
@Service
public class SystemStatusService {

    private final DataSource dataSource;
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;
    private final ObjectProvider<org.neo4j.driver.Driver> neo4jDriverProvider;
    private final boolean neo4jEnabled;
    private final boolean milvusEnabled;
    private final String ocrUrl;
    private final String pdfUrl;
    private final String dockerHost;

    private final RestTemplate probeRestTemplate = createProbeRestTemplate();
    private final DockerClient dockerClient;
    private final Object dockerLock = new Object();
    private volatile long containersCachedAt = 0;
    private volatile List<Map<String, Object>> containersCache = null;
    private static final long CONTAINER_CACHE_MS = 10_000L;

    public SystemStatusService(
            DataSource dataSource,
            ObjectProvider<StringRedisTemplate> redisTemplateProvider,
            ObjectProvider<org.neo4j.driver.Driver> neo4jDriverProvider,
            @Value("${neo4j.enabled:true}") boolean neo4jEnabled,
            @Value("${milvus.enabled:true}") boolean milvusEnabled,
            @Value("${ocr.service.url:http://localhost:5001}") String ocrUrl,
            @Value("${PDF_PARSE_SERVICE_URL:}") String pdfUrl,
            @Value("${DOCKER_HOST:unix:///var/run/docker.sock}") String dockerHost) {
        this.dataSource = dataSource;
        this.redisTemplateProvider = redisTemplateProvider;
        this.neo4jDriverProvider = neo4jDriverProvider;
        this.neo4jEnabled = neo4jEnabled;
        this.milvusEnabled = milvusEnabled;
        this.ocrUrl = ocrUrl;
        this.pdfUrl = pdfUrl;
        this.dockerHost = dockerHost;
        this.dockerClient = initDockerClient(dockerHost);
    }

    private DockerClient initDockerClient(String host) {
        try {
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                    .withDockerHost(host)
                    .build();
            ZerodepDockerHttpClient httpClient = new ZerodepDockerHttpClient.Builder()
                    .dockerHost(URI.create(host))
                    .build();
            DockerClient client = DockerClientImpl.getInstance(config, httpClient);
            // 启动时探活：socket 不存在则置空（容器外本地运行场景）
            client.infoCmd().exec();
            log.info("Docker status collector initialized: {}", host);
            return client;
        } catch (Throwable ex) {
            log.warn("Docker status collector unavailable ({}): {}", host, ex.getMessage());
            return null;
        }
    }

    private static RestTemplate createProbeRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2500);
        return new RestTemplate(factory);
    }

    // ---------------- 主机指标 ----------------

    public Map<String, Object> hostMetrics() {
        Map<String, Object> host = new HashMap<>();
        com.sun.management.OperatingSystemMXBean os =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        double systemCpu = os.getSystemCpuLoad();
        double processCpu = os.getProcessCpuLoad();
        host.put("systemCpuPercent", systemCpu >= 0 ? round1(systemCpu * 100) : null);
        host.put("processCpuPercent", processCpu >= 0 ? round1(processCpu * 100) : null);
        long sysMemTotal = os.getTotalPhysicalMemorySize();
        long sysMemFree = os.getFreePhysicalMemorySize();
        host.put("memTotalBytes", sysMemTotal);
        host.put("memUsedBytes", Math.max(0, sysMemTotal - sysMemFree));
        long heapMax = Runtime.getRuntime().maxMemory();
        long heapUsed = Math.max(0, Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        host.put("heapMaxBytes", heapMax);
        host.put("heapUsedBytes", heapUsed);
        File disk = new File("/");
        host.put("diskTotalBytes", disk.getTotalSpace());
        host.put("diskUsableBytes", disk.getUsableSpace());
        host.put("availableProcessors", os.getAvailableProcessors());
        host.put("systemLoadAverage", os.getSystemLoadAverage());
        return host;
    }

    // ---------------- 中间件探活 ----------------

    public List<Map<String, Object>> middlewareStatus() {
        List<CompletableFuture<Map<String, Object>>> futures = new ArrayList<>();
        futures.add(CompletableFuture.supplyAsync(this::probeMysql));
        futures.add(CompletableFuture.supplyAsync(this::probeRedis));
        futures.add(CompletableFuture.supplyAsync(this::probeNeo4j));
        futures.add(CompletableFuture.supplyAsync(this::probeMilvus));
        futures.add(CompletableFuture.supplyAsync(() -> probeHttp("OCR 服务", "zhixu-ocr", ocrUrl + "/ocr/health")));
        futures.add(CompletableFuture.supplyAsync(() -> probeHttp("PDF 解析服务", "zhixu-pdf",
                hasText(pdfUrl) ? pdfUrl + "/health" : null)));
        List<Map<String, Object>> result = new ArrayList<>();
        for (CompletableFuture<Map<String, Object>> future : futures) {
            try {
                result.add(future.get(6, TimeUnit.SECONDS));
            } catch (Exception ex) {
                Map<String, Object> unknown = new HashMap<>();
                unknown.put("name", "探活任务");
                unknown.put("status", "DOWN");
                unknown.put("detail", "探活超时");
                result.add(unknown);
            }
        }
        return result;
    }

    private Map<String, Object> probeMysql() {
        long start = System.currentTimeMillis();
        Map<String, Object> status = new HashMap<>();
        status.put("name", "MySQL");
        status.put("container", "zhixu-mysql");
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            rs.next();
            status.put("status", "UP");
        } catch (Exception ex) {
            status.put("status", "DOWN");
            status.put("detail", ex.getMessage());
        }
        status.put("latencyMs", System.currentTimeMillis() - start);
        return status;
    }

    private Map<String, Object> probeRedis() {
        long start = System.currentTimeMillis();
        Map<String, Object> status = new HashMap<>();
        status.put("name", "Redis");
        status.put("container", "zhixu-redis");
        StringRedisTemplate template = redisTemplateProvider.getIfAvailable();
        if (template == null) {
            status.put("status", "DISABLED");
            status.put("detail", "未配置（本地内存降级模式）");
            return status;
        }
        try {
            String pong = template.getConnectionFactory().getConnection().ping();
            status.put("status", "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN");
        } catch (Exception ex) {
            status.put("status", "DOWN");
            status.put("detail", ex.getMessage());
        }
        status.put("latencyMs", System.currentTimeMillis() - start);
        return status;
    }

    private Map<String, Object> probeNeo4j() {
        long start = System.currentTimeMillis();
        Map<String, Object> status = new HashMap<>();
        status.put("name", "Neo4j 图谱");
        status.put("container", "zhixu-neo4j");
        if (!neo4jEnabled) {
            status.put("status", "DISABLED");
            status.put("detail", "neo4j.enabled=false");
            return status;
        }
        org.neo4j.driver.Driver driver = neo4jDriverProvider.getIfAvailable();
        if (driver == null) {
            status.put("status", "DISABLED");
            status.put("detail", "驱动未初始化");
            return status;
        }
        try (org.neo4j.driver.Session session = driver.session()) {
            session.run("RETURN 1").consume();
            status.put("status", "UP");
        } catch (Exception ex) {
            status.put("status", "DOWN");
            status.put("detail", ex.getMessage());
        }
        status.put("latencyMs", System.currentTimeMillis() - start);
        return status;
    }

    private Map<String, Object> probeMilvus() {
        Map<String, Object> status = new HashMap<>();
        status.put("name", "Milvus 向量库");
        status.put("container", "zhixu-milvus");
        if (!milvusEnabled) {
            status.put("status", "DISABLED");
            status.put("detail", "milvus.enabled=false（关键词检索模式）");
        } else {
            // 深度探活依赖 SDK 客户端生命周期，此处仅报告启用态（实际可用性由向量检索结果兜底）
            status.put("status", "UP");
            status.put("detail", "已启用");
        }
        return status;
    }

    private Map<String, Object> probeHttp(String name, String container, String url) {
        long start = System.currentTimeMillis();
        Map<String, Object> status = new HashMap<>();
        status.put("name", name);
        status.put("container", container);
        if (!hasText(url)) {
            status.put("status", "DISABLED");
            status.put("detail", "未配置");
            return status;
        }
        try {
            probeRestTemplate.getForEntity(URI.create(url), String.class);
            status.put("status", "UP");
        } catch (Exception ex) {
            status.put("status", "DOWN");
            status.put("detail", rootMessage(ex));
        }
        status.put("latencyMs", System.currentTimeMillis() - start);
        return status;
    }

    // ---------------- Docker 容器状态 ----------------

    public Map<String, Object> containerStatus() {
        Map<String, Object> data = new ConcurrentHashMap<>();
        if (dockerClient == null) {
            data.put("available", false);
            data.put("note", "Docker 容器采集未启用（backend 未挂载 docker.sock，属预期安全降级；主机/中间件探活不受影响。如需容器列表请部署 socket-proxy 白名单）");
            return data;
        }
        long now = System.currentTimeMillis();
        List<Map<String, Object>> cached = containersCache;
        if (cached != null && now - containersCachedAt < CONTAINER_CACHE_MS) {
            data.put("available", true);
            data.put("containers", cached);
            return data;
        }
        synchronized (dockerLock) {
            cached = containersCache;
            if (cached != null && System.currentTimeMillis() - containersCachedAt < CONTAINER_CACHE_MS) {
                data.put("available", true);
                data.put("containers", cached);
                return data;
            }
            List<Map<String, Object>> list = new ArrayList<>();
            ListContainersCmd cmd = dockerClient.listContainersCmd().withShowAll(true);
            for (Container container : cmd.exec()) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", container.getNames() != null && container.getNames().length > 0
                        ? container.getNames()[0].replaceFirst("^/", "") : container.getId());
                item.put("state", container.getState());
                item.put("status", container.getStatus());
                item.put("image", container.getImage());
                list.add(item);
            }
            list.sort((a, b) -> String.valueOf(a.get("name")).compareTo(String.valueOf(b.get("name"))));
            containersCache = list;
            containersCachedAt = System.currentTimeMillis();
            data.put("available", true);
            data.put("containers", list);
            return data;
        }
    }

    // ---------------- 容器日志（docker.sock 拉取） ----------------

    /** 拉取指定容器最近 N 行日志（stdout+stderr，带 docker 时间戳前缀） */
    public String containerLogs(String container, int tail) {
        if (dockerClient == null) {
            throw new IllegalStateException("Docker Socket 不可用，无法读取容器日志");
        }
        int safeTail = Math.max(1, Math.min(1000, tail));
        StringBuilder sb = new StringBuilder();
        java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
        dockerClient.logContainerCmd(container)
                .withStdOut(true)
                .withStdErr(true)
                .withTimestamps(true)
                .withTail(safeTail)
                .exec(new com.github.dockerjava.core.command.LogContainerResultCallback() {
                    @Override
                    public void onNext(com.github.dockerjava.api.model.Frame frame) {
                        sb.append(new String(frame.getPayload(), java.nio.charset.StandardCharsets.UTF_8));
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return sb.toString();
    }

    // ---------------- 工具 ----------------

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static String rootMessage(Throwable ex) {
        Throwable cursor = ex;
        while (cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null ? cursor.getClass().getSimpleName() : message;
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
