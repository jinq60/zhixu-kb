package com.zhixu.kb.common;

import com.zhixu.kb.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/recent")
    public Result<List<AlertEvent>> recent(@RequestParam(value = "limit", defaultValue = "20") Integer limit) {
        return Result.success(alertService.recent(limit));
    }

    @PostMapping("/emit-test")
    public Result<Boolean> emitTest(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> metadata = body == null ? Collections.emptyMap() : body;
        alertService.emit("TEST_ALERT", "INFO", "manual test alert", metadata);
        return Result.success("测试告警已写入", Boolean.TRUE);
    }
}
