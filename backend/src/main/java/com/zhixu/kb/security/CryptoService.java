package com.zhixu.kb.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * 敏感数据加密（AES/GCM）：咨询记录、文书记录内容落库前加密存储。
 */
@Component
public class CryptoService {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    @Value("${security.crypto.aes-key}")
    private String aesKey;

    private SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    @PostConstruct
    public void init() {
        if (aesKey == null || aesKey.isEmpty()) {
            throw new IllegalStateException(
                    "CRYPTO_AES_KEY is missing. Set a strong random value (16/24/32 bytes) in environment before starting.");
        }
        byte[] keyBytes = aesKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException(
                    "CRYPTO_AES_KEY length must be 16/24/32 bytes (UTF-8), got " + keyBytes.length +
                            ". Do not pad or truncate keys; use a cryptographically strong random value.");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(cipherText, 0, out, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception ex) {
            throw new IllegalStateException("Sensitive data encryption failed", ex);
        }
    }

    public String decrypt(String encryptedText) {
        if (encryptedText == null) {
            return null;
        }
        try {
            byte[] all = Base64.getDecoder().decode(encryptedText);
            if (all.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Encrypted payload too short");
            }
            byte[] iv = Arrays.copyOfRange(all, 0, IV_LENGTH);
            byte[] cipherBytes = Arrays.copyOfRange(all, IV_LENGTH, all.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Sensitive data decryption failed (CRYPTO_AES_KEY changed?)", ex);
        }
    }
}
