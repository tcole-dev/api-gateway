package org.gateway.common.model;

import java.util.List;

import com.alibaba.fastjson2.JSON;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class RouteDefinition {

    private boolean enabled;

    // 路由服务ID，如 user-service
    private String routeId;

    // 路由匹配规则，如 /user/**
    private String path;

    // 该条路由所选择的“可选过滤器”
    // private List<FilterDefinition> filters;

    // 后端服务实例列表
    private List<ServiceInstance> serviceInstances;

    // 匹配优先级，数值越大优先级越高
    private int order;
 
    // 限流规则，单位：每秒允许的请求数
    private int limit;

    // 熔断阈值，单位：百分比，超过该值则触发熔断
    private int circuitBreakerLimit;

    public static RouteDefinition fromJson(String json) {
        return JSON.parseObject(json, RouteDefinition.class);
    }
}
