package com.zhixu.kb.common.exception;

import com.zhixu.kb.common.result.ResultCode;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {
    private final Integer code;

    public BusinessException(ResultCode code, String message) {
        super(message);
        this.code = code.getCode();
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String code, String message) {
        super(message);
        Integer parsed = null;
        try {
            parsed = Integer.valueOf(code);
        } catch (NumberFormatException ignored) {
            // 非数字错误码按 BAD_REQUEST 处理
        }
        this.code = parsed != null ? parsed : ResultCode.BAD_REQUEST.getCode();
    }
}
