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
        // log.info("接收到请求：{}", msg.getRequestId());

        String path = msg.getPath();
        RouteDefinition route = routeManager.matchRoute(path);
        
        // 路由匹配失败，返回404响应
        if (route == null) {
            log.warn("未找到匹配的路由实例：{}", path);
            GatewayResponse response = new GatewayResponse();
            response.setStatus(HttpResponseStatus.NOT_FOUND.code());
            String responseContent = "No route found for path: " + path;
            response.setBody(responseContent.getBytes());
            response.setHeaders(Map.of("Content-Type", "text/plain", "Content-Length", String.valueOf(responseContent.length())));
            ctx.writeAndFlush(response);
            return;
        }

        ServiceInstance instance = balanceLoader.select(route.getServiceInstances());
        // 实例选择失败，返回503响应
        if (instance == null) {
            log.warn("未找到匹配的服务实例：{}", route.getRouteId());
            GatewayResponse response = new GatewayResponse();
            response.setStatus(HttpResponseStatus.SERVICE_UNAVAILABLE.code());
            String responseContent = "No available service instance for route: " + route.getRouteId();
            response.setBody(responseContent.getBytes());
            response.setHeaders(Map.of("Content-Type", "text/plain", "Content-Length", String.valueOf(responseContent.length())));
            ctx.writeAndFlush(response);
            return;
        }
        
        // 添加代理头到 GatewayRequest（而非 FullHttpRequest）
        String realClientIp = msg.getRemoteAddress();
        msg.getHeaders().putIfAbsent("X-Forwarded-For", realClientIp);
        msg.getHeaders().putIfAbsent("X-Real-IP", realClientIp);
        msg.getHeaders().putIfAbsent("Forwarded", "for=" + realClientIp);

        String toUrl = instance.getScheme() + "://" + instance.getHost() + ":" + instance.getPort() + msg.getPath();
        var params = msg.getQueryParams();
        if (params != null && !params.isEmpty()) {
            StringJoiner queryJoiner = new StringJoiner("&");
            
            for (Map.Entry<String, String> entry : params.entrySet()) {
                // 对 key 和 value 都进行 UTF-8 URL 编码
                String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
                String value = URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8);

                queryJoiner.add(key + "=" + value);
            }
            
            // 拼接最终查询字符串
            toUrl += "?" + queryJoiner;
        }
        log.info("转发请求到：{}", toUrl);

        GatewayResponse gatewayResponse = client.forwardRequest(msg, toUrl);

        ctx.writeAndFlush(gatewayResponse);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 记录错误日志
        log.error("处理请求时发生错误: {}", cause.getMessage(), cause);

        int status;
        String message;

        // 1. 处理执行异常 (通常来自 HttpClient.forwardRequest)
        if (cause instanceof java.util.concurrent.ExecutionException) {
            Throwable targetException = cause.getCause();
            
            // 1.1 判断是否为超时
            if (targetException instanceof java.util.concurrent.TimeoutException) {
                status = HttpResponseStatus.GATEWAY_TIMEOUT.code(); // 504
                message = "Gateway Timeout: The backend service did not respond in time.";
            } 
            // 1.2 其他后端通信错误 (连接拒绝, DNS失败等)
            else {
                status = HttpResponseStatus.BAD_GATEWAY.code(); // 502
                message = "Bad Gateway: The backend service returned an invalid response or was unreachable.";
            }
        } 
        // 2. 处理中断异常 (线程被中断)
        else if (cause instanceof InterruptedException) {
            status = HttpResponseStatus.SERVICE_UNAVAILABLE.code(); // 503
            message = "Service Unavailable: The request was interrupted.";
        } 
        // 3. 其他未知异常
        else {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code(); // 500
            message = "Internal Server Error: An unexpected error occurred.";
        }

        // 构建并发送响应
        if (ctx.channel().isActive()) {
            GatewayResponse response = new GatewayResponse();
            response.setStatus(status);
            response.setBody(message.getBytes(StandardCharsets.UTF_8));
            response.setHeaders(Map.of(
                "Content-Type", "text/plain; charset=utf-8",
                "Content-Length", String.valueOf(message.getBytes(StandardCharsets.UTF_8).length)
            ));
            
            // 写入响应并关闭通道
            ctx.writeAndFlush(response).addListener(future -> {
                if (!future.isSuccess()) {
                    log.error("Failed to send error response", future.cause());
                }
                ctx.close();
            });
        } else {
            // 如果通道已不活跃，直接关闭
            ctx.close();
        }
    }
}