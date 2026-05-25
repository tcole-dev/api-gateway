package org.gateway.common.config;

import java.util.List;

import lombok.Data;

@Data
public class GatewayConfig {

    private int nettyPort;

    private String redisAddress;

    private int redisPort;

    private String redisPassword;

    private int requestBodyMaxSize;

    private String localRoute;

    private List<String> trustedProxies;
}
