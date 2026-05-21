package org.gateway.core.Bean;

import org.gateway.common.utils.YamlUtil;
import org.gateway.core.config.GatewayConfig;
import org.gateway.core.config.RedissonComponent;
import org.gateway.core.router.RouteManager;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局Bean容器：管理所有单例组件
 * 对外提供静态方法，内部使用单例
 */
public class BeanContainer {

    // 单例容器：存储所有组件实例
    private final Map<Class<?>, Object> beanMap = new HashMap<>();

    // 私有构造，禁止外部实例化
    private BeanContainer() {}

    // ========== 静态内部类单例 ==========
    private static class Holder {
        private static final BeanContainer INSTANCE = new BeanContainer();
    }

    // ========== 对外静态方法 ==========

    /**
     * 注册Bean
     * @param bean 组件实例，不能为空
     */
    public static void registerBean(Object bean) {
        if (bean == null) {
            throw new IllegalArgumentException("Bean cannot be null");
        }
        Holder.INSTANCE.beanMap.put(bean.getClass(), bean);
    }

    /**
     * 获取Bean
     * @param clazz 组件类，不能为空
     * @param <T> 组件类型
     */
    public static <T> T getBean(Class<T> clazz) {
        return (T) Holder.INSTANCE.beanMap.get(clazz);
    }

    /**
     * 清空容器（测试用）
     */
    public static void clear() {
        Holder.INSTANCE.beanMap.clear();
    }

    // ========== 内部初始化 ==========
    private void initBeans() {
        // 1. 加载全局配置
        GatewayConfig gatewayConfig = YamlUtil.loadAs("GatewayConfig.yaml", GatewayConfig.class);
        beanMap.put(GatewayConfig.class, gatewayConfig);

        // 2. 注册 Redisson
        RedissonComponent redissonComponent = new RedissonComponent(gatewayConfig);
        beanMap.put(RedissonComponent.class, redissonComponent);

        // 3. 注册 RouteManager（依赖 Redisson）
        RouteManager routeManager = new RouteManager(redissonComponent);
        beanMap.put(RouteManager.class, routeManager);
    }

    // ========== 对外初始化入口 ==========
    public static void init() {
        Holder.INSTANCE.initBeans();
    }
}