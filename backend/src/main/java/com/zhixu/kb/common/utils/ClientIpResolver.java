package com.zhixu.kb.common.utils;

import com.zhixu.kb.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 客户端 IP 解析：仅当请求来源（remoteAddr）命中可信代理网段时才信任
 * X-Forwarded-For / X-Real-IP，避免外部请求伪造代理头绕过限流/审计。
 * <p>
 * 可信网段由 app.client-ip.trusted-proxies 配置（支持单 IP 与 IPv4/IPv6 CIDR），
 * 默认覆盖回环 + RFC1918 私网/Docker 网桥 + IPv6 ULA，使 nginx 反代与
 * Docker Compose 部署下都能取到真实客户端 IP。
 */
@Slf4j
@Component
public class ClientIpResolver {

    private final Set<String> exactTrusted = new HashSet<>();
    private final List<long[]> trustedV4Cidrs = new ArrayList<>();
    private final List<byte[]> trustedV6Prefixes = new ArrayList<>();
    private final List<Integer> trustedV6PrefixLens = new ArrayList<>();

    private final AppProperties appProperties;

    public ClientIpResolver(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @PostConstruct
    public void init() {
        List<String> proxies = appProperties.getClientIp().getTrustedProxies();
        if (proxies == null) {
            return;
        }
        for (String raw : proxies) {
            String entry = raw == null ? "" : raw.trim();
            if (entry.isEmpty()) {
                continue;
            }
            try {
                int slash = entry.indexOf('/');
                String addrText = slash > 0 ? entry.substring(0, slash) : entry;
                // 仅接受字面量 IP/CIDR；域名不解析（避免 DNS 变化引入信任漂移）
                if (!addrText.matches("^[0-9a-fA-F:.]+$")) {
                    log.warn("Ignore non-literal trusted proxy entry: {}", entry);
                    continue;
                }
                byte[] bytes = InetAddress.getByName(addrText).getAddress();
                if (bytes.length == 4) {
                    int prefix = slash > 0 ? Integer.parseInt(entry.substring(slash + 1)) : 32;
                    long network = ((bytes[0] & 0xFFL) << 24) | ((bytes[1] & 0xFFL) << 16)
                            | ((bytes[2] & 0xFFL) << 8) | (bytes[3] & 0xFFL);
                    int effective = Math.max(0, Math.min(32, prefix));
                    long mask = effective == 0 ? 0L : (0xFFFFFFFFL << (32 - effective)) & 0xFFFFFFFFL;
                    trustedV4Cidrs.add(new long[]{network & mask, mask});
                } else {
                    int prefix = slash > 0 ? Integer.parseInt(entry.substring(slash + 1)) : 128;
                    trustedV6Prefixes.add(bytes);
                    trustedV6PrefixLens.add(Math.max(0, Math.min(128, prefix)));
                }
                exactTrusted.add(entry.toLowerCase(Locale.ROOT));
            } catch (Exception ex) {
                log.warn("Parse trusted proxy entry failed: {} err={}", entry, ex.getMessage());
            }
        }
        log.info("Trusted proxy ranges loaded: v4Cidrs={} v6Prefixes={} exact={}",
                trustedV4Cidrs.size(), trustedV6Prefixes.size(), exactTrusted);
    }

    public boolean isTrustedProxy(String ip) {
        if (ip == null || ip.isEmpty()) {
            return false;
        }
        String normalized = normalizeIp(ip);
        if (normalized.isEmpty()) {
            return false;
        }
        if (exactTrusted.contains(normalized)) {
            return true;
        }
        if (normalized.contains(":")) {
            return matchesV6(normalized);
        }
        if (normalized.contains(".")) {
            return matchesV4(normalized);
        }
        return false;
    }

    public String resolve(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (isTrustedProxy(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.trim().isEmpty()) {
                return firstUntrusted(forwarded, remoteAddr);
            }
            String realIp = request.getHeader("X-Real-IP");
            if (realIp != null && !realIp.trim().isEmpty()) {
                return realIp.trim();
            }
        }
        return remoteAddr;
    }

    /**
     * 从 XFF 链中自右向左跳过可信代理，返回第一个不可信地址（即真实客户端）；
     * 全部可信时回退为链首。防伪造：攻击者可在链首注入假 IP，但无法伪造
     * 直连代理所追加的最后一段。
     */
    private String firstUntrusted(String forwarded, String fallback) {
        String[] parts = forwarded.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = normalizeIp(parts[i]);
            if (!candidate.isEmpty() && !isTrustedProxy(candidate)) {
                return candidate;
            }
        }
        String first = normalizeIp(parts[0]);
        return first.isEmpty() ? fallback : first;
    }

    private String normalizeIp(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.startsWith("[") && value.endsWith("]") && value.length() > 2) {
            value = value.substring(1, value.length() - 1);
        }
        int zone = value.indexOf('%');
        if (zone > 0) {
            value = value.substring(0, zone);
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private boolean matchesV4(String ip) {
        if (!ip.matches("^\\d{1,3}(\\.\\d{1,3}){3}$")) {
            return false;
        }
        long addr;
        try {
            byte[] bytes = InetAddress.getByName(ip).getAddress();
            if (bytes.length != 4) {
                return false;
            }
            addr = ((bytes[0] & 0xFFL) << 24) | ((bytes[1] & 0xFFL) << 16)
                    | ((bytes[2] & 0xFFL) << 8) | (bytes[3] & 0xFFL);
        } catch (Exception ex) {
            return false;
        }
        for (long[] cidr : trustedV4Cidrs) {
            if ((addr & cidr[1]) == cidr[0]) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesV6(String ip) {
        byte[] bytes;
        try {
            bytes = InetAddress.getByName(ip).getAddress();
        } catch (Exception ex) {
            return false;
        }
        if (bytes.length != 16) {
            return false;
        }
        for (int i = 0; i < trustedV6Prefixes.size(); i++) {
            byte[] network = trustedV6Prefixes.get(i);
            int prefix = trustedV6PrefixLens.get(i);
            if (prefix <= 0) {
                return true;
            }
            if (prefixBitsMatch(bytes, network, prefix)) {
                return true;
            }
        }
        return false;
    }

    private boolean prefixBitsMatch(byte[] candidate, byte[] network, int prefix) {
        int fullBytes = prefix / 8;
        int remainBits = prefix % 8;
        for (int i = 0; i < fullBytes; i++) {
            if (candidate[i] != network[i]) {
                return false;
            }
        }
        if (remainBits == 0) {
            return true;
        }
        if (fullBytes >= candidate.length) {
            return false;
        }
        int mask = 0xFF << (8 - remainBits);
        return (candidate[fullBytes] & mask) == (network[fullBytes] & mask);
    }
}
