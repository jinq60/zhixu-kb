package com.zhixu.kb.system.auth;

import com.zhixu.kb.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.Cookie;

import static org.junit.jupiter.api.Assertions.*;

class AuthCookieServiceTest {

    private AuthCookieService cookieService;

    @BeforeEach
    void setUp() {
        AppProperties props = new AppProperties();
        props.getAuth().setCookieName("ZHIXU_SESSION");
        props.getAuth().setCookieSecure(false);
        props.getAuth().setCookieSameSite("Lax");
        cookieService = new AuthCookieService(props);
    }

    @Test
    void addSessionCookie_shouldSetHttpOnlyLaxCookie() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookieService.addSessionCookie(response, "eyJhbGciOiJIUzI1NiJ9.payload.sig");

        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.startsWith("ZHIXU_SESSION=eyJhbGciOiJIUzI1NiJ9.payload.sig"));
        assertTrue(header.contains("Path=/"));
        assertTrue(header.contains("HttpOnly"));
        assertTrue(header.contains("SameSite=Lax"));
    }

    @Test
    void addSessionCookie_shouldRejectHeaderInjection() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertThrows(IllegalArgumentException.class,
                () -> cookieService.addSessionCookie(response, "abc; Path=/; Domain=evil"));
        assertThrows(IllegalArgumentException.class,
                () -> cookieService.addSessionCookie(response, "abc def"));
    }

    @Test
    void resolveToken_shouldPreferCookieOverHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(new Cookie("ZHIXU_SESSION", "cookie-token"));
        request.addHeader("Authorization", "Bearer header-token");

        assertEquals("cookie-token", cookieService.resolveToken(request));
    }

    @Test
    void resolveToken_shouldFallBackToHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer header-token");

        assertEquals("header-token", cookieService.resolveToken(request));
    }

    @Test
    void resolveToken_shouldReturnNullWhenAbsent() {
        assertNull(cookieService.resolveToken(new MockHttpServletRequest()));
        assertNull(cookieService.resolveToken(null));
    }

    @Test
    void clearSessionCookie_shouldExpireImmediately() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        cookieService.clearSessionCookie(response);

        String header = response.getHeader("Set-Cookie");
        assertNotNull(header);
        assertTrue(header.contains("Max-Age=0"));
        assertTrue(header.contains("HttpOnly"));
    }
}
