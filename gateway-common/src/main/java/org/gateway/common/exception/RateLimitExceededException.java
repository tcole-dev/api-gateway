package org.gateway.common.exception;

/**
 * 限流异常（429 Too Many Requests）
 * 请求超过限流阈值时抛出
 */
public class RateLimitExceededException extends GatewayBusinessException {

    /** 路由 ID */
    private final String routeId;

    /** 限流阈值（每秒请求数） */
    private final int limit;

    public RateLimitExceededException(String routeId, int limit) {
        super(429, String.format("Rate limit exceeded for route: %s, limit: %d/sec", routeId, limit));
        this.routeId = routeId;
        this.limit = limit;
    }

    public String getRouteId() {
        return routeId;
    }

    public int getLimit() {
        return limit;
    }
}
