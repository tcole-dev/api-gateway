package org.gateway.common.exception;

/**
 * 服务不可用异常（503）
 * 路由匹配成功但无可用的服务实例时抛出
 */
public class ServiceUnavailableException extends GatewayBusinessException {

    public ServiceUnavailableException(String routeId) {
        super(503, "No available service instance for route: " + routeId);
    }
}
