package org.gateway.core.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gateway.common.model.RouteDefinition;
import org.gateway.common.utils.YamlUtil;
import org.gateway.core.Bean.BeanContainer;
import org.gateway.core.Bean.Component;
import org.gateway.core.config.GatewayConfig;
import org.gateway.core.config.RedissonComponent;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RouteManager implements Component {
    private static final String ROUTE_UPDATE = "ROUTE:UPDATE";
    private static final String ROUTE_LIST = "ROUTE:LIST";

    // 内存中维护的路由表（routeId -> 原始定义）
    private final Map<String, RouteDefinition> routeMap = new HashMap<>();

    // 预编译后的只读快照，按优先级+路径精度排序
    private volatile List<CompiledRoute> compiledRoutes = new ArrayList<>();

    // Redis客户端
    private RedissonClient redissonClient;
    private RTopic routeTopic;


    public RouteManager(RedissonComponent redissonComponent) {
        if (redissonComponent == null || redissonComponent.getClient() == null) {
            loadLocalRoutes();
        } else {
            // 先从本地加载路由，保证即使Redis拉取失败也有路由可用
            loadLocalRoutes();
            this.redissonClient = redissonComponent.getClient();
            this.routeTopic = redissonClient.getTopic(ROUTE_UPDATE);
            updateRoute(); // 启动时订阅路由更新，覆盖本地路由表
        }
    }

    /**
     * 路由表排序 + 预编译。
     * 排序规则：
     * 1. 优先级 order 数值越大优先级越高
     * 2. 路径越精确越靠前，精确段多 > 精确段少 > 含 * > 含 **
     */
    private List<CompiledRoute> routeSort(Map<String, RouteDefinition> routeMap) {
        List<CompiledRoute> list = new ArrayList<>();
        for (RouteDefinition def : routeMap.values()) {
            try {
                list.add(CompiledRoute.compile(def));
            } catch (IllegalArgumentException e) {
                log.warn("跳过无效路由 {}: {}", def.getRouteId(), e.getMessage());
            }
        }

        Collections.sort(list,
            Comparator.comparingInt((CompiledRoute r) -> r.getDefinition().getOrder()).reversed()
                  .thenComparing(this::comparePathPrecision)
        );
        return list;
    }

    /**
     * 比较路径精细度：越精细越靠前
     * 逐段比较：LITERAL(0) > SINGLE(1) > MULTI(2)，数值小的优先
     * @param r1 路由1
     * @param r2 路由2
     * @return r1更精确返回负数，r2更精确返回正数，相同返回0
     */
    private int comparePathPrecision(CompiledRoute r1, CompiledRoute r2) {
        CompiledRoute.Segment[] s1 = r1.getSegments();
        CompiledRoute.Segment[] s2 = r2.getSegments();

        // 1. 段数多的更精确
        if (s1.length != s2.length) {
            return Integer.compare(s2.length, s1.length);
        }

        // 2. 逐段比较类型优先级
        for (int i = 0; i < s1.length; i++) {
            int p1 = segmentPriority(s1[i]);
            int p2 = segmentPriority(s2[i]);
            if (p1 != p2) {
                return Integer.compare(p2, p1);
            }
        }

        // 3. 完全相同 → 按原始路径字符串排序
        return r1.getDefinition().getPath().compareTo(r2.getDefinition().getPath());
    }

    private int segmentPriority(CompiledRoute.Segment seg) {
        return switch (seg.getType()) {
            case LITERAL -> 0;
            case SINGLE  -> 1;
            case MULTI   -> 2;
        };
    }


    // ==================== 路由更新 ====================

    private void updateRoute() {
        updateRouteFromRedis();

        this.routeTopic.addListener(String.class, (channel, message) -> {
            log.info("收到路由更新消息：{}，开始更新路由表", message);
            updateRouteFromRedis();
        });
    }

    private void updateRouteFromRedis() {
        synchronized (this.routeMap) {
            RMap<String, String> redisMap = redissonClient.getMap(ROUTE_LIST);
            Map<String, String> data = redisMap.readAllMap();
            this.routeMap.clear();

            for (Map.Entry<String, String> entry : data.entrySet()) {
                RouteDefinition route = RouteDefinition.fromJson(entry.getValue());
                this.routeMap.put(entry.getKey(), route);
            }

            this.compiledRoutes = routeSort(this.routeMap);
            log.info("路由表已从Redis更新，当前路由数量：{}", this.compiledRoutes.size());
        }
    }

    private void loadLocalRoutes() {
        log.info("开始加载本地路由...");

        Map<String, RouteDefinition> testRoutes = YamlUtil.loadRouteMap(BeanContainer.getBean(GatewayConfig.class).getLocalRoute());

        this.routeMap.clear();
        this.routeMap.putAll(testRoutes);
        this.compiledRoutes = routeSort(this.routeMap);

        log.info("本地路由已加载，当前路由数量：{}", this.compiledRoutes.size());
    }


    // ==================== 路由匹配 ====================
    /**
     * @param path 请求路径，如 /api/user/123
     * @return 匹配到的路由定义，未匹配返回 null
     */
    public RouteDefinition matchRoute(String path) {
        List<CompiledRoute> snapshot = this.compiledRoutes;
        String[] pathSegments = path.split("/");

        for (CompiledRoute compiled : snapshot) {
            if (compiled.match(pathSegments)) {
                return compiled.getDefinition();
            }
        }
        return null;
    }
}
