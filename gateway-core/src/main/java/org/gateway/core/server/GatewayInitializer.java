package org.gateway.core.server;

import org.gateway.core.Bean.BeanContainer;
import org.gateway.core.codec.GatewayRequestCoder;
import org.gateway.core.codec.GatewayResponseWriter;
import org.gateway.core.config.GatewayConfig;
import org.gateway.core.config.TrustedProxyResolver;
import org.gateway.core.handler.ExceptionCatchHandler;
import org.gateway.core.handler.NettyHttpServerHandler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;

public class GatewayInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        /*
         * Pipeline 顺序设计说明：
         * - 入站方向：从前往后执行
         * - 出站方向：从后往前执行
         * - WriteTimeoutHandler 是出站处理器，需要在 GatewayResponseWriter 之后声明
         * - ExceptionCatchHandler 是入站处理器，捕获所有上游入站异常
         */

        // ===== 超时检测层（入站） =====
        // 空闲连接检测：60s 无读操作则触发 IdleStateEvent
        socketChannel.pipeline().addLast("idle-handler", new IdleStateHandler(60, 0, 0));
        // 读超时：30s 内未收到完整请求则关闭连接
        socketChannel.pipeline().addLast("read-timeout", new ReadTimeoutHandler(30));

        // ===== 日志层（双向） =====
        socketChannel.pipeline().addLast("logging", new LoggingHandler(LogLevel.DEBUG));

        // ===== HTTP 编解码层（双向） =====
        socketChannel.pipeline().addLast("http-codec", new HttpServerCodec());
        socketChannel.pipeline().addLast("http-aggregator",
                new HttpObjectAggregator(BeanContainer.getBean(GatewayConfig.class).getRequestBodyMaxSize()));

        // ===== 业务处理层（入站） =====
        // 自定义请求解码器：FullHttpRequest → GatewayRequest
        socketChannel.pipeline().addLast("gateway-request-coder",
                new GatewayRequestCoder(BeanContainer.getBean(TrustedProxyResolver.class)));
        // 主业务 Handler：路由匹配 + 负载均衡 + 请求转发
        socketChannel.pipeline().addLast("gateway-handler", new NettyHttpServerHandler());
        // 捕获所有未处理入站异常，返回规范错误响应
        socketChannel.pipeline().addLast("exception-handler", new ExceptionCatchHandler());

        // ===== 响应处理层（出站，逆序执行） =====
        // 写超时：30s 内未完成响应写入则关闭连接
        socketChannel.pipeline().addLast("write-timeout", new WriteTimeoutHandler(30));
        // 自定义响应编码器：GatewayResponse → FullHttpResponse
        socketChannel.pipeline().addLast("gateway-response-writer", new GatewayResponseWriter());
    }
}
