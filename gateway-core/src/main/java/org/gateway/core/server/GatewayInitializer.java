package org.gateway.core.server;

import org.gateway.core.Bean.BeanContainer;
import org.gateway.core.codec.GatewayRequestCoder;
import org.gateway.core.codec.GatewayResponseWriter;
import org.gateway.core.config.GatewayConfig;
import org.gateway.core.handler.NettyHttpServerHandler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.timeout.IdleStateHandler;

public  class GatewayInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        // 日志处理器
        socketChannel.pipeline().addLast(new LoggingHandler(LogLevel.INFO));
        // http请求解析（入站处理器）
        socketChannel.pipeline().addLast("http-coder", new HttpServerCodec());
        // 将解析的请求片封装粘合（入站处理器）
        socketChannel.pipeline().addLast("http-aggregator" ,new HttpObjectAggregator(BeanContainer.getBean(GatewayConfig.class).getRequestBodyMaxSize()));
        // 空闲连接检测
        socketChannel.pipeline().addLast(new IdleStateHandler(60, 0, 0));
        // 自定义响应对象编码器（出站处理器）
        socketChannel.pipeline().addLast(new GatewayResponseWriter());
        // 自定义请求对象编码器（入站处理器）
        socketChannel.pipeline().addLast("gateway-request-coder", new GatewayRequestCoder());
        // 自定义处理（产生响应对象）
        socketChannel.pipeline().addLast(new NettyHttpServerHandler());
    }
                    
}