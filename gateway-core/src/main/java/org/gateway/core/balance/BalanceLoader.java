package org.gateway.core.balance;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.gateway.common.enums.LoadBalanceStrategy;
import org.gateway.common.model.ServiceInstance;

public class BalanceLoader {
    // 轮询计数使用的索引
    private AtomicInteger index = new AtomicInteger(0);
    // 负载均衡策略
    private LoadBalanceStrategy balanceStrategy;

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
     *
     * @param instances 可用实例列表
     * @return 选择的实例
     */
    private ServiceInstance roundRobin(List<ServiceInstance> instances) { 
        return instances.get((index.getAndIncrement() & 0x7FFFFFFF) % instances.size());
    }

    /**
     * 随机
     *
     * @param instances 可用实例列表
     * @return 选择的实例
     */
    private ServiceInstance random(List<ServiceInstance> instances) { 
        return instances.get((int) (Math.random() * instances.size()));
    }


    /**
     * 平滑加权轮询
     *
     * @param instances 可用实例列表
     * @return 选择的实例
     */
    private ServiceInstance weightRoundRobin(List<ServiceInstance> instances) { 
        int ansIdx = -1;
        int maxWeight = Integer.MIN_VALUE;
        int totalWeight = 0;

        for (int i = 0; i < instances.size(); i++) {
            ServiceInstance instance = instances.get(i);
            // 1. 当前权重增加自身权重
            instance.setCurrentWeight(instance.getCurrentWeight());
            // 2. 累加总权重
            totalWeight += instance.getWeight();
            // 3. 选择当前权重最大的实例
            if (instance.getCurrentWeight() > maxWeight) {
                ansIdx = i;
                maxWeight = instance.getCurrentWeight();
            }
        }
        // 4. 选中的实例当前权重减去总权重
        ServiceInstance ans = instances.get(ansIdx);
        ans.setCurrentWeight(ans.getCurrentWeight() - totalWeight);

        return ans;
    }
    
}