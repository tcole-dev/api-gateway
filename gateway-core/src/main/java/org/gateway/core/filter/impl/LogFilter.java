package org.gateway.core.filter.impl;

import java.util.ArrayList;
import java.util.List;

import org.gateway.common.enums.FilterPhase;
import org.gateway.common.enums.FilterType;
import org.gateway.common.model.GatewayRequest;
import org.gateway.common.model.GatewayResponse;
import org.gateway.core.filter.AbstractGatewayFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * 日志过滤器
 * 记录请求全生命周期信息：入站、路由、过滤器耗时、转发、响应
 * 特殊：由 FilterChain 单独管理，在前置阶段最先执行、后置阶段最后执行
 */
@Slf4j
public class LogFilter extends AbstractGatewayFilter {

    public LogFilter() {
        super("logging", FilterType.LOGGING, FilterPhase.PRE, Integer.MAX_VALUE);
    }

    /**
     * 前置阶段：记录入站时间，初始化过滤器日志列表
     */
    @Override
    public GatewayResponse preFilter(GatewayRequest request) {
        request.setAttribute("inboundTime", System.currentTimeMillis());
        request.setAttribute("filterLogs", new ArrayList<String>());
        return null;
    }

    /**
     * 后置阶段：汇总并输出完整日志
     */
    @Override
    public GatewayResponse postFilter(GatewayRequest request, GatewayResponse response) {
        String requestId = request.getRequestId();
        String method = request.getMethod().name();
        String path = request.getPath();
        long inboundTime = request.getAttribute("inboundTime");
        long totalRt = System.currentTimeMillis() - inboundTime;

        // 入站日志
        log.info("[{}] >>> {} {}", requestId, method, path);

        // 路由信息
        String routeId = request.getAttribute("routeId");
        String instance = request.getAttribute("instance");
        if (routeId != null) {
            log.info("[{}]     Route: {}, Instance: {}", requestId, routeId, instance);
        }

        // 过滤器耗时
        List<String> filterLogs = request.getAttribute("filterLogs");
        if (filterLogs != null && !filterLogs.isEmpty()) {
            log.info("[{}]     Filters: {}", requestId, String.join(", ", filterLogs));
        }

        // 转发信息
        String forward = request.getAttribute("forward");
        if (forward != null) {
            log.info("[{}]     Forward: {}", requestId, forward);
        }

        // 响应日志
        log.info("[{}] <<< {} {}, RT={}ms, Status: {}", requestId, method, path, totalRt, response.getStatus());

        return response;
    }

    /**
     * 打印部分日志（异常时调用）
     * 输出请求基本信息 + 已完成的过滤器耗时
     *
     * @param request        请求
     * @param failedFilterId 失败的过滤器ID（null表示在异常过滤器阶段）
     * @param phase          失败阶段
     */
    public void printPartialLog(GatewayRequest request, String failedFilterId, String phase) {
        String requestId = request.getRequestId();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String path = request.getPath();

        // 入站日志
        log.info("[{}] >>> {} {}", requestId, method, path);

        // 路由信息（如果有）
        String routeId = request.getAttribute("routeId");
        String instance = request.getAttribute("instance");
        if (routeId != null) {
            log.info("[{}]     Route: {}, Instance: {}", requestId, routeId, instance);
        }

        // 已执行的过滤器耗时
        List<String> filterLogs = request.getAttribute("filterLogs");
        if (filterLogs != null && !filterLogs.isEmpty()) {
            log.info("[{}]     Filters: {}", requestId, String.join(", ", filterLogs));
        }

        // 转发信息（如果有）
        String forward = request.getAttribute("forward");
        if (forward != null) {
            log.info("[{}]     Forward: {}", requestId, forward);
        }

        // 异常点
        if (failedFilterId != null) {
            log.error("[{}] !!! Failed at {} phase, filter: {}", requestId, phase, failedFilterId);
        } else {
            log.error("[{}] !!! Failed at error handling phase", requestId);
        }
    }
}
