package com.zhixu.kb.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

class CryptoServiceTest {

    private CryptoService cryptoService;

    @BeforeEach
    void setUp() throws Exception {
        cryptoService = new CryptoService();
        setAesKey(cryptoService, "0123456789abcdef0123456789abcdef");
        cryptoService.init();
    }

    private void setAesKey(CryptoService service, String key) throws Exception {
        Field field = CryptoService.class.getDeclaredField("aesKey");
        field.setAccessible(true);
        field.set(service, key);
    }

    @Test
    void encryptThenDecrypt_shouldReturnOriginalText() {
        String plain = "这是需要加密的敏感内容 api-key-12345";
        String encrypted = cryptoService.encrypt(plain);

        assertNotNull(encrypted);
        assertNotEquals(plain, encrypted);
        assertEquals(plain, cryptoService.decrypt(encrypted));
    }

    @Test
    void encrypt_shouldProduceDifferentCiphertextEachTime() {
        String plain = "same-content";
        String first = cryptoService.encrypt(plain);
        String second = cryptoService.encrypt(plain);

        assertNotEquals(first, second, "GCM 随机 IV 应保证相同明文每次密文不同");
    }

    @Test
    void encryptNull_shouldReturnNull() {
        assertNull(cryptoService.encrypt(null));
    }

    @Test
    void decryptNull_shouldReturnNull() {
        assertNull(cryptoService.decrypt(null));
    }

    @Test
    void decryptTamperedCiphertext_shouldThrow() {
        String encrypted = cryptoService.encrypt("secret");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThrows(IllegalStateException.class, () -> cryptoService.decrypt(tampered));
    }

    @Test
    void decryptGarbage_shouldThrow() {
        assertThrows(IllegalStateException.class, () -> cryptoService.decrypt("not-a-valid-base64-@#"));
    }

    @Test
    void init_withShortKey_shouldRejectInvalidKey() throws Exception {
        CryptoService service = new CryptoService();
        setAesKey(service, "short");
        assertThrows(IllegalStateException.class, service::init);
    }
}
