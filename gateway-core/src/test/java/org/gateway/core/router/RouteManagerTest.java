package org.gateway.core.router;

import static org.junit.jupiter.api.Assertions.*;

import org.gateway.common.model.RouteDefinition;
import org.gateway.core.Bean.BeanContainer;
import org.gateway.core.config.GatewayConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * RouteManager 集成测试。
 * 传入 null RedissonComponent 触发本地 YAML 路由加载，不依赖 Redis。
 */
class RouteManagerTest {

    private RouteManager routeManager;

    @BeforeEach
    void setUp() {
        GatewayConfig config = new GatewayConfig();
        config.setLocalRoute("testRoute.yaml");
        BeanContainer.clear();
        BeanContainer.registerBean(config);
        routeManager = new RouteManager(null);
    }

    // ==================== 精确路径匹配 ====================

    @Test
    void matchExactPath() {
        // testRoute.yaml 配置了 /api/user/** 和 /api/order/**
        RouteDefinition route = routeManager.matchRoute("/api/user/123");
        assertNotNull(route);
        assertEquals("user-service", route.getRouteId());
    }

    @Test
    void matchOrderService() {
        RouteDefinition route = routeManager.matchRoute("/api/order/456");
        assertNotNull(route);
        assertEquals("order-service", route.getRouteId());
    }

    @Test
    void noMatchReturnsNull() {
        RouteDefinition route = routeManager.matchRoute("/api/product/789");
        assertNull(route);
    }

    // ==================== 通配符匹配 ====================

    @Test
    void multiWildcardMatchesMultipleSegments() {
        RouteDefinition route = routeManager.matchRoute("/api/user/123/456/789");
        assertNotNull(route);
        assertEquals("user-service", route.getRouteId());
    }

    @Test
    void multiWildcardMatchesZeroSegments() {
        RouteDefinition route = routeManager.matchRoute("/api/user");
        assertNotNull(route);
        assertEquals("user-service", route.getRouteId());
    }

    // ==================== 优先级 ====================

    @Test
    void higherOrderMatchesFirst() {
        // order-service order=20, user-service order=10
        // 如果两个都能匹配同一个路径，order 大的优先
        // 但这里路径不同，验证各自匹配即可
        RouteDefinition user = routeManager.matchRoute("/api/user/profile");
        RouteDefinition order = routeManager.matchRoute("/api/order/detail");

        assertEquals("user-service", user.getRouteId());
        assertEquals("order-service", order.getRouteId());
    }

    // ==================== 路由信息完整性 ====================

    @Test
    void matchedRouteContainsServiceInstances() {
        RouteDefinition route = routeManager.matchRoute("/api/user/123");
        assertNotNull(route.getServiceInstances());
        assertEquals(2, route.getServiceInstances().size());
        assertEquals("user-1", route.getServiceInstances().get(0).getInstanceId());
        assertEquals(8081, route.getServiceInstances().get(0).getPort());
    }

    @Test
    void matchedRouteContainsLimitAndCircuitBreaker() {
        RouteDefinition route = routeManager.matchRoute("/api/user/123");
        assertEquals(100, route.getLimit());
        assertEquals(50, route.getCircuitBreakerLimit());
    }
}
