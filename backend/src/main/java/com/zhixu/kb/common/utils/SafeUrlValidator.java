package com.zhixu.kb.common.utils;

import com.zhixu.kb.common.exception.BusinessException;
import com.zhixu.kb.common.result.ResultCode;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

/**
 * SSRF 防护：校验用户提交的 baseUrl 只允许公网 http/https，主机名解析后
 * 不得指向内网/保留地址（防内网探测：元数据、Redis、内部服务等）。
 * <p>
 * 为缓解 DNS Rebinding 攻击，保存配置时调用 {@link #validateOrThrow(String)} 进行首次校验；
 * 在实际发起 HTTP 请求前，应再次调用 {@link #validateBeforeRequest(String)} 重新解析并校验目标地址。
 */
public final class SafeUrlValidator {

    /** DNS 解析器（测试可注入替身，避免依赖外部网络） */
    private static volatile HostResolver hostResolver = SafeUrlValidator::resolveDefault;

    /**
     * 代理 fake-ip 兼容开关（默认关闭）。
     * Clash/Mihomo 等代理的 fake-ip 模式会把外网域名解析到 198.18.0.0/15 或
     * IPv6 ULA(fc00::/7) 段，流量由 TUN 转发。仅在本机开发环境显式开启；
     * 生产环境保持 false——这两段属于真实保留地址段，放行等于允许 SSRF 打内网。
     * 通过 -Dssrf.allow-fake-ip-ranges=true 开启。
     */
    private static final boolean ALLOW_FAKE_IP_RANGES =
            Boolean.parseBoolean(System.getProperty("ssrf.allow-fake-ip-ranges", "false"));

    private SafeUrlValidator() {
    }

    @FunctionalInterface
    interface HostResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    static void setHostResolver(HostResolver resolver) {
        hostResolver = resolver == null ? SafeUrlValidator::resolveDefault : resolver;
    }

    private static InetAddress[] resolveDefault(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    /**
     * 保存/更新配置时使用：校验 URL 格式、协议、主机名及解析后的 IP 是否合法。
     */
    public static void validateOrThrow(String baseUrl) {
        validateInternal(baseUrl);
    }

    /**
     * 实际发起 HTTP 请求前使用：重新解析并校验目标地址，防止 DNS Rebinding 绕过。
     */
    public static void validateBeforeRequest(String baseUrl) {
        validateInternal(baseUrl);
    }

    private static void validateInternal(String baseUrl) {
        URI uri;
        try {
            uri = new URI(baseUrl.trim());
        } catch (Exception ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接口地址格式非法");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接口地址仅支持 http/https 协议");
        }
        String host = uri.getHost();
        if (host == null || host.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接口地址缺少主机名");
        }
        if (isDeniedHost(host)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接口地址不允许指向本机/内网地址");
        }
        InetAddress[] resolved;
        try {
            resolved = hostResolver.resolve(host);
        } catch (UnknownHostException ex) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接口地址域名无法解析");
        }
        if (resolved == null || resolved.length == 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "接口地址域名无法解析");
        }
        for (InetAddress addr : resolved) {
            if (isDeniedAddress(addr)) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "接口地址解析后指向内网/保留地址，已拒绝");
            }
        }
    }

    private static boolean isDeniedHost(String host) {
        String lower = host.toLowerCase(Locale.ROOT);
        return lower.equals("localhost")
                || lower.endsWith(".localhost")
                || lower.endsWith(".local")
                || lower.endsWith(".internal")
                || lower.endsWith(".lan")
                || isNumericIpLiteral(lower);
    }

    private static boolean isNumericIpLiteral(String host) {
        return host.chars().allMatch(c -> Character.isDigit(c) || c == '.' || c == ':');
    }

    private static boolean isDeniedAddress(InetAddress addr) {
        if (addr instanceof Inet4Address) {
            byte[] b = addr.getAddress();
            int first = b[0] & 0xFF;
            int second = b[1] & 0xFF;
            if (first == 0) {
                return true;
            }
            if (first == 10) {
                return true;
            }
            if (first == 127) {
                return true;
            }
            if (first == 169 && second == 254) {
                return true;
            }
            if (first == 172 && second >= 16 && second <= 31) {
                return true;
            }
            if (first == 192 && second == 168) {
                return true;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            if (first >= 224) {
                return true;
            }
            // 198.18.0.0/15（benchmark 段）：默认拒绝；仅开发环境开启
            // ssrf.allow-fake-ip-ranges 后放行（Clash/Mihomo fake-ip 模式）
            if (first == 198 && second >= 18 && second <= 19) {
                return !ALLOW_FAKE_IP_RANGES;
            }
            return false;
        }
        if (addr instanceof Inet6Address) {
            byte[] b = addr.getAddress();
            if (b[0] == 0) {
                return true;
            }
            if ((b[0] & 0xFF) == 0xFE && (b[1] & 0xC0) == 0x80) {
                return true;
            }
            if ((b[0] & 0xFF) == 0xFC || (b[0] & 0xFF) == 0xFD) {
                // fc00::/7（ULA 真实私网段）默认拒绝；同上，仅开发环境 fake-ip 兼容时放行
                if (!ALLOW_FAKE_IP_RANGES) {
                    return true;
                }
                return false;
            }
            // 链路本地 fe80::/10 已在上方拦截，此处兜底 ::1
            return Arrays.equals(b, new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1});
        }
        return true;
    }
}
