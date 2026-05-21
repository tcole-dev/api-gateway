package org.gateway.core.codec;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.gateway.common.enums.HttpMethodEnum;
import org.gateway.common.model.GatewayRequest;

import io.netty.buffer.ByteBuf;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.ssl.SslHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayRequestCoder extends SimpleChannelInboundHandler<FullHttpRequest> {
    @Override
    protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        // log.info("GatewayRequestCoder解码……");
        GatewayRequest request = new GatewayRequest();
        // 请求方法
        request.setMethod(HttpMethodEnum.valueOf(msg.method().name()));
        
        // 获取Host和Port
        String hostHeader = msg.headers().get(HttpHeaderNames.HOST);
        String host;
        int port;

        if (hostHeader != null && !hostHeader.isBlank()) {
            // 有 Host 头：正常解析
            int lastColon = hostHeader.lastIndexOf(':');
            // 处理 IPv6 [::1]:8080 格式
            if (lastColon > 0 && (!hostHeader.contains("]") || lastColon > hostHeader.indexOf(']'))) {
                host = hostHeader.substring(0, lastColon);
                port = Integer.parseInt(hostHeader.substring(lastColon + 1));
            } else {
                host = hostHeader;
                // 根据是否启用 SSL 判断默认端口
                port = ctx.pipeline().get(SslHandler.class) != null ? 443 : 80;
            }
        } else {
            // ====================== 关键：没有 Host 头，从请求URI解析 ======================
            String uriStr = msg.uri();
            URI uri = URI.create(uriStr); // 把 String 转成 URI 对象解析
            
            host = uri.getHost();
            port = uri.getPort();

            // 没有端口 → 使用默认端口
            if (port == -1) {
                port = ctx.pipeline().get(SslHandler.class) != null ? 443 : 80;
            }
        }

        request.setHost(host);
        request.setPort(port);


        String originUri = msg.uri();
        int qIdx = originUri.indexOf('?');
        if (qIdx > 0) {
            // 请求路径
            request.setPath(originUri.substring(0, qIdx));

            // 请求参数解析
            String params = originUri.substring(qIdx + 1);
            var queryParams = new HashMap<String, String>();
            String[] kvArr = params.split("&");
            for (String kv : kvArr) {
                int eqIdx = kv.indexOf('=');
                if (eqIdx > 0) {
                    queryParams.put(kv.substring(0, eqIdx), kv.substring(eqIdx + 1));
                }
            }
            request.setQueryParams(queryParams);
        } else {
            request.setPath(originUri);
        }

        // 完整URL构建
        request.setUrl(
            (ctx.pipeline().get(SslHandler.class) != null ? "https://" : "http://") +
             request.getHost() + ":" + 
             request.getPort() +
             request.getPath()
        );
        

        // 请求头解析
        var header = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : msg.headers()) {
            String headKey = entry.getKey();
            String headVal = entry.getValue();
            header.put(headKey, headVal);
        }
        request.setHeaders(header);


        // 请求体解析
        ByteBuf content = msg.content();
        if (content.isReadable()) {
            byte[] body = new byte[content.readableBytes()];
            content.readBytes(body);
            request.setBody(body);
        }

        // 请求来源
        request.setRemoteAddress(ctx.channel().remoteAddress().toString());

        // 请求时间戳
        request.setTimestamp(System.currentTimeMillis());

        // 请求ID生成
        request.setRequestId("request-" + UUID.randomUUID().toString());

        // log.info("解码完成……");
        ctx.fireChannelRead(request);
    }
}
