package org.gateway.core.client;

import org.gateway.common.config.GatewayConfig;
import org.gateway.core.bean.Component;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import lombok.extern.slf4j.Slf4j;

/**
 * Redisson 组件包装器
 * 实现 Component 接口以便存入 BeanContainer
 */
@Slf4j
public class RedissonComponent implements Component {

    private final RedissonClient redissonClient;

    public RedissonComponent(GatewayConfig gatewayConfig) {
        this.redissonClient = initRedisson(gatewayConfig);
    }

    /**
     * 获取原生 RedissonClient
     */
    public RedissonClient getClient() {
        return redissonClient;
    }

    /**
     * 初始化逻辑
     */
    private RedissonClient initRedisson(GatewayConfig gatewayConfig) {
        try {
            Config config = new Config();

            // 从配置文件读取
            config.useSingleServer().setAddress("redis://" + gatewayConfig.getRedisAddress() + ":" + gatewayConfig.getRedisPort());
            if (gatewayConfig.getRedisPassword() != null) {
                config.useSingleServer().setPassword(gatewayConfig.getRedisPassword());
            }
            RedissonClient client = Redisson.create(config);

            return client;

        } catch (Exception e) {
            log.warn("Redis连接失败，使用yaml本地路由配置，错误信息：{}", e.getMessage());
            return null;
        }
    }

    /**
     * 资源释放
     */
    public void shutdown() {
        if (redissonClient != null && !redissonClient.isShutdown()) {
            redissonClient.shutdown();
        }
    }
}
