package org.gateway.core.codec;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.gateway.common.enums.HttpMethodEnum;
import org.gateway.common.model.GatewayRequest;

import io.netty.buffer.ByteBuf;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GatewayRequestCoder extends SimpleChannelInboundHandler<FullHttpRequest> {
    @Override
    protected void channelRead0(io.netty.channel.ChannelHandlerContext ctx, FullHttpRequest msg) throws Exception {
        GatewayRequest request = new GatewayRequest();

        // 请求方法
        request.setMethod(HttpMethodEnum.valueOf(msg.method().name()));

        // 路径和查询参数解析
        String originUri = msg.uri();
        int qIdx = originUri.indexOf('?');
        if (qIdx > 0) {
            request.setPath(originUri.substring(0, qIdx));
            String params = originUri.substring(qIdx + 1);
            var queryParams = new HashMap<String, String>();
            for (String kv : params.split("&")) {
                int eqIdx = kv.indexOf('=');
                if (eqIdx > 0) {
                    queryParams.put(kv.substring(0, eqIdx), kv.substring(eqIdx + 1));
                }
            }
            request.setQueryParams(queryParams);
        } else {
            request.setPath(originUri);
        }

        // 请求头解析
        var header = new HashMap<String, String>();
        for (Map.Entry<String, String> entry : msg.headers()) {
            header.put(entry.getKey(), entry.getValue());
        }
        request.setHeaders(header);

        // 请求体解析
        ByteBuf content = msg.content();
        if (content.isReadable()) {
            byte[] body = new byte[content.readableBytes()];
            // int readerIndex = content.readerIndex();
            content.readBytes(body);
            // content.readerIndex(readerIndex);
            request.setBody(body);
        }

        // 请求来源
        request.setRemoteAddress(ctx.channel().remoteAddress().toString());
        // 请求时间戳
        request.setTimestamp(System.currentTimeMillis());
        // 请求ID
        request.setRequestId("request-" + UUID.randomUUID());

        ctx.fireChannelRead(request);
    }
}
