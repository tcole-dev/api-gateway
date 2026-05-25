package org.gateway.common.enums;

import lombok.Getter;

/**
 * 过滤器功能类型枚举
 * 定义网关支持的过滤器种类
 */
@Getter
public enum FilterType {

    // ===== 认证/授权 =====
    AUTH("auth", "JWT认证过滤器"),

    // ===== 限流/熔断 =====
    RATE_LIMIT("rate_limit", "限流过滤器"),
    CIRCUIT_BREAKER("circuit_breaker", "熔断过滤器"),

    // ===== 安全 =====
    IP_BLACKLIST("ip_blacklist", "IP黑名单过滤器"),
    IP_WHITELIST("ip_whitelist", "IP白名单过滤器"),
    CORS("cors", "跨域处理过滤器"),

    // ===== 请求/响应转换 =====
    REQUEST_HEADER("request_header", "请求头修改过滤器"),
    RESPONSE_HEADER("response_header", "响应头修改过滤器"),
    PATH_REWRITE("path_rewrite", "路径重写过滤器"),

    // ===== 可观测性 =====
    LOGGING("logging", "日志过滤器"),
    METRIC("metric", "监控指标过滤器"),
    TRACE("trace", "链路追踪过滤器"),

    // ===== 其他 =====
    RETRY("retry", "重试过滤器"),
    CACHE("cache", "缓存过滤器"),

    // ===== 异常处理 =====
    ERROR("error", "异常处理过滤器");

    /** 过滤器类型标识 */
    private final String code;

    /** 过滤器描述 */
    private final String description;

    FilterType(String code, String description) {
        this.code = code;
        this.description = description;
    }
}