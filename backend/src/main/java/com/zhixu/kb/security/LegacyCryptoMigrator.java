package com.zhixu.kb.security;

import com.zhixu.kb.ai.entity.AiEndpointEntity;
import com.zhixu.kb.ai.entity.AiUserConfigEntity;
import com.zhixu.kb.ai.mapper.AiEndpointMapper;
import com.zhixu.kb.ai.mapper.AiUserConfigMapper;
import com.zhixu.kb.ask.entity.AskRecordEntity;
import com.zhixu.kb.ask.mapper.AskRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * 一次性数据迁移：旧部署使用公开默认 AES 密钥（zhixu-kb-aes-key-change-me）加密
 * ask_records / ai_user_config.api_key / ai_endpoints.api_key。
 * 当配置了新的 CRYPTO_AES_KEY 时，启动时自动把仍用旧密钥加密的数据重加密为新密钥。
 * 幂等：已迁移/新写入的数据用旧密钥解密必然失败，自动跳过。
 */
@Component
public class LegacyCryptoMigrator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyCryptoMigrator.class);

    private static final String LEGACY_AES_KEY = "zhixu-kb-aes-key-change-me";
    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final AskRecordMapper askRecordMapper;
    private final AiUserConfigMapper aiUserConfigMapper;
    private final AiEndpointMapper aiEndpointMapper;
    private final CryptoService cryptoService;

    @Value("${security.crypto.aes-key}")
    private String currentAesKey;

    public LegacyCryptoMigrator(AskRecordMapper askRecordMapper,
                                AiUserConfigMapper aiUserConfigMapper,
                                AiEndpointMapper aiEndpointMapper,
                                CryptoService cryptoService) {
        this.askRecordMapper = askRecordMapper;
        this.aiUserConfigMapper = aiUserConfigMapper;
        this.aiEndpointMapper = aiEndpointMapper;
        this.cryptoService = cryptoService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (currentAesKey == null || currentAesKey.equals(LEGACY_AES_KEY)) {
            return;
        }
        SecretKeySpec legacyKey = buildKeySpec(LEGACY_AES_KEY);
        int migrated = 0;
        List<AskRecordEntity> records = askRecordMapper.selectList(null);
        for (AskRecordEntity record : records) {
            boolean changed = false;
            if (hasText(record.getQuestion())) {
                String legacyPlain = legacyDecrypt(record.getQuestion(), legacyKey);
                if (legacyPlain != null) {
                    record.setQuestion(cryptoService.encrypt(legacyPlain));
                    changed = true;
                }
            }
            if (hasText(record.getAnswer())) {
                String legacyPlain = legacyDecrypt(record.getAnswer(), legacyKey);
                if (legacyPlain != null) {
                    record.setAnswer(cryptoService.encrypt(legacyPlain));
                    changed = true;
                }
            }
            if (changed) {
                askRecordMapper.updateById(record);
                migrated++;
            }
        }
        log.info("LegacyCryptoMigrator: re-encrypted {} ask_records with new CRYPTO_AES_KEY", migrated);
        migrateUserApiKeys(legacyKey);
        migrateEndpointApiKeys(legacyKey);
    }

    private void migrateUserApiKeys(SecretKeySpec legacyKey) {
        int migrated = 0;
        List<AiUserConfigEntity> entities = aiUserConfigMapper.selectList(null);
        for (AiUserConfigEntity entity : entities) {
            if (!hasText(entity.getApiKey())) {
                continue;
            }
            String legacyPlain = legacyDecrypt(entity.getApiKey(), legacyKey);
            if (legacyPlain != null) {
                entity.setApiKey(cryptoService.encrypt(legacyPlain));
                aiUserConfigMapper.updateById(entity);
                migrated++;
            }
        }
        log.info("LegacyCryptoMigrator: re-encrypted {} ai_user_config api keys", migrated);
    }

    private void migrateEndpointApiKeys(SecretKeySpec legacyKey) {
        int migrated = 0;
        List<AiEndpointEntity> entities = aiEndpointMapper.selectList(null);
        for (AiEndpointEntity entity : entities) {
            if (!hasText(entity.getApiKey())) {
                continue;
            }
            String legacyPlain = legacyDecrypt(entity.getApiKey(), legacyKey);
            if (legacyPlain != null) {
                entity.setApiKey(cryptoService.encrypt(legacyPlain));
                aiEndpointMapper.updateById(entity);
                migrated++;
            }
        }
        log.info("LegacyCryptoMigrator: re-encrypted {} ai_endpoints api keys", migrated);
    }

    private boolean hasText(String value) {
        return value != null && !value.isEmpty();
    }

    private String legacyDecrypt(String encryptedText, SecretKeySpec legacyKey) {
        try {
            byte[] all = Base64.getDecoder().decode(encryptedText);
            if (all.length <= IV_LENGTH) {
                return null;
            }
            byte[] iv = java.util.Arrays.copyOfRange(all, 0, IV_LENGTH);
            byte[] cipherBytes = java.util.Arrays.copyOfRange(all, IV_LENGTH, all.length);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, legacyKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    private SecretKeySpec buildKeySpec(String key) {
        byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length > 32) {
            keyBytes = java.util.Arrays.copyOf(keyBytes, 32);
        }
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            keyBytes = java.util.Arrays.copyOf(keyBytes, 32);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }
}
