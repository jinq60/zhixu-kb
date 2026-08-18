package com.zhixu.kb.common.utils;

import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsTest {

    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        jwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(jwtUtils, "secret", "bG5vLXNoYXJlZC1zZWNyZXQta2V5LWxvbmctZW5vdWdo");
        ReflectionTestUtils.setField(jwtUtils, "expiration", 86400000L);
    }

    private UserDetails createUser(String username) {
        return new User(username, "password", Collections.emptyList());
    }

    @Test
    void generateToken_shouldReturnNonNullToken() {
        UserDetails user = createUser("testuser");
        String token = jwtUtils.generateToken(user);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void extractUsername_shouldReturnCorrectUsername() {
        UserDetails user = createUser("admin");
        String token = jwtUtils.generateToken(user);
        assertEquals("admin", jwtUtils.extractUsername(token));
    }

    @Test
    void isTokenValid_shouldReturnTrueForValidToken() {
        UserDetails user = createUser("testuser");
        String token = jwtUtils.generateToken(user);
        assertTrue(jwtUtils.isTokenValid(token, user));
    }

    @Test
    void isTokenValid_shouldReturnFalseForDifferentUser() {
        UserDetails user1 = createUser("user1");
        UserDetails user2 = createUser("user2");
        String token = jwtUtils.generateToken(user1);
        assertFalse(jwtUtils.isTokenValid(token, user2));
    }

    @Test
    void isTokenValid_shouldReturnFalseForExpiredToken() {
        JwtUtils expiredJwtUtils = new JwtUtils();
        ReflectionTestUtils.setField(expiredJwtUtils, "secret", "bG5vLXNoYXJlZC1zZWNyZXQta2V5LWxvbmctZW5vdWdo");
        ReflectionTestUtils.setField(expiredJwtUtils, "expiration", -1000L); // already expired

        UserDetails user = createUser("testuser");
        String token = expiredJwtUtils.generateToken(user);

        assertThrows(ExpiredJwtException.class, () -> {
            expiredJwtUtils.isTokenValid(token, user);
        });
    }

    @Test
    void extractExpiration_shouldReturnFutureDate() {
        UserDetails user = createUser("testuser");
        String token = jwtUtils.generateToken(user);
        assertNotNull(jwtUtils.extractExpiration(token));
        assertTrue(jwtUtils.extractExpiration(token).getTime() > System.currentTimeMillis());
    }
}
