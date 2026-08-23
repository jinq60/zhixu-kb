package com.zhixu.kb.common.utils;

import com.zhixu.kb.common.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;

import static org.junit.jupiter.api.Assertions.*;

class SafeUrlValidatorTest {

    @BeforeEach
    void setUp() {
        // 用固定解析器替代真实 DNS，保证测试确定性且不依赖外部网络
        SafeUrlValidator.setHostResolver(host -> new InetAddress[]{InetAddress.getByName("8.8.8.8")});
    }

    @AfterEach
    void tearDown() {
        SafeUrlValidator.setHostResolver(null);
    }

    @Test
    void validate_httpsPublicUrl_shouldPass() {
        assertDoesNotThrow(() -> SafeUrlValidator.validateOrThrow("https://api.deepseek.com"));
    }

    @Test
    void validate_domainResolvingToPrivateIp_shouldThrow() {
        SafeUrlValidator.setHostResolver(host -> new InetAddress[]{InetAddress.getByName("192.168.1.1")});
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("https://evil.example.com"));
    }

    @Test
    void validate_ftpScheme_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("ftp://example.com"));
    }

    @Test
    void validate_fileScheme_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("file:///etc/passwd"));
    }

    @Test
    void validate_missingHost_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("http://"));
    }

    @Test
    void validate_localhost_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("http://localhost:8080"));
    }

    @Test
    void validate_loopbackIp_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("http://127.0.0.1:6379"));
    }

    @Test
    void validate_privateNetworkIp_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("http://192.168.1.1"));
    }

    @Test
    void validate_tenRangeIp_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("http://10.0.0.1"));
    }

    @Test
    void validate_linkLocalIp_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("http://169.254.169.254"));
    }

    @Test
    void validate_internalDomain_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("http://service.internal"));
    }

    @Test
    void validate_malformedUrl_shouldThrow() {
        assertThrows(BusinessException.class,
                () -> SafeUrlValidator.validateOrThrow("not a url at all"));
    }
}
