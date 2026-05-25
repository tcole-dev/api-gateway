package org.gateway.core.proxy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import org.gateway.core.bean.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 可信代理解析器。
 * 维护可信代理IP白名单（CIDR），从 X-Forwarded-For 中提取真实客户端IP。
 *
 * 逻辑：
 * 1. 若 TCP remoteIp 不在白名单 → remoteIp 即为真实客户端IP
 * 2. 若 TCP remoteIp 在白名单 → 从 X-Forwarded-For 右向左找第一个非白名单IP
 */
@Slf4j
public class TrustedProxyResolver implements Component {

    private final List<Cidr> trustedRanges;

    public TrustedProxyResolver(List<String> cidrList) {
        this.trustedRanges = cidrList.stream()
                .map(Cidr::new)
                .toList();
        log.info("可信代理白名单加载完成，共 {} 条CIDR", trustedRanges.size());
    }

    /**
     * 从请求中提取真实客户端IP
     *
     * @param remoteAddress TCP连接地址（host:port 或 [host]:port）
     * @param xForwardedFor X-Forwarded-For 头的值，可能为null
     * @return 真实客户端IP
     */
    public String resolve(String remoteAddress, String xForwardedFor) {
        String remoteIp = parseIp(remoteAddress);

        if (!isTrusted(remoteIp)) {
            return remoteIp;
        }

        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            String[] parts = xForwardedFor.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                String candidate = parts[i].trim();
                if (!candidate.isEmpty() && !isTrusted(candidate)) {
                    return candidate;
                }
            }
        }

        return remoteIp;
    }

    /**
     * 判断IP是否属于可信代理范围
     */
    private boolean isTrusted(String ip) {
        for (Cidr cidr : trustedRanges) {
            if (cidr.matches(ip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从地址字符串中提取IP部分
     * 处理格式：1.2.3.4:8080、[::1]:8080、/1.2.3.4:8080
     */
    private String parseIp(String address) {
        if (address == null || address.isEmpty()) {
            return "";
        }
        // 去掉前导 /
        if (address.startsWith("/")) {
            address = address.substring(1);
        }
        // IPv6: [::1]:8080 → ::1
        if (address.startsWith("[")) {
            int end = address.indexOf(']');
            if (end > 0) {
                return address.substring(1, end);
            }
        }
        // IPv4: 1.2.3.4:8080 → 1.2.3.4
        int colon = address.lastIndexOf(':');
        if (colon > 0) {
            return address.substring(0, colon);
        }
        return address;
    }

    // ==================== CIDR 匹配 ====================

    private static class Cidr {
        private final byte[] network;
        private final int prefixLen;

        Cidr(String cidr) {
            try {
                int slash = cidr.indexOf('/');
                String ipPart;
                if (slash > 0) {
                    ipPart = cidr.substring(0, slash);
                    this.prefixLen = Integer.parseInt(cidr.substring(slash + 1));
                } else {
                    ipPart = cidr;
                    this.prefixLen = isIpv4(ipPart) ? 32 : 128;
                }
                this.network = toBytes(ipPart);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid CIDR: " + cidr, e);
            }
        }

        boolean matches(String ip) {
            try {
                byte[] ipBytes = toBytes(ip);
                if (ipBytes.length != network.length) {
                    return false;
                }
                int fullBytes = prefixLen / 8;
                int remainder = prefixLen % 8;

                for (int i = 0; i < fullBytes; i++) {
                    if (ipBytes[i] != network[i]) {
                        return false;
                    }
                }
                if (remainder > 0 && fullBytes < ipBytes.length) {
                    int mask = 0xFF << (8 - remainder);
                    if ((ipBytes[fullBytes] & mask) != (network[fullBytes] & mask)) {
                        return false;
                    }
                }
                return true;
            } catch (UnknownHostException e) {
                return false;
            }
        }

        private static boolean isIpv4(String ip) {
            return !ip.contains(":");
        }

        private static byte[] toBytes(String ip) throws UnknownHostException {
            // 处理 IPv4-mapped IPv6（::ffff:1.2.3.4）→ 提取 IPv4 部分
            if (ip.startsWith("::ffff:")) {
                String ipv4Part = ip.substring(7);
                if (isIpv4(ipv4Part)) {
                    return InetAddress.getByName(ipv4Part).getAddress();
                }
            }
            return InetAddress.getByName(ip).getAddress();
        }
    }
}
