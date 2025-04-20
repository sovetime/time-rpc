package com.yupi.yurpc.fault.retry;

import com.yupi.yurpc.spi.SpiLoader;


//重试策略工厂（用于获取重试器对象）
public class RetryStrategyFactory {

    static {
        SpiLoader.load(RetryStrategy.class);
    }

    //默认重试器
    private static final RetryStrategy DEFAULT_RETRY_STRATEGY = new NoRetryStrategy();

    //获取实例
    public static RetryStrategy getInstance(String key) {
        return SpiLoader.getInstance(RetryStrategy.class, key);
    }

}
