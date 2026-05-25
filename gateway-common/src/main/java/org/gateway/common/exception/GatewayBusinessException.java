package org.gateway.common.exception;

import lombok.Getter;

/**
 * 网关业务异常基类
 * FilterChain 中的业务异常都应继承此类
 * 由 ErrorFilter 统一处理，ExceptionCatchHandler 会忽略此类异常
 */
@Getter
public class GatewayBusinessException extends RuntimeException {

    private final int statusCode;

    public GatewayBusinessException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public GatewayBusinessException(int statusCode, String message, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }
}
