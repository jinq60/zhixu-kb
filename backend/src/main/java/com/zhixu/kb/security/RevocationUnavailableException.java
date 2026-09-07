package com.zhixu.kb.security;

/**
 * 撤销存储不可用异常：严格模式下 Redis 故障时抛出，
 * 由认证过滤器转为 503（而非伪装成 401 全员掉线）。
 */
public class RevocationUnavailableException extends RuntimeException {

    public RevocationUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
