package com.zhixu.kb.system.service;

import com.zhixu.kb.common.utils.ClientIpResolver;
import com.zhixu.kb.system.entity.OperationLog;
import com.zhixu.kb.system.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 敏感操作审计日志：登录成功/失败、注册、登出、角色变更、密码修改、邮箱绑定。
 * 写入失败不影响主流程；审计记录不包含密码/验证码等敏感参数。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final OperationLogMapper operationLogMapper;

    public void record(Long userId, String operationType, String operationDesc, String requestUrl) {
        try {
            OperationLog entry = new OperationLog();
            entry.setUserId(userId);
            entry.setOperationType(operationType);
            entry.setOperationDesc(operationDesc);
            entry.setRequestMethod("SYSTEM");
            entry.setRequestUrl(requestUrl);
            entry.setIpAddress(resolveClientIp());
            operationLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("Audit log write failed: type={}", operationType, e);
        }
    }

    private String resolveClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes) {
            return ClientIpResolver.resolve(
                    ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest());
        }
        return "unknown";
    }
}
