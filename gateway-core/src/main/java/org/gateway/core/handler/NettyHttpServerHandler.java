package org.gateway.core.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;

import org.gateway.common.model.GatewayRequest;
import org.gateway.core.bean.BeanContainer;
import org.gateway.core.codec.GatewayResponseWriter;
import org.gateway.core.filter.FilterChain;

/**
 * 网关主业务 Handler
 * 职责：调用 FilterChain 执行过滤器链，处理请求和响应
 */
@Slf4j
public class NettyHttpServerHandler extends SimpleChannelInboundHandler<GatewayRequest> {

    private final FilterChain filterChain;

    public NettyHttpServerHandler() {
        super();
        this.filterChain = BeanContainer.getBean(FilterChain.class);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, GatewayRequest msg) throws Exception {
        filterChain.execute(msg).whenComplete((response, ex) -> {
            if (ex != null) {
                log.error("请求处理异常: {}", ex.getMessage(), ex);
                response = GatewayResponseWriter.errorResponse(
                        HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error: " + ex.getMessage());
            }
            if (response == null) {
                response = GatewayResponseWriter.errorResponse(
                        HttpResponseStatus.INTERNAL_SERVER_ERROR,
                        "Internal Server Error: No response generated");
            }
            ctx.channel().writeAndFlush(response);
        });
    }
}
