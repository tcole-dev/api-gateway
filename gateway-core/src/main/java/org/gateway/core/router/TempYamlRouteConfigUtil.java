package org.gateway.core.router;

import org.gateway.common.model.RouteDefinition;
import org.yaml.snakeyaml.Yaml;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 路由YAML配置解析工具，仅用于测试
 */
public class TempYamlRouteConfigUtil {

    private static final Yaml YAML = new Yaml();

    /**
     * 解析resources下路由yaml，组装成 routeId -> 路由实体 Map
     * @param yamlFileName resources目录下yaml文件名
     * @return Map<routeId, RouteDefinition>
     */
    public static Map<String, RouteDefinition> loadRouteMap(String yamlFileName) {
        Map<String, RouteDefinition> routeMap = new HashMap<>();
        try (InputStream inputStream = TempYamlRouteConfigUtil.class.getClassLoader().getResourceAsStream(yamlFileName)) {
            if (inputStream == null) {
                throw new RuntimeException("路由配置文件不存在：" + yamlFileName);
            }
            // 读取顶层配置
            Map<String, Object> rootMap = YAML.load(inputStream);
            // 获取路由列表
            List<RouteDefinition> routeList = (List<RouteDefinition>) rootMap.get("routes");
            if (routeList == null || routeList.isEmpty()) {
                return routeMap;
            }
            // 转为routeId为key的Map
            for (RouteDefinition definition : routeList) {
                routeMap.put(definition.getRouteId(), definition);
            }
        } catch (Exception e) {
            throw new RuntimeException("解析路由YAML配置失败", e);
        }
        return routeMap;
    }
}