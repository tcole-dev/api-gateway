package org.gateway.core.proxy;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.CompletableFuture;

import org.gateway.common.exception.GatewayBusinessException;
import org.gateway.common.exception.RouteNotFoundException;
import org.gateway.common.exception.ServiceUnavailableException;
import org.gateway.common.model.GatewayRequest;
import org.gateway.common.model.GatewayResponse;
import org.gateway.common.model.RouteDefinition;
import org.gateway.common.model.ServiceInstance;
import org.gateway.core.bean.BeanContainer;
import org.gateway.core.bean.Component;
import org.gateway.core.balance.BalanceLoader;
import org.gateway.core.client.HttpClient;
import org.gateway.core.router.RouteManager;

import lombok.extern.slf4j.Slf4j;

/**
 * 代理服务
 * 封装完整的代理转发流程：路由匹配 → 负载均衡 → 请求转发
 */
@Slf4j
public class ProxyService implements Component {

    private final RouteManager routeManager;
    private final BalanceLoader balanceLoader;
    private final HttpClient httpClient;

    public ProxyService() {
        this.routeManager = BeanContainer.getBean(RouteManager.class);
        this.balanceLoader = BeanContainer.getBean(BalanceLoader.class);
        this.httpClient = BeanContainer.getBean(HttpClient.class);
    }

    /**
     * 执行代理转发（完整流程）
     * 从请求上下文获取路由信息 → 负载均衡 → 记录上下文 → 转发请求
     *
     * @param request 请求（已包含路由信息）
     * @return 异步后端响应
     */
    public CompletableFuture<GatewayResponse> execute(GatewayRequest request) {
        // 1. 从请求上下文获取路由信息（FilterChain已提前匹配）
        RouteDefinition route = request.getAttribute("route");
        if (route == null) {
            return CompletableFuture.failedFuture(new RouteNotFoundException(request.getPath()));
        }

        // 2. 负载均衡选择实例
        ServiceInstance instance = balanceLoader.select(route.getServiceInstances());
        if (instance == null) {
            return CompletableFuture.failedFuture(new ServiceUnavailableException(route.getRouteId()));
        }

        // 3. 记录实例信息到请求上下文（供日志过滤器使用）
        request.setAttribute("instance", instance.getHost() + ":" + instance.getPort());
        String forward = instance.getScheme() + "://" + instance.getHost() + ":" + instance.getPort() + request.getPath();
        request.setAttribute("forward", forward);

        // 4. 添加代理头
        String realClientIp = request.getRemoteAddress();
        // X-Forwarded-For：追加 TCP 源地址，保留代理链
        String tcpRemote = request.getAttribute("tcpRemoteAddress");
        String xff = request.getHeaders().get("X-Forwarded-For");
        String newXff = (xff != null) ? xff + ", " + tcpRemote : tcpRemote;
        request.getHeaders().put("X-Forwarded-For", newXff);
        // X-Real-Ip：TrustedProxyResolver 解析后的真实客户端 IP
        request.getHeaders().put("X-Real-Ip", realClientIp);

        // 5. 构建目标 URL 并转发
        String toUrl = buildTargetUrl(instance, request);

        return httpClient.forwardRequest(request, toUrl)
                .exceptionally(ex -> {
                    if (ex.getCause() instanceof GatewayBusinessException) {
                        throw (GatewayBusinessException) ex.getCause();
                    }
                    throw new GatewayBusinessException(502,
                            "Bad Gateway: " + ex.getMessage(), ex);
                });
    }

    /**
     * 构建目标 URL
     */
    private String buildTargetUrl(ServiceInstance instance, GatewayRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append(instance.getScheme())
          .append("://")
          .append(instance.getHost())
          .append(":")
          .append(instance.getPort())
          .append(request.getPath());

        var params = request.getQueryParams();
        if (params != null && !params.isEmpty()) {
            StringJoiner queryJoiner = new StringJoiner("&");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
                String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
                queryJoiner.add(key + "=" + value);
            }
            sb.append("?").append(queryJoiner);
        }

        return sb.toString();
    }
}
