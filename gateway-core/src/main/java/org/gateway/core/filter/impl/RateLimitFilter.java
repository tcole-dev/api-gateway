package org.gateway.core.filter.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.gateway.common.enums.FilterPhase;
import org.gateway.common.enums.FilterType;
import org.gateway.common.exception.RateLimitExceededException;
import org.gateway.common.model.GatewayRequest;
import org.gateway.common.model.GatewayResponse;
import org.gateway.core.filter.AbstractGatewayFilter;
import org.gateway.core.filter.rate.TokenBucketRateLimiter;

import lombok.extern.slf4j.Slf4j;

/**
 * 限流过滤器
 * 基于令牌桶算法，按路由维度限流
 *
 * 职责：判断是否触发限流，触发时抛出 RateLimitExceededException
 * 响应构造：由 ErrorFilter 统一处理
 *
 * 配置来源：RouteDefinition.limit（每秒允许的请求数）
 * 限流粒度：每个路由独立限流，互不影响
 */
@Slf4j
public class RateLimitFilter extends AbstractGatewayFilter {

    /** 路由限流器映射（routeId -> TokenBucketRateLimiter） */
    private final Map<String, TokenBucketRateLimiter> rateLimiters = new ConcurrentHashMap<>();

    /** 突发流量倍数（capacity = rate * burstMultiplier） */
    private static final double BURST_MULTIPLIER = 1.0;

    public RateLimitFilter() {
        super("rate-limit", FilterType.RATE_LIMIT, FilterPhase.PRE, 10);
    }

    @Override
    public GatewayResponse preFilter(GatewayRequest request) {
        String routeId = request.getAttribute("routeId");
        int limit = request.getAttribute("limit");

        // 未配置限流或限流阈值为0，跳过限流
        if (limit <= 0) {
            return null;
        }

        // 获取或创建限流器（懒加载）
        TokenBucketRateLimiter limiter = rateLimiters.computeIfAbsent(routeId,
                id -> createRateLimiter(limit));

        // 尝试获取令牌，失败则抛出限流异常
        if (!limiter.tryAcquire()) {
            log.warn("[{}] 限流触发: routeId={}, limit={}/sec, available={}",
                    request.getRequestId(), routeId, limit, limiter.getAvailableTokens());
            throw new RateLimitExceededException(routeId, limit);
        }

        return null;
    }

    /**
     * 创建限流器
     *
     * @param limit 每秒允许的请求数
     */
    private TokenBucketRateLimiter createRateLimiter(int limit) {
        long capacity = Math.max(1, (long) (limit * BURST_MULTIPLIER));
        return new TokenBucketRateLimiter(capacity, limit);
    }

    /**
     * 清理限流器（路由更新时调用）
     */
    public void clearLimiters() {
        rateLimiters.clear();
    }

    /**
     * 获取指定路由的限流器（用于监控）
     */
    public TokenBucketRateLimiter getRateLimiter(String routeId) {
        return rateLimiters.get(routeId);
    }
}
