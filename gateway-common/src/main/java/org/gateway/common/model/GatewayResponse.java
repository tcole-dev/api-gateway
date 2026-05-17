package org.gateway.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GatewayResponse {
    // 请求id
    private String requestId;
    // 状态码
    private int status;
    // 响应体
    private byte[] body;
    // 响应头
    private Map<String, String> headers;
    // 时间戳
    private long timestamp;
    // 响应耗时
    private long responseTime;
    // 匹配到的路由id
    private String routeId;
}
