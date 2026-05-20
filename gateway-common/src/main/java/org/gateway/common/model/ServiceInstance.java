package org.gateway.common.model;

import lombok.Data;

@Data
public class ServiceInstance {
    // 实例ID
    private String instanceId;
    // 服务ID
    private String serviceId;
    // 主机地址
    private String host;
    // 端口号
    private int port;
    // 权重，数值越大优先被选中
    private int weight;
    // 健康状态，true表示健康，false表示不健康
    private boolean healthy;
}
