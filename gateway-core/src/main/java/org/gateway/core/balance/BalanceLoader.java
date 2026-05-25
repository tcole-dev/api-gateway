package org.gateway.core.balance;

import java.util.HashMap;
import java.util.List;

import org.gateway.common.enums.LoadBalanceStrategy;
import org.gateway.common.model.ServiceInstance;
import org.gateway.core.bean.Component;
import java.util.concurrent.atomic.AtomicInteger;

public class BalanceLoader implements Component{
    // 轮询计数使用的索引
    private AtomicInteger index = new AtomicInteger(0);
    // 负载均衡策略
    private LoadBalanceStrategy balanceStrategy;
    // 平滑加权轮询的权重状态（instanceId → 当前权重）
    private final HashMap<String, Integer> weightState = new HashMap<>();

    public BalanceLoader(LoadBalanceStrategy balanceStrategy) {
        this.balanceStrategy = balanceStrategy;
    }


    /**
     * 根据负载均衡策略选择实例
     *
     * @param instances 可用实例列表
     * @return 选择的实例
     */
    public ServiceInstance select(List<ServiceInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return null;
        }

        List<ServiceInstance> healthy = instances.stream()
            .filter(ServiceInstance::isHealthy)
            .toList();

        if (healthy.isEmpty()) {
            return null;
        }

        switch (balanceStrategy) {
            case ROUND_ROBIN:
                return roundRobin(healthy);
            case RANDOM:
                return random(healthy);
            case WEIGHT_ROUND_ROBIN:
                return weightRoundRobin(healthy);
            default:
                return roundRobin(healthy);
        }
    }


    /**
     * 轮询
     */
    private ServiceInstance roundRobin(List<ServiceInstance> instances) {
        return instances.get((index.getAndIncrement() & 0x7FFFFFFF) % instances.size());
    }

    /**
     * 随机
     */
    private ServiceInstance random(List<ServiceInstance> instances) {
        return instances.get((int) (Math.random() * instances.size()));
    }


    /**
     * 平滑加权轮询
     */
    private ServiceInstance weightRoundRobin(List<ServiceInstance> instances) {
        int ansIdx = -1;
        int maxWeight = Integer.MIN_VALUE;
        int totalWeight = 0;

        synchronized (weightState) {
            for (int i = 0; i < instances.size(); i++) {
                ServiceInstance instance = instances.get(i);
                // 1. 当前权重增加自身权重
                int cw = weightState.merge(instance.getInstanceId(), instance.getWeight(), Integer::sum);
                // 2. 累加总权重
                totalWeight += instance.getWeight();
                // 3. 选择当前权重最大的实例
                if (cw > maxWeight) {
                    ansIdx = i;
                    maxWeight = cw;
                }
            }
            // 4. 选中的实例当前权重减去总权重
            ServiceInstance ans = instances.get(ansIdx);
            weightState.merge(ans.getInstanceId(), -totalWeight, Integer::sum);
            return ans;
        }
    }

    /**
     * 路由更新时重置权重状态，避免旧实例残留脏数据
     */
    public void resetWeightState() {
        synchronized (weightState) {
            weightState.clear();
        }
    }

}
