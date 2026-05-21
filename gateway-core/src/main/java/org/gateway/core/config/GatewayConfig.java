package org.gateway.core.config;

import org.gateway.core.Bean.Component;

import lombok.Data;

@Data
public class GatewayConfig implements Component {

    private int nettyPort;

    private String redisAddress;

    private int redisPort;

    private String redisPassword;

    private int requestBodyMaxSize;

    private String localRoute;
}
