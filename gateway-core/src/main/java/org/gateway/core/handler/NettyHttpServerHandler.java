package org.gateway.core.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.StringJoiner;

import org.gateway.common.model.GatewayRequest;
import org.gateway.common.model.GatewayResponse;
import org.gateway.common.model.RouteDefinition;
import org.gateway.common.model.ServiceInstance;
import org.gateway.core.Bean.BeanContainer;
import org.gateway.core.Bean.HttpClient;
import org.gateway.core.balance.BalanceLoader;
import org.gateway.core.codec.GatewayResponseWriter;
import org.gateway.core.router.RouteManager;

@Slf4j
public class NettyHttpServerHandler extends SimpleChannelInboundHandler<GatewayRequest> {

    private final RouteManager routeManager;
    private final BalanceLoader balanceLoader;
    private final HttpClient client;

    public NettyHttpServerHandler() {
        super();
        this.routeManager = BeanContainer.getBean(RouteManager.class);
        this.balanceLoader = BeanContainer.getBean(BalanceLoader.class);
        this.client = BeanContainer.getBean(HttpClient.class);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GatewayRequest msg) throws Exception {
        String path = msg.getPath();
        RouteDefinition route = routeManager.matchRoute(path);

        // 路由匹配失败，返回404响应
        if (route == null) {
            log.warn("未找到匹配的路由实例：{}", path);
            ctx.writeAndFlush(GatewayResponseWriter.errorResponse(
                    HttpResponseStatus.NOT_FOUND,
                    "No route found for path: " + path));
            return;
        }

        // 负载均衡选择实例
        ServiceInstance instance = balanceLoader.select(route.getServiceInstances());

        // 实例选择失败，返回503响应
        if (instance == null) {
            log.warn("未找到匹配的服务实例：{}", route.getRouteId());
            ctx.writeAndFlush(GatewayResponseWriter.errorResponse(
                    HttpResponseStatus.SERVICE_UNAVAILABLE,
                    "No available service instance for route: " + route.getRouteId()));
            return;
        }

        // 添加代理头
        String realClientIp = msg.getRemoteAddress();
        msg.getHeaders().put("X-Forwarded-For", realClientIp);
        msg.getHeaders().put("X-Real-Ip", realClientIp);

        // 构建目标 URL
        String toUrl = buildTargetUrl(instance, msg);
        log.info("转发请求到：{}", toUrl);

        // 转发请求并回写响应
        GatewayResponse gatewayResponse = client.forwardRequest(msg, toUrl);
        ctx.writeAndFlush(gatewayResponse);
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
