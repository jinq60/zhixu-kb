package com.zhixu.kb.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TokenRevocationStoreTest {

    @Test
    void revokeThenIsRevoked_shouldReturnTrue_inMemoryOnly() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        TokenRevocationStore store = new TokenRevocationStore(provider);

        assertFalse(store.isRevoked("token-1"));
        store.revoke("token-1");
        assertTrue(store.isRevoked("token-1"));
    }

    @Test
    void isRevoked_unknownToken_shouldReturnFalse() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        TokenRevocationStore store = new TokenRevocationStore(provider);

        assertFalse(store.isRevoked("never-revoked"));
    }
}
