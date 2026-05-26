package org.gateway.core.client;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.AsyncHttpClientConfig;
import org.asynchttpclient.BoundRequestBuilder;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClientConfig;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.gateway.common.model.GatewayRequest;
import org.gateway.common.model.GatewayResponse;
import org.gateway.core.bean.BeanContainer;
import org.gateway.core.bean.Component;

import io.netty.channel.EventLoopGroup;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class HttpClient implements Component {
    private AsyncHttpClient client;

    private HttpClient(EventLoopGroup group) {
        if (group == null) {
            throw new IllegalArgumentException("EventLoopGroup cannot be null");
        }
        // 构建可复用的配置
        AsyncHttpClientConfig config = new DefaultAsyncHttpClientConfig.Builder()
            .setEventLoopGroup(group) // 核心：复用线程池
            .setConnectTimeout(5000)      // 连接超时 5s
            .setReadTimeout(30000)        // 读超时 30s，与 ReadTimeoutHandler 对齐
            .setMaxConnections(500)
            .setMaxConnectionsPerHost(100)
            .setKeepAlive(true)
            .setPooledConnectionIdleTimeout(60000) // 空闲连接 60s 回收
            .build();
        this.client = new DefaultAsyncHttpClient(config);

        BeanContainer.registerBean(this);

        log.info("Http客户端初始化完成");
    }


    public CompletableFuture<GatewayResponse> forwardRequest(GatewayRequest request, String toUrl) {
        BoundRequestBuilder builder = client.prepare(request.getMethod().name(), toUrl);

        // Header
        if (request.getHeaders() != null) {
            request.getHeaders()
                    .forEach(builder::addHeader);
        }

        // QueryParam
        if (request.getQueryParams() != null) {
            request.getQueryParams()
                    .forEach(builder::addQueryParam);
        }

        // Body
        if (request.getBody() != null
                && request.getBody().length > 0) {
            builder.setBody(request.getBody());
        }

        CompletableFuture<GatewayResponse> cf = new CompletableFuture<>();
        ListenableFuture<Response> future = builder.execute();

        // 异步回调：在当前EventLoop线程执行，无线程切换
        future.addListener(() -> {
            try {
                Response response = future.get();
                GatewayResponse gatewayResponse = GatewayResponse.builder()
                        .requestId(request.getRequestId())
                        .status(response.getStatusCode())
                        .headers(response.getHeaders().entries().stream()
                                .collect(java.util.stream.Collectors.toMap(
                                        Map.Entry::getKey,
                                        Map.Entry::getValue,
                                        (v1, v2) -> v2)))
                        .body(response.getResponseBodyAsBytes())
                        .build();
                cf.complete(gatewayResponse);
            } catch (Exception e) {
                cf.completeExceptionally(e);
            }
        }, Runnable::run);

        return cf;
    }


    public static void register(EventLoopGroup group) {
        new HttpClient(group);
    }

    public AsyncHttpClient getClient() {
        return client;
    }

    /**
     * 关闭 HTTP 客户端，释放相关资源
     */
    public static void closeClient() {
        // 获取已注册的 HttpClient 实例
        HttpClient httpClient = BeanContainer.getBean(HttpClient.class);
        if (httpClient != null && httpClient.client != null) {
            try {
                httpClient.client.close();
                log.info("Http客户端已关闭");
            } catch (Exception e) {
                log.error("关闭Http客户端时发生异常", e);
            }
        } else {
            log.warn("Http客户端未初始化或已关闭");
        }
    }
}
