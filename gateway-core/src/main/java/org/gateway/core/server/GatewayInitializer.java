package org.gateway.core.server;

import org.gateway.core.codec.GatewayRequestCoder;
import org.gateway.core.handler.NettyHttpServerHandler;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;

public  class GatewayInitializer extends ChannelInitializer<SocketChannel> {
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        // http请求解析（入站处理器）
        socketChannel.pipeline().addLast("http-coder", new HttpServerCodec());
        // 将解析的请求片封装粘合（入站处理器）
        socketChannel.pipeline().addLast("http-aggregator" ,new HttpObjectAggregator(10 * 1024 * 1024));
        // 自定义请求对象编码器（入站处理器）
        socketChannel.pipeline().addLast("gateway-request-coder", new GatewayRequestCoder());
        // 自定义处理（产生响应对象）
        socketChannel.pipeline().addLast(new NettyHttpServerHandler());
    }
                    
}