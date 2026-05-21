package org.gateway.common.utils;

import org.gateway.common.config.RouteConfig;
import org.gateway.common.model.RouteDefinition;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * YAML 解析工具，支持从不同文件解析不同类型。
 */
public class YamlUtil {

    private static final Yaml YAML = new Yaml();

    /**
     * 从 resources 下指定 YAML 文件解析为指定类型。
     *
     * @param fileName resources 目录下的文件名
     * @param clazz    目标类型
     * @return 解析后的对象，文件为空时返回 null
     */
    public static <T> T loadAs(String fileName, Class<T> clazz) {
        try (InputStream is = YamlUtil.class.getClassLoader().getResourceAsStream(fileName)) {
            if (is == null) {
                throw new RuntimeException("配置文件不存在：" + fileName);
            }
            return YAML.loadAs(is, clazz);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析YAML配置失败：" + fileName, e);
        }
    }

    /**
     * 加载路由配置，返回 routeId -> RouteDefinition 的 Map。
     */
    public static Map<String, RouteDefinition> loadRouteMap(String fileName) {
        Map<String, RouteDefinition> routeMap = new HashMap<>();
        RouteConfig config = loadAs(fileName, RouteConfig.class);
        if (config == null || config.getRoutes() == null) {
            return routeMap;
        }
        for (RouteDefinition def : config.getRoutes()) {
            routeMap.put(def.getRouteId(), def);
        }
        return routeMap;
    }
}
