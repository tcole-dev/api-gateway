package org.gateway.core.filter.rate;

/**
 * 令牌桶限流器
 * 懒 refill 实现：仅在 tryAcquire 时根据时间戳计算并填充令牌，无定时器
 *
 * 核心参数：
 * - capacity：桶容量，决定突发流量大小
 * - rate：每秒填充速率（每秒允许的请求数）
 */
public class TokenBucketRateLimiter {

    /** 桶容量（突发流量上限） */
    private final long capacity;

    /** 每秒填充速率 */
    private final double rate;

    /** 当前令牌数 */
    private double tokens;

    /** 上次填充时间戳（纳秒） */
    private long lastRefillTimeNanos;

    /**
     * 构造令牌桶限流器
     *
     * @param capacity 桶容量（突发流量上限）
     * @param rate     每秒填充速率
     */
    public TokenBucketRateLimiter(long capacity, double rate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        if (rate <= 0) {
            throw new IllegalArgumentException("rate must be positive");
        }
        this.capacity = capacity;
        this.rate = rate;
        this.tokens = capacity;
        this.lastRefillTimeNanos = System.nanoTime();
    }

    /**
     * 便捷构造：capacity = rate（无突发，匀速限流）
     *
     * @param rate 每秒允许的请求数
     */
    public TokenBucketRateLimiter(double rate) {
        this((long) rate, rate);
    }

    /**
     * 尝试获取令牌
     *
     * @return true 获取成功，false 桶已空
     */
    public synchronized boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * 尝试获取指定数量的令牌
     *
     * @param count 令牌数量
     * @return true 获取成功，false 令牌不足
     */
    public synchronized boolean tryAcquire(int count) {
        refill();
        if (tokens >= count) {
            tokens -= count;
            return true;
        }
        return false;
    }

    /**
     * 获取当前令牌数（用于监控）
     */
    public synchronized long getAvailableTokens() {
        refill();
        return (long) tokens;
    }

    /**
     * 获取桶容量
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * 获取每秒填充速率
     */
    public double getRate() {
        return rate;
    }

    /**
     * 填充令牌（基于时间差计算，调用时必须持有锁）
     * 保留未消耗的时间，避免精度丢失
     */
    private void refill() {
        long now = System.nanoTime();
        long elapsed = now - lastRefillTimeNanos;
        if (elapsed <= 0) {
            return;
        }

        // 计算应填充的令牌数
        double tokensToAdd = (elapsed / 1_000_000_000.0) * rate;

        // 填充令牌，不超过容量
        tokens = Math.min(capacity, tokens + tokensToAdd);

        // 更新时间戳：只扣除已消耗的时间，保留剩余时间
        // 例如：rate=10, elapsed=150ms, tokensToAdd=1.5
        // 填充 1.5 个令牌，时间戳推进 150ms（全部消耗）
        // 因为 tokensToAdd 可能是小数，所以时间戳直接更新为 now
        lastRefillTimeNanos = now;
    }
}
