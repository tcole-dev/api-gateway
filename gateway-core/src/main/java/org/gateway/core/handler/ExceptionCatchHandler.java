package org.gateway.core.handler;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.gateway.common.model.GatewayResponse;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * 统一异常处理器：捕获所有上游 Handler 泄漏的异常
 * 位于 Pipeline 末端，确保任何未捕获异常都能返回有意义的响应
 */
@Slf4j
public class ExceptionCatchHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        log.error("Pipeline 异常: {}", cause.getMessage(), cause);

        HttpResponseStatus status;
        String message;

        if (cause instanceof java.util.concurrent.ExecutionException execEx) {
            Throwable target = execEx.getCause();
            if (target instanceof java.util.concurrent.TimeoutException) {
                status = HttpResponseStatus.GATEWAY_TIMEOUT;
                message = "Gateway Timeout: The backend service did not respond in time.";
            } else {
                status = HttpResponseStatus.BAD_GATEWAY;
                message = "Bad Gateway: The backend service returned an invalid response or was unreachable.";
            }
        } else if (cause instanceof InterruptedException) {
            status = HttpResponseStatus.SERVICE_UNAVAILABLE;
            message = "Service Unavailable: The request was interrupted.";
            Thread.currentThread().interrupt();
        } else if (cause instanceof IllegalArgumentException) {
            status = HttpResponseStatus.BAD_REQUEST;
            message = "Bad Request: " + cause.getMessage();
        } else {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            message = "Internal Server Error: An unexpected error occurred.";
        }

        if (ctx.channel().isActive()) {
            byte[] bodyBytes = message.getBytes(StandardCharsets.UTF_8);
            GatewayResponse errorResp = GatewayResponse.builder()
                    .status(status.code())
                    .body(bodyBytes)
                    .headers(Map.of(
                            "Content-Type", "text/plain; charset=utf-8",
                            "Content-Length", String.valueOf(bodyBytes.length)))
                    .build();
            ctx.writeAndFlush(errorResp).addListener(future -> {
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
