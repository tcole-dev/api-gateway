package org.gateway.core.filter;

import org.gateway.common.enums.FilterPhase;
import org.gateway.common.enums.FilterType;
import org.gateway.common.model.GatewayRequest;
import org.gateway.common.model.GatewayResponse;

import lombok.Getter;

/**
 * 过滤器抽象基类
 * 定义过滤器的基本属性和执行方法
 */
@Getter
public abstract class AbstractGatewayFilter implements Comparable<AbstractGatewayFilter> {

    /** 过滤器唯一标识 */
    private final String id;

    /** 过滤器功能类型 */
    private final FilterType type;

    /** 过滤器执行阶段 */
    private final FilterPhase phase;

    /** 执行顺序（数值越小越先执行） */
    private final int order;

    /** 是否启用 */
    private boolean enabled = true;

    public AbstractGatewayFilter(String id, FilterType type, FilterPhase phase, int order) {
        this.id = id;
        this.type = type;
        this.phase = phase;
        this.order = order;
    }

    /**
     * 前置过滤：在请求转发到后端之前执行
     * 子类按需重写
     *
     * @param request 请求
     * @return 处理后的响应，返回 不为null 表示中断链路（如限流拒绝）
     */
    public GatewayResponse preFilter(GatewayRequest request) {
        return null;
    }

    /**
     * 后置过滤：在收到后端响应后执行
     * 子类按需重写
     *
     * @param request  请求
     * @param response 响应
     * @return 处理后的响应
     */
    public GatewayResponse postFilter(GatewayRequest request, GatewayResponse response) {
        return response;
    }

    /**
     * 异常过滤：在发生异常时执行
     * 子类按需重写
     *
     * @param request 请求
     * @param ex      异常
     * @return 错误响应
     */
    public GatewayResponse errorFilter(GatewayRequest request, Throwable ex) {
        return null;
    }

    /**
     * 设置启用状态
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * 按 order 排序
     */
    @Override
    public int compareTo(AbstractGatewayFilter other) {
        return Integer.compare(this.order, other.order);
    }
}
