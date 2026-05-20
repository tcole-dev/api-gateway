package org.gateway.core.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.gateway.common.model.RouteDefinition;
import org.redisson.api.RMap;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class RouteManager {
    private static final String ROUTE_UPDATE = "ROUTE:UPDATE";
    private static final String ROUTE_LIST = "ROUTE:LIST";

    // 内存中维护的路由表
    private final Map<String, RouteDefinition> routeMap = new HashMap<>();

    // 路由表排序后的只读快照
    private List<RouteDefinition> routeSnapShot = new ArrayList<>();

    // Redis客户端，用于分布式锁和拉取路由数据
    private RedissonClient redissonClient;

    // Redis订阅
    private RTopic routeTopic;


    public RouteManager(RedissonClient redissonClient) {

        this.redissonClient = redissonClient;
        this.routeTopic = redissonClient.getTopic(ROUTE_UPDATE);
        
        // updateRoute();   // 实际从Redis拉取数据的方法，订阅路由更新消息
        loadTestRoutes();   // 测试阶段从yaml中拉取测试路由
    }

    /**
     * 路由表排序。
     * 排序规则：
     * 1. 优先级 order 数值越大优先级越高
     * 2. 路径越精确越靠前，如 /user/123 > /user/* > /user/** > /**，同样优先级时生效
     */
    private List<RouteDefinition> routeSort(Map<String, RouteDefinition> routeMap) {
        List<RouteDefinition> routeList = new ArrayList<>(routeMap.values());
        Collections.sort(routeList,
            Comparator.comparingInt(RouteDefinition::getOrder) // 1. 优先级
                  .thenComparing(this::comparePathPrecision) // 2. 路径精细度
        );
        return routeList;
    }

    /**
     * 比较路径精细度：越精细越大，排前面
     */
    private int comparePathPrecision(RouteDefinition r1, RouteDefinition r2) {
        String path1 = r1.getPath();
        String path2 = r2.getPath();

        boolean p1Exact = !path1.contains("*");
        boolean p2Exact = !path2.contains("*");

        // 1. 精确路径 > 通配符路径
        if (p1Exact && !p2Exact) return -1;
        if (!p1Exact && p2Exact) return 1;

        // 2. 都精确 / 都通配 → 按 / 分割的片段数量多的更精细
        String[] segments1 = path1.split("/");
        String[] segments2 = path2.split("/");

        int segmentCompare = Integer.compare(segments2.length, segments1.length);
        if (segmentCompare != 0) {
            return segmentCompare;
        }

        // 3. 片段数相同 → 含 ** 的优先级最低
        boolean p1DoubleWildcard = path1.contains("**");
        boolean p2DoubleWildcard = path2.contains("**");

        if (!p1DoubleWildcard && p2DoubleWildcard) return -1;
        if (p1DoubleWildcard && !p2DoubleWildcard) return 1;

        // 4. 都一样 → 按字符串自然排序
        return path1.compareTo(path2);
    }



    /* 实际从Redis拉取数据的方法 */
    private void updateRoute() {
        this.routeTopic.addListener(String.class, (channel, message) -> {

            log.info("收到路由更新消息：{}", message);

            // 加锁，修改路由表，替换快照
            synchronized(this.routeMap) {
                RMap<String, String> routeMap = redissonClient.getMap(ROUTE_LIST);
                Map<String, String> data = routeMap.readAllMap();
                this.routeMap.clear();

                for (Map.Entry<String, String> entry : data.entrySet()) {
                    RouteDefinition route = RouteDefinition.fromJson(entry.getValue());
                    this.routeMap.put(entry.getKey(), route);
                }

                this.routeSnapShot = routeSort(this.routeMap);

                log.info("路由表已更新，当前路由数量：{}", this.routeSnapShot.size());
            }
        });
    }

    /* 测试阶段从yaml中拉取测试路由 */
    public void loadTestRoutes() {
        Map<String, RouteDefinition> testRoutes = TempYamlRouteConfigUtil.loadRouteMap("testRoute.yaml");
        
        synchronized(this.routeMap) {
            log.info("开始加载测试路由...");

            this.routeMap.clear();
            this.routeMap.putAll(testRoutes);
            this.routeSnapShot = routeSort(this.routeMap);

            log.info("测试路由已加载，当前路由数量：{}", this.routeSnapShot.size());
        }
    }


}
