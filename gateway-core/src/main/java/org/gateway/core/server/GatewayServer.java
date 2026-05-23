package org.gateway.core.server;


import org.gateway.core.Bean.BeanContainer;
import org.gateway.core.Bean.HttpClient;
import org.gateway.core.config.GatewayConfig;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayServer {

    private final int port;

    public GatewayServer(int port) {
        this.port = port;
    }

    public void run () {
        // Netty 4.2.5.Final 写法
        // EventLoopGroup bossGroup = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
        // EventLoopGroup workerGroup = new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());

        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            HttpClient.register(workerGroup); // 依赖注入 HttpClient

            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    // Channel类型
                    .channel(NioServerSocketChannel.class)
                    // 连接队列大小
                    .option(ChannelOption.SO_BACKLOG, 128)
                    // 保持长连接
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new GatewayInitializer());
            // 启动
            ChannelFuture future = bootstrap.bind(port).sync();

            log.info("==============================================");
            log.info("网关开启在端口： {}", port);
            log.info("==============================================");

            // 阻塞监听，直到关闭
            future.channel().closeFuture().sync();

        } catch (InterruptedException e) {
            log.error("网关服务器启动失败 {}", e.getMessage());
            Thread.currentThread().interrupt();

        } finally {
            log.info("==============================================");
            log.info("网关服务器关闭");
            log.info("==============================================");
            // 优雅关闭
            try {
                HttpClient.closeClient(); // 优雅关闭 HttpClient

                bossGroup.shutdownGracefully().sync();
                workerGroup.shutdownGracefully().sync();
            } catch (InterruptedException e) {
                log.error("网关服务器关闭失败 {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        BeanContainer.init();
        new GatewayServer(BeanContainer.getBean(GatewayConfig.class).getNettyPort()).run();
    }
}
