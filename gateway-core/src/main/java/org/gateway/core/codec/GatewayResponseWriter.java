package org.gateway.core.codec;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.gateway.common.model.GatewayResponse;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;

public class GatewayResponseWriter extends ChannelOutboundHandlerAdapter {

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {
        if (msg instanceof GatewayResponse gatewayResponse) {
            FullHttpResponse response = toHttpResponse(gatewayResponse);
            ctx.write(response, promise);
        } else {
            ctx.write(msg, promise);
        }
    }

    private FullHttpResponse toHttpResponse(GatewayResponse msg) {
        HttpResponseStatus status = HttpResponseStatus.valueOf(msg.getStatus());

        FullHttpResponse response = new DefaultFullHttpResponse(
                HttpVersion.HTTP_1_1,
                status,
                msg.getBody() != null ? Unpooled.wrappedBuffer(msg.getBody()) : Unpooled.EMPTY_BUFFER
        );

        if (msg.getHeaders() != null) {
            for (Map.Entry<String, String> entry : msg.getHeaders().entrySet()) {
                response.headers().set(entry.getKey(), entry.getValue());
            }
        }

        response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());
        return response;
    }

    /**
     * 构建纯文本错误响应
     */
    public static GatewayResponse errorResponse(HttpResponseStatus status, String message) {
        GatewayResponse response = new GatewayResponse();
        response.setStatus(status.code());
        response.setBody(message.getBytes(StandardCharsets.UTF_8));
        response.setHeaders(Map.of(
                "Content-Type", "text/plain; charset=utf-8",
                "Content-Length", String.valueOf(message.getBytes(StandardCharsets.UTF_8).length)));
        return response;
    }
}
