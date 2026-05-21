package org.gateway.common.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.gateway.common.config.RouteConfig;
import org.gateway.common.model.RouteDefinition;
import org.gateway.common.model.ServiceInstance;
import org.junit.jupiter.api.Test;

class YamlUtilTest {

    @Test
    void loadRouteMap() {
        Map<String, RouteDefinition> routes = YamlUtil.loadRouteMap("test-routes.yaml");

        assertEquals(2, routes.size());
        assertTrue(routes.containsKey("user-service"));
        assertTrue(routes.containsKey("order-service"));
    }

    @Test
    void routeFieldsParsedCorrectly() {
        Map<String, RouteDefinition> routes = YamlUtil.loadRouteMap("test-routes.yaml");
        RouteDefinition user = routes.get("user-service");

        assertEquals("user-service", user.getRouteId());
        assertEquals("/api/user/**", user.getPath());
        assertEquals(10, user.getOrder());
        assertEquals(100, user.getLimit());
        assertEquals(50, user.getCircuitBreakerLimit());
        assertTrue(user.isEnabled());
    }

    @Test
    void serviceInstancesParsedCorrectly() {
        Map<String, RouteDefinition> routes = YamlUtil.loadRouteMap("test-routes.yaml");
        RouteDefinition user = routes.get("user-service");

        assertEquals(2, user.getServiceInstances().size());

        ServiceInstance inst1 = user.getServiceInstances().get(0);
        assertEquals("user-1", inst1.getInstanceId());
        assertEquals("user-service", inst1.getServiceId());
        assertEquals("127.0.0.1", inst1.getHost());
        assertEquals(8081, inst1.getPort());
        assertEquals(100, inst1.getWeight());
        assertTrue(inst1.isHealthy());

        ServiceInstance inst2 = user.getServiceInstances().get(1);
        assertEquals("user-2", inst2.getInstanceId());
        assertEquals(8082, inst2.getPort());
        assertEquals(80, inst2.getWeight());
    }

    @Test
    void loadAsGeneric() {
        RouteConfig config = YamlUtil.loadAs("test-routes.yaml", RouteConfig.class);

        assertNotNull(config);
        assertNotNull(config.getRoutes());
        assertEquals(2, config.getRoutes().size());
        assertEquals("order-service", config.getRoutes().get(1).getRouteId());
    }

    @Test
    void loadNonexistentFileThrows() {
        assertThrows(RuntimeException.class, () -> {
            YamlUtil.loadAs("nonexistent.yaml", RouteConfig.class);
        });
    }

    @Test
    void loadRouteMapWithEmptyRoutesReturnsEmptyMap() {
        Map<String, RouteDefinition> routes = YamlUtil.loadRouteMap("empty.yaml");
        assertNotNull(routes);
        assertTrue(routes.isEmpty());
    }
}
