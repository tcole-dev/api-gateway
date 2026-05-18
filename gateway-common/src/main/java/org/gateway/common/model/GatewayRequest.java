package org.gateway.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

import org.gateway.common.enums.HttpMethodEnum;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class GatewayRequest {
    // 请求ID
    private String requestId;
    // 请求方法
    private HttpMethodEnum method;
    // 主机
    private String host;
    // 端口
    private int port;
    // 请求URL
    private String url;
    // 请求路径
    private String path;
    // 请求参数
    private Map<String, String> queryParams;
    // 请求头
    private Map<String, String> headers;
    // 请求体
    private byte[] body;
    // 远程地址
    private String remoteAddress;
    // 时间戳
    private long timestamp;
    // 上下文
    private Map<String, Object> attributes;

    /**
     * 设置上下文
     * @param key key
     * @param attribute val
     * @param <T> 泛型
     */
    public <T> void setAttribute(String key, T attribute) {
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        attributes.put(key, attribute);
    }

    /**
     * 获取上下文
     * @param key key
     * @param <T> 泛型
     * @return val
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        if (attributes == null) {
            return null;
        }
        return (T) attributes.get(key);
    }
}
