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


        String toUrl = instance.getScheme() + "://" + instance.getHost() + ":" + instance.getPort() + msg.getPath();
        var params = msg.getQueryParams();
        if (params != null && !params.isEmpty()) {
            StringJoiner queryJoiner = new StringJoiner("&");
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
                String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);
                queryJoiner.add(key + "=" + value);
            }
            toUrl += "?" + queryJoiner;
        }
        log.info("转发请求到：{}", toUrl);

        GatewayResponse gatewayResponse = client.forwardRequest(msg, toUrl);
        ctx.writeAndFlush(gatewayResponse);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("处理请求时发生错误: {}", cause.getMessage(), cause);

        int status;
        String message;

        if (cause instanceof java.util.concurrent.ExecutionException) {
            Throwable targetException = cause.getCause();
            if (targetException instanceof java.util.concurrent.TimeoutException) {
                status = HttpResponseStatus.GATEWAY_TIMEOUT.code();
                message = "Gateway Timeout: The backend service did not respond in time.";
            } else {
                status = HttpResponseStatus.BAD_GATEWAY.code();
                message = "Bad Gateway: The backend service returned an invalid response or was unreachable.";
            }
        } else if (cause instanceof InterruptedException) {
            status = HttpResponseStatus.SERVICE_UNAVAILABLE.code();
            message = "Service Unavailable: The request was interrupted.";
        } else {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
            message = "Internal Server Error: An unexpected error occurred.";
        }

        if (ctx.channel().isActive()) {
            ctx.writeAndFlush(GatewayResponseWriter.errorResponse(
                    HttpResponseStatus.valueOf(status), message)).addListener(future -> {
                if (!future.isSuccess()) {
                    log.error("Failed to send error response", future.cause());
                }
                ctx.close();
            });
        } else {
            ctx.close();
        }
    }
}
