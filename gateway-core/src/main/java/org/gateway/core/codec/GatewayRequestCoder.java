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

        request.setMethod(HttpMethodEnum.valueOf(msg.method().name()));
        
        String[] host = msg.headers().get(HttpHeaderNames.HOST).split(":");
        request.setPort(Integer.parseInt(host[1]));
        request.setHost(host[0]);

        request.setPath(msg.uri());

        request.setUrl(
            (ctx.pipeline().get(SslHandler.class) != null ? "https://" : "http://") +
             request.getHost() + 
             request.getPath()
        );
        
        // 请求参数解析
        String uri = request.getPath();
        int qIdx = uri.indexOf('?');
        Map<String, String> paramMap = new HashMap<>();
        if (qIdx > 0) {
            String queryStr = uri.substring(qIdx + 1);
            String[] kvArr = queryStr.split("&");
            for (String kv : kvArr) {
                int eqIdx = kv.indexOf('=');
                if (eqIdx > 0) {
                    paramMap.put(kv.substring(0, eqIdx), kv.substring(eqIdx + 1));
                }
            }
        }
        request.setQueryParams(paramMap);

        var header = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : msg.headers()) {
            String headKey = entry.getKey();
            String headVal = entry.getValue();
            header.put(headKey, headVal);
        }
        request.setHeaders(header);

        ByteBuf content = msg.content();
        if (content.isReadable()) {
            byte[] body = new byte[content.readableBytes()];
            content.readBytes(body);
            request.setBody(body);
        }

        request.setRemoteAddress(ctx.channel().remoteAddress().toString());

        request.setTimestamp(System.currentTimeMillis());

        request.setRequestId("request-" + UUID.randomUUID().toString());

        log.info("解码完成……");
        ctx.fireChannelRead(request);
    }
}
