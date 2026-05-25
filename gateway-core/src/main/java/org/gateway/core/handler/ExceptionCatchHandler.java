package org.gateway.core.handler;


import org.gateway.common.exception.GatewayBusinessException;
import org.gateway.common.model.GatewayResponse;
import org.gateway.core.codec.GatewayResponseWriter;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * Pipeline 框架级异常处理器
 * 只处理 Netty 框架层面的异常（编解码、超时、连接等）
 * 业务异常（GatewayBusinessException）由 FilterChain 中的 ErrorFilter 处理
 */
@Slf4j
public class ExceptionCatchHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        // 业务异常交给 ErrorFilter 处理，不在这里处理
        if (cause instanceof GatewayBusinessException) {
            super.exceptionCaught(ctx, cause);
            return;
        }

        // 只处理 Pipeline 框架异常
        log.error("Pipeline 框架异常: {}", cause.getMessage(), cause);

        HttpResponseStatus status;
        String message;

        // HTTP 解码异常：请求体过大
        if (cause instanceof io.netty.handler.codec.TooLongFrameException) {
            status = HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE;
            message = "Request Entity Too Large: The request exceeds the maximum allowed size.";
        }
        // HTTP 解码异常：格式错误
        else if (cause instanceof io.netty.handler.codec.DecoderException) {
            status = HttpResponseStatus.BAD_REQUEST;
            message = "Bad Request: Failed to decode the HTTP request.";
        }
        // 读超时
        else if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
            status = HttpResponseStatus.REQUEST_TIMEOUT;
            message = "Request Timeout: The client did not send the request in time.";
        }
        // 写超时
        else if (cause instanceof io.netty.handler.timeout.WriteTimeoutException) {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            message = "Write Timeout: Failed to write response in time.";
        }
        // 连接异常
        else if (cause instanceof java.io.IOException) {
            status = HttpResponseStatus.BAD_REQUEST;
            message = "Bad Request: Connection error - " + cause.getMessage();
        }
        // 其他框架异常
        else {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            message = "Internal Server Error: An unexpected pipeline error occurred.";
        }

        if (ctx.channel().isActive()) {
            // 使用 GatewayResponseWriter 的静态方法构建响应
            GatewayResponse errorResp = GatewayResponseWriter.errorResponse(status, message);
            // 使用 channel().writeAndFlush 确保经过 GatewayResponseWriter 出站处理器
            ctx.channel().writeAndFlush(errorResp).addListener(future -> {
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
