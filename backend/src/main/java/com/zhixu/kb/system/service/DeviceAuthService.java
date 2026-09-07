package com.zhixu.kb.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.JwtUtils;
import com.zhixu.kb.system.entity.DeviceBinding;
import com.zhixu.kb.system.mapper.DeviceBindingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 桌面版设备授权：
 * <ol>
 *   <li>官网已登录用户申请一次性 bindCode（60 秒）；</li>
 *   <li>桌面端凭 bindCode 兑换长期 device_token（30 天），服务端落库设备绑定；</li>
 *   <li>桌面端定期在线校验（validate），用户可在官网查看/吊销设备。</li>
 * </ol>
 * bindCode 优先存 Redis（多实例共享，原子消费），Redis 不可用时降级内存 Caffeine。
 */
@Slf4j
@Service
public class DeviceAuthService {

    public static final long DEVICE_TOKEN_TTL_MS = 30L * 24 * 3600 * 1000L;
    private static final Duration BIND_CODE_TTL = Duration.ofSeconds(60);

    private final DeviceBindingMapper deviceBindingMapper;
    private final JwtUtils jwtUtils;
    private final StringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    /** bindCode -> userId（一次性，60s 过期）降级缓存 */
    private final Cache<String, Long> bindCodeCache = Caffeine.newBuilder()
            .expireAfterWrite(BIND_CODE_TTL)
            .maximumSize(10_000)
            .build();

    public DeviceAuthService(DeviceBindingMapper deviceBindingMapper,
                             JwtUtils jwtUtils,
                             ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.deviceBindingMapper = deviceBindingMapper;
        this.jwtUtils = jwtUtils;
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    /** 官网已登录用户为待授权桌面端生成一次性绑定码 */
    public Map<String, Object> createBindCode(Long userId) {
        String code = "bind_" + randomHex(32);
        // 优先写 Redis，多实例共享；Redis 成功时不写本地，避免双写放大竞态窗口
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set(redisBindKey(code), String.valueOf(userId), BIND_CODE_TTL);
            } catch (Exception ex) {
                log.warn("BindCode Redis write failed, fallback to local: {}", ex.getMessage());
                bindCodeCache.put(code, userId);
            }
        } else {
            bindCodeCache.put(code, userId);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("bindCode", code);
        data.put("expiresIn", BIND_CODE_TTL.getSeconds());
        return data;
    }

