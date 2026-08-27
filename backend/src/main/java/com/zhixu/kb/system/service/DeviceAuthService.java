package com.zhixu.kb.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;
import com.zhixu.kb.common.utils.JwtUtils;
import com.zhixu.kb.system.entity.DeviceBinding;
import com.zhixu.kb.system.mapper.DeviceBindingMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
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
 * bindCode 存内存 Caffeine（60s TTL + 一次性消费），多实例部署时可迁 Redis。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceAuthService {

    public static final long DEVICE_TOKEN_TTL_MS = 30L * 24 * 3600 * 1000L;
    private static final Duration BIND_CODE_TTL = Duration.ofSeconds(60);

    private final DeviceBindingMapper deviceBindingMapper;
    private final JwtUtils jwtUtils;
    private final SecureRandom random = new SecureRandom();

    /** bindCode -> userId（一次性，60s 过期） */
    private final Cache<String, Long> bindCodeCache = Caffeine.newBuilder()
            .expireAfterWrite(BIND_CODE_TTL)
            .maximumSize(10_000)
            .build();

    /** 官网已登录用户为待授权桌面端生成一次性绑定码 */
    public Map<String, Object> createBindCode(Long userId) {
        String code = "bind_" + randomHex(32);
        bindCodeCache.put(code, userId);
        Map<String, Object> data = new HashMap<>();
        data.put("bindCode", code);
        data.put("expiresIn", BIND_CODE_TTL.getSeconds());
        return data;
    }

    /** 桌面端用一次性 bindCode 兑换长期设备凭证（码消费即失效，防重放） */
    public Map<String, Object> exchange(String bindCode, String deviceName) {
        Long userId = bindCodeCache.asMap().remove(bindCode);
        if (userId == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "绑定码无效或已过期，请重新验证");
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
            if (binding == null || binding.getRevoked() != null && binding.getRevoked() == 1
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
        return name.length() > 100 ? name.substring(0, 100) : name.trim();
    }

    private String randomHex(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append("0123456789abcdef".charAt(random.nextInt(16)));
        }
        return sb.toString();
    }
}
