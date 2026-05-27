package org.gateway.common.exception;

/**
 * 路由未找到异常（404）
 * 请求路径未匹配到任何路由规则时抛出
 */
public class RouteNotFoundException extends GatewayBusinessException {

    public RouteNotFoundException(String path) {
        super(404, "No route found for path: " + path);
    }
}
