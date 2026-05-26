package org.gateway.core.filter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.gateway.common.model.GatewayRequest;
import org.gateway.common.model.GatewayResponse;
import org.gateway.core.bean.Component;
import org.gateway.core.filter.impl.LogFilter;
import org.gateway.core.proxy.ProxyService;

import lombok.extern.slf4j.Slf4j;

/**
 * 过滤器链
 * 职责：过滤器编排（前置 → 代理转发 → 后置），异常时执行异常过滤器
 */
@Slf4j
public class FilterChain implements Component {

    /** 前置过滤器（按 order 排序） */
    private final List<AbstractGatewayFilter> preFilters = new ArrayList<>();

    /** 后置过滤器（按 order 排序） */
    private final List<AbstractGatewayFilter> postFilters = new ArrayList<>();

    /** 异常过滤器（按 order 排序） */
    private final List<AbstractGatewayFilter> errorFilters = new ArrayList<>();

    /** 日志过滤器（构造时自动创建，最先/最后执行） */
    private final LogFilter logFilter;

    /** 代理转发服务 */
    private final ProxyService proxyService;

    public FilterChain(ProxyService proxyService) {
        this.proxyService = proxyService;
        this.logFilter = new LogFilter();
    }

    /**
     * 执行过滤器链
     *
     * @param request 请求
     * @return 异步响应
     */
    public CompletableFuture<GatewayResponse> execute(GatewayRequest request) {
        // 1. 前置过滤器（同步快速路径：限流、鉴权等无IO操作）
        GatewayResponse preResult = executePreFilters(request);
        if (preResult != null) {
            return CompletableFuture.completedFuture(preResult);
        }

        // 2. 代理转发 → 后置过滤器 → 异常过滤器（异步编排）
        return proxyService.execute(request)
                .thenApply(response -> executePostFilters(request, response))
                .exceptionally(ex -> executeErrorFilters(request, ex));
    }

    /**
     * 添加过滤器
     */
    public void addFilter(AbstractGatewayFilter filter) {
        if (!filter.isEnabled()) {
            return;
        }
        switch (filter.getPhase()) {
            case PRE -> preFilters.add(filter);
            case POST -> postFilters.add(filter);
            case ERROR -> errorFilters.add(filter);
        }
    }

    /**
     * 批量添加过滤器并排序
     */
    public void addFilters(List<AbstractGatewayFilter> filters) {
        filters.forEach(this::addFilter);
        sortFilters();
    }

    /**
     * 对过滤器列表排序
     */
    public void sortFilters() {
        Collections.sort(preFilters);
        Collections.sort(postFilters);
        Collections.sort(errorFilters);
    }

    /**
     * 执行前置过滤器
     *
     * @return 不为 null 表示中断链路（如限流拒绝）
     */
    private GatewayResponse executePreFilters(GatewayRequest request) {
        logFilter.preFilter(request);

        for (AbstractGatewayFilter filter : preFilters) {
            try {
                GatewayResponse resp = filter.preFilter(request);
                if (resp != null) {
                    return resp;
                }
            } catch (Exception e) {
                logFilter.printPartialLog(request, filter.getId(), "pre");
                throw e;
            }
        }
        return null;
    }

    /**
     * 执行后置过滤器
     */
    private GatewayResponse executePostFilters(GatewayRequest request, GatewayResponse response) {
        GatewayResponse current = response;

        for (AbstractGatewayFilter filter : postFilters) {
            try {
                GatewayResponse resp = filter.postFilter(request, current);
                current = (resp != null) ? resp : current;
            } catch (Exception e) {
                logFilter.printPartialLog(request, filter.getId(), "post");
                throw e;
            }
        }

        return logFilter.postFilter(request, current);
    }

    /**
     * 执行异常过滤器
     */
    private GatewayResponse executeErrorFilters(GatewayRequest request, Throwable ex) {
        logFilter.printPartialLog(request, null, "error");

        for (AbstractGatewayFilter filter : errorFilters) {
            try {
                GatewayResponse resp = filter.errorFilter(request, ex);
                if (resp != null) {
                    return resp;
                }
            } catch (Exception e) {
                log.error("异常过滤器 {} 执行异常: {}", filter.getId(), e.getMessage(), e);
            }
        }
        return null;
    }
}
