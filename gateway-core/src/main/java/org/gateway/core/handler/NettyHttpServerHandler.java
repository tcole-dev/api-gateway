package org.gateway.core.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import org.gateway.common.model.GatewayRequest;
import org.gateway.common.model.RouteDefinition;
import org.gateway.core.Bean.BeanContainer;
import org.gateway.core.router.RouteManager;

@Slf4j
public class NettyHttpServerHandler extends SimpleChannelInboundHandler<GatewayRequest> {

    private final RouteManager routeManager;

    public NettyHttpServerHandler() {
        super();
        this.routeManager = BeanContainer.getBean(RouteManager.class);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GatewayRequest msg) throws Exception {
        // log.info("接收到请求：{}", msg.getRequestId());

        String path = msg.getPath();
        RouteDefinition route = routeManager.matchRoute(path);

        // 仅用于Day 2的测试阶段，后续会注释或删除
        log.info("{}", route != null ? "匹配到路由：" + route.getPath() : "未匹配到路由");

        // 1. 构造响应内容
        String responseContent = "Hello Gateway, matched the route: " + (route != null ? route.getPath() : "unknown");
        
        // 2. 创建 FullHttpResponse
        // 使用 Unpooled.copiedBuffer 将字符串转换为 ByteBuf
        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1, 
                HttpResponseStatus.OK, 
                Unpooled.copiedBuffer(responseContent, CharsetUtil.UTF_8)
        );

        // 3. 设置响应头
        response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        
        // log.info("响应内容：{}", responseContent);
        // 4. 写回响应并关闭连接
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // log.error("处理请求时发生错误", cause);
        if (ctx.channel().isActive()) {
            FullHttpResponse response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, 
                    HttpResponseStatus.INTERNAL_SERVER_ERROR, 
                    Unpooled.copiedBuffer("Internal Server Error", CharsetUtil.UTF_8)
            );
            response.headers().set(HttpHeaderNames.CONTENT_TYPE, "text/plain; charset=UTF-8");
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
            ctx.writeAndFlush(response);
        }
    }
}