    /** 桌面端用一次性 bindCode 兑换长期设备凭证（码消费即失效，防重放） */
    public Map<String, Object> exchange(String bindCode, String deviceName) {
        if (bindCode == null || bindCode.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "绑定码无效或已过期，请重新验证");
        }
        Long userId = null;
        // Lua 原子消费：GET + DEL 一次完成，防多实例并发重放
        if (redisTemplate != null) {
            try {
                String key = redisBindKey(bindCode);
                DefaultRedisScript<String> script = new DefaultRedisScript<>(
                        "local v = redis.call('GET', KEYS[1]); if v then redis.call('DEL', KEYS[1]); return v; else return nil; end",
                        String.class);
                String val = redisTemplate.execute(script, Collections.singletonList(key));
                if (val != null) {
                    userId = Long.valueOf(val);
                }
            } catch (Exception ex) {
                log.warn("BindCode Redis read failed: {}", ex.getMessage());
            }
        }
        if (userId == null) {
            userId = bindCodeCache.asMap().remove(bindCode);
        }
        if (userId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "绑定码无效或已过期，请重新验证");
        }
        // 每用户设备数上限，防循环建码造无限设备
        Long deviceCount = deviceBindingMapper.selectCount(new QueryWrapper<DeviceBinding>().lambda()
                .eq(DeviceBinding::getUserId, userId));
        if (deviceCount != null && deviceCount >= 20) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "设备数量已达上限（20），请先吊销旧设备");
        }
        String deviceId = UUID.randomUUID().toString();

        DeviceBinding binding = new DeviceBinding();
        binding.setUserId(userId);
        binding.setDeviceId(deviceId);
        binding.setDeviceName(sanitizeName(deviceName));
        binding.setRevoked(0);
        binding.setLastSeenAt(LocalDateTime.now());
        deviceBindingMapper.insert(binding);

        String token = jwtUtils.generateDeviceToken(userId, deviceId, DEVICE_TOKEN_TTL_MS);
        Map<String, Object> data = new HashMap<>();
        data.put("deviceToken", token);
        data.put("deviceId", deviceId);
        data.put("expiresInMs", DEVICE_TOKEN_TTL_MS);
        log.info("Desktop device bound: userId={} deviceId={}", userId, deviceId);
        return data;
    }

    /** 在线校验：签名/过期/吊销三重检查，顺带更新最后在线时间（滑动离线宽限窗口） */
    public Map<String, Object> validate(String deviceToken) {
        Map<String, Object> data = new HashMap<>();
        try {
            var claims = jwtUtils.parseToken(deviceToken);
            if (!"device".equals(claims.get("typ"))) {
                data.put("valid", false);
                data.put("reason", "NOT_DEVICE_TOKEN");
                return data;
            }
            String subject = claims.getSubject();
            String deviceId = subject != null && subject.startsWith("device:")
                    ? subject.substring("device:".length()) : null;
            Long userId = claims.get("uid", Long.class);
            if (deviceId == null || userId == null) {
                data.put("valid", false);
                data.put("reason", "MALFORMED");
                return data;
            }
            DeviceBinding binding = deviceBindingMapper.selectOne(new QueryWrapper<DeviceBinding>().lambda()
                    .eq(DeviceBinding::getDeviceId, deviceId));
            if (binding == null || (binding.getRevoked() != null && binding.getRevoked() == 1)
                    || !userId.equals(binding.getUserId())) {
                data.put("valid", false);
                data.put("reason", "REVOKED_OR_MISSING");
                return data;
            }
            binding.setLastSeenAt(LocalDateTime.now());
            deviceBindingMapper.updateById(binding);
            data.put("valid", true);
            data.put("userId", userId);
            data.put("deviceId", deviceId);
            data.put("expiresAt", claims.getExpiration().getTime());
            return data;
        } catch (Exception ex) {
            data.put("valid", false);
            data.put("reason", "INVALID_TOKEN");
            return data;
        }
    }

    /** 滑动续期：旧凭证仍有效时签发新 30 天凭证（旧绑定保留，避免设备列表膨胀） */
    public Map<String, Object> refresh(String deviceToken) {
        Map<String, Object> status = validate(deviceToken);
        if (!Boolean.TRUE.equals(status.get("valid"))) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "设备凭证无效，请重新验证");
        }
        String deviceId = String.valueOf(status.get("deviceId"));
        Long userId = (Long) status.get("userId");
        String newToken = jwtUtils.generateDeviceToken(userId, deviceId, DEVICE_TOKEN_TTL_MS);
        Map<String, Object> data = new HashMap<>();
        data.put("deviceToken", newToken);
        data.put("deviceId", deviceId);
        data.put("expiresInMs", DEVICE_TOKEN_TTL_MS);
        return data;
    }

    /** 用户在官网查看自己的设备列表 */
    public List<DeviceBinding> myDevices(Long userId) {
        return deviceBindingMapper.selectList(new QueryWrapper<DeviceBinding>().lambda()
                .eq(DeviceBinding::getUserId, userId)
                .orderByDesc(DeviceBinding::getId));
    }

    /** 吊销设备：桌面端下次在线校验即失效 */
    public void revoke(Long userId, String deviceId) {
        DeviceBinding binding = deviceBindingMapper.selectOne(new QueryWrapper<DeviceBinding>().lambda()
                .eq(DeviceBinding::getDeviceId, deviceId)
                .eq(DeviceBinding::getUserId, userId));
        if (binding == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "设备不存在");
        }
        binding.setRevoked(1);
        deviceBindingMapper.updateById(binding);
    }

    private static String sanitizeName(String name) {
        if (name == null || name.isBlank()) {
            return "Windows 设备";
        }
        // 防存储型 XSS：官网设备列表展示该字段，需剥危险字符
        String clean = name.trim().replaceAll("[<>\"'&]", "");
        return clean.length() > 100 ? clean.substring(0, 100) : clean;
    }

    private String redisBindKey(String code) {
        return "device:bind:" + code;
    }

    private String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append("0123456789abcdef".charAt(random.nextInt(16)));
        }
        return sb.toString();
    }
}
