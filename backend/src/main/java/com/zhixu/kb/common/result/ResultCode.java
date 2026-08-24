package com.zhixu.kb.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ResultCode {
    SUCCESS(200, "success"),
    BAD_REQUEST(400, "bad request"),
    UNAUTHORIZED(401, "unauthorized"),
    FORBIDDEN(403, "forbidden"),
    NOT_FOUND(404, "not found"),
    PAYLOAD_TOO_LARGE(413, "payload too large"),
    TOO_MANY_REQUESTS(429, "too many requests"),
    SERVER_ERROR(500, "server error"),
    SERVICE_UNAVAILABLE(503, "service unavailable");

    private final Integer code;
    private final String message;
}
