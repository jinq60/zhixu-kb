package com.zhixu.kb.common.result;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void success_withData_shouldReturnCode200() {
        Result<String> result = Result.success("hello");
        assertEquals(200, result.getCode());
        assertEquals("hello", result.getData());
        assertNotNull(result.getTimestamp());
    }

    @Test
    void success_withNullData_shouldReturnCode200() {
        Result<Void> result = Result.success(null);
        assertEquals(200, result.getCode());
        assertNull(result.getData());
    }

    @Test
    void success_withMessage_shouldSetMessage() {
        Result<String> result = Result.success("custom message", "data");
        assertEquals("custom message", result.getMessage());
        assertEquals("data", result.getData());
    }

    @Test
    void error_withCodeAndMessage_shouldReturnError() {
        Result<Object> result = Result.error(500, "Internal Server Error");
        assertEquals(500, result.getCode());
        assertEquals("Internal Server Error", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void error_withResultCode_shouldReturnCorrectCode() {
        Result<Object> result = Result.error(ResultCode.BAD_REQUEST, "Invalid input");
        assertEquals(400, result.getCode());
        assertEquals("Invalid input", result.getMessage());
    }

    @Test
    void timestamp_shouldBeCloseToCurrentTime() {
        long before = System.currentTimeMillis();
        Result<String> result = Result.success("test");
        long after = System.currentTimeMillis();
        assertTrue(result.getTimestamp() >= before && result.getTimestamp() <= after);
    }
}
