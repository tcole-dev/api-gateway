package org.gateway.core.codec;

import java.util.Map;

import org.gateway.common.model.GatewayResponse;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class GatewayResponseWriter extends SimpleChannelInboundHandler<GatewayResponse> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GatewayResponse msg) throws Exception {
        HttpResponseStatus status = HttpResponseStatus.valueOf(msg.getStatus());

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                msg.getBody() != null ? Unpooled.wrappedBuffer(msg.getBody()) : Unpooled.EMPTY_BUFFER
        );

        // 写入响应头
        if (msg.getHeaders() != null) {
            for (Map.Entry<String, String> entry : msg.getHeaders().entrySet()) {
                response.headers().set(entry.getKey(), entry.getValue());
            }
        }

        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

        ctx.write(response);
    }
}
