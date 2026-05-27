package org.gateway.core.filter.impl;

import java.nio.charset.StandardCharsets;

import org.gateway.common.enums.FilterPhase;
import org.gateway.common.enums.FilterType;
import org.gateway.common.exception.GatewayBusinessException;
import org.gateway.common.model.GatewayRequest;
import org.gateway.common.model.GatewayResponse;
import org.gateway.core.filter.AbstractGatewayFilter;

import lombok.extern.slf4j.Slf4j;

/**
 * 异常处理过滤器
 * 捕获过滤器链中抛出的 GatewayBusinessException，构造对应的错误响应
 * 所有 GatewayBusinessException 子类由此过滤器统一处理
 */
@Slf4j
public class ErrorFilter extends AbstractGatewayFilter {

    public ErrorFilter() {
        super("error-filter", FilterType.ERROR, FilterPhase.ERROR, 0);
    }

    @Override
    public GatewayResponse errorFilter(GatewayRequest request, Throwable ex) {
        if (ex instanceof GatewayBusinessException gex) {
            int status = gex.getStatusCode();
            String message = gex.getMessage();

            log.warn("[{}] 业务异常: status={}, message={}",
                    request.getRequestId(), status, message);

            return buildErrorResponse(request.getRequestId(), status, message);
        }

        // 非 GatewayBusinessException，返回 500
        log.error("[{}] 未预期异常: {}", request.getRequestId(), ex.getMessage(), ex);
        return buildErrorResponse(request.getRequestId(), 500, "Internal Server Error");
    }

    /**
     * 构造 JSON 格式的错误响应
     *
     * 注：如需对特定状态码添加响应头（如 429 的 Retry-After、X-RateLimit-*），
     * 可在 GatewayBusinessException 基类中扩展 getResponseHeaders() 方法，
     * 让子类自定义响应头，此处统一处理。
     */
    private GatewayResponse buildErrorResponse(String requestId, int status, String message) {
        String json = String.format(
                "{\"requestId\":\"%s\",\"status\":%d,\"message\":\"%s\"}",
                requestId, status, escapeJson(message));

        return GatewayResponse.builder()
                .requestId(requestId)
                .status(status)
                .body(json.getBytes(StandardCharsets.UTF_8))
                .build();
    }

    /**
     * 简单 JSON 转义（处理引号和换行）
     */
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
