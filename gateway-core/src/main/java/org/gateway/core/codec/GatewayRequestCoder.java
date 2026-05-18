package org.gateway.core.codec;

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
        log.info("GatewayRequestCoder解码……");
        GatewayRequest request = new GatewayRequest();
        // 请求方法
        request.setMethod(HttpMethodEnum.valueOf(msg.method().name()));
        
        // 获取Host和Port
        String hostHeader = msg.headers().get(HttpHeaderNames.HOST);
        String host;
        int port;
        if (hostHeader.contains(":")) {
            String[] parts = hostHeader.split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        } else {
            host = hostHeader;
            port = ctx.pipeline().get(SslHandler.class) != null ? 443 : 80;
        }
        request.setPort(port);
        request.setHost(host);


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

        log.info("解码完成……");
        ctx.fireChannelRead(request);
    }
}
