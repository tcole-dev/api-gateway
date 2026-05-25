package org.gateway.common.enums;

import lombok.Getter;

/**
 * 过滤器执行阶段枚举
 * 定义过滤器在请求生命周期中的执行时机
 */
@Getter
public enum FilterPhase {

    /** 前置过滤器：在请求转发到后端之前执行 */
    PRE("pre"),

    /** 后置过滤器：在收到后端响应后、返回客户端之前执行 */
    POST("post"),

    /** 异常过滤器：在发生异常时执行 */
    ERROR("error");

    private final String phase;

    FilterPhase(String phase) {
        this.phase = phase;
    }
}
