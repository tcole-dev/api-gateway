package org.gateway.core.handler;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.*;
import io.netty.util.CharsetUtil;
import lombok.extern.slf4j.Slf4j;

import org.gateway.common.model.GatewayRequest;

@Slf4j
public class NettyHttpServerHandler extends SimpleChannelInboundHandler<GatewayRequest> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GatewayRequest msg) throws Exception {
        log.info("接收到请求：{}", msg.getRequestId());
        // 1. 构造响应内容
        String responseContent = "Hello Gateway";
        
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
        
        log.info("响应内容：{}", responseContent);
        // 4. 写回响应并关闭连接
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("处理请求时发生错误", cause);
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