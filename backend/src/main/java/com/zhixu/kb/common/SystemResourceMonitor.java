package com.zhixu.kb.common;

import com.zhixu.kb.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;

/**
 * 系统资源监控：周期检查 CPU / 堆内存使用率，超阈值写入告警。
 */
@Component
@EnableScheduling
@RequiredArgsConstructor
public class SystemResourceMonitor {

    private final AppProperties appProperties;
    private final AlertService alertService;

    @Scheduled(
            fixedDelayString = "${app.monitor.resource-check-interval-ms:30000}",
            initialDelay = 15000
    )
    public void monitorResources() {
        if (!Boolean.TRUE.equals(appProperties.getMonitor().getEnabled())) {
            return;
        }
        evaluateAndAlert(readProcessCpuUsage(), readHeapUsage());
    }

    void evaluateAndAlert(double cpuUsage, double heapUsage) {
        double threshold = Math.max(0, appProperties.getMonitor().getResourceThresholdPercent()) / 100.0;
        long cooldown = Math.max(0L, appProperties.getMonitor().getAlertCooldownMs());

        if (cpuUsage >= 0 && cpuUsage >= threshold) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("cpuUsage", round2(cpuUsage * 100));
            meta.put("threshold", appProperties.getMonitor().getResourceThresholdPercent());
            alertService.emitIfDue(
                    "resource-cpu",
                    cooldown,
                    "SYSTEM_RESOURCE",
                    "WARN",
                    "CPU 使用率超过阈值",
                    meta
            );
        }

        if (heapUsage >= 0 && heapUsage >= threshold) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("heapUsage", round2(heapUsage * 100));
            meta.put("threshold", appProperties.getMonitor().getResourceThresholdPercent());
            alertService.emitIfDue(
                    "resource-heap",
                    cooldown,
                    "SYSTEM_RESOURCE",
                    "WARN",
                    "JVM 堆内存使用率超过阈值",
                    meta
            );
        }
    }

    private double readProcessCpuUsage() {
        try {
            java.lang.management.OperatingSystemMXBean mxBean = ManagementFactory.getOperatingSystemMXBean();
            if (mxBean instanceof com.sun.management.OperatingSystemMXBean) {
                double load = ((com.sun.management.OperatingSystemMXBean) mxBean).getProcessCpuLoad();
                if (load >= 0) {
                    return load;
                }
            }
        } catch (Exception ignored) {
            return -1D;
        }
        return -1D;
    }

    private double readHeapUsage() {
        long max = Runtime.getRuntime().maxMemory();
        if (max <= 0L) {
            return -1D;
        }
        long total = Runtime.getRuntime().totalMemory();
        long free = Runtime.getRuntime().freeMemory();
        long used = Math.max(0L, total - free);
        return (double) used / (double) max;
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
