package com.zhixu.kb.ai.controller;

import com.zhixu.kb.ai.model.AiEndpointSaveRequest;
import com.zhixu.kb.ai.model.AiEndpointView;
import com.zhixu.kb.ai.service.AiEndpointService;
import com.zhixu.kb.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI 端点管理接口（管理员）：数据库维护多厂商端点，动态生效。
 */
@RestController
@RequestMapping("/api/v1/admin/ai/endpoints")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AiEndpointController {

    private final AiEndpointService endpointService;

    @GetMapping
    public Result<List<AiEndpointView>> list() {
        return Result.success(endpointService.list());
    }

    @PostMapping
    public Result<AiEndpointView> create(@RequestBody AiEndpointSaveRequest request) {
        return Result.success("端点已创建", endpointService.save(request));
    }

    @PutMapping("/{id}")
    public Result<AiEndpointView> update(@PathVariable Long id, @RequestBody AiEndpointSaveRequest request) {
        request.setId(id);
        return Result.success("端点已更新", endpointService.save(request));
    }

    @DeleteMapping("/{id}")
    public Result<Boolean> delete(@PathVariable Long id) {
        endpointService.delete(id);
        return Result.success("端点已删除", Boolean.TRUE);
    }

    @PostMapping("/{id}/toggle")
    public Result<AiEndpointView> toggle(@PathVariable Long id,
                                         @RequestParam("enabled") Boolean enabled) {
        return Result.success(enabled ? "端点已启用" : "端点已停用", endpointService.toggle(id, enabled));
    }

    @PostMapping("/{id}/test")
    public Result<Map<String, Object>> test(@PathVariable Long id) {
        return Result.success(endpointService.test(id));
    }
}
