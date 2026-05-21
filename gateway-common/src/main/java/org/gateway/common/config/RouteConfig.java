package org.gateway.common.config;

import java.util.List;

import org.gateway.common.model.RouteDefinition;

import lombok.Data;

@Data
public class RouteConfig {
    private List<RouteDefinition> routes;
}
