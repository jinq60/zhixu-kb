package com.zhixu.kb.admin.status;

import com.zhixu.kb.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理端运行状态：主机指标 + 中间件探活 + Docker 容器状态。
 */
@RestController
@RequestMapping("/api/v1/admin/system")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class SystemStatusController {

    private final SystemStatusService systemStatusService;

    @GetMapping("/runtime")
    public Result<Map<String, Object>> runtime() {
        Map<String, Object> data = new HashMap<>();
        data.put("host", systemStatusService.hostMetrics());
        data.put("middleware", systemStatusService.middlewareStatus());
        data.put("containers", systemStatusService.containerStatus());
        data.put("timestamp", System.currentTimeMillis());
        return Result.success(data);
    }

    /** 容器日志下钻：中间件/容器行点击查看（tail 上限 1000 行） */
    @GetMapping("/containers/{name}/logs")
    public Result<Map<String, Object>> containerLogs(
            @org.springframework.web.bind.annotation.PathVariable("name") String name,
            @org.springframework.web.bind.annotation.RequestParam(value = "tail", defaultValue = "300") Integer tail) {
        // 容器名白名单字符，防止路径拼接滥用
        if (name == null || !name.matches("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")) {
            return Result.error(400, "非法容器名");
        }
        String logs = systemStatusService.containerLogs(name, tail == null ? 300 : tail);
        Map<String, Object> data = new HashMap<>();
        data.put("container", name);
        data.put("logs", logs);
        return Result.success(data);
    }
}
