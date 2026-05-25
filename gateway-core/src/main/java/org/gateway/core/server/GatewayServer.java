package org.gateway.core.server;


import org.gateway.common.config.GatewayConfig;
import org.gateway.core.bean.BeanContainer;
import org.gateway.core.client.HttpClient;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayServer {

    private final int port;
    private final EventLoopGroup workerGroup;

    public GatewayServer(int port, EventLoopGroup workerGroup) {
        this.port = port;
        this.workerGroup = workerGroup;
    }

    public void run () {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);

        try {
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
        // 先创建 workerGroup 并注册 HttpClient（ProxyService 构造时依赖）
        EventLoopGroup workerGroup = new NioEventLoopGroup();
        HttpClient.register(workerGroup);

        BeanContainer.init();
        new GatewayServer(BeanContainer.getBean(GatewayConfig.class).getNettyPort(), workerGroup).run();
    }
}
