package com.yupi.yurpc.fault.retry;

import com.github.rholder.retry.*;
import com.yupi.yurpc.model.RpcResponse;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;


//固定时间间隔 - 重试策略
@Slf4j
public class FixedIntervalRetryStrategy implements RetryStrategy {

    //重试
    //使用 Guava-Retrying 提供的 RetryerBuilder 指定重试条件、重试等待策略、重试停止策略、监听重试
    public RpcResponse doRetry(Callable<RpcResponse> callable) throws ExecutionException, RetryException {
        Retryer<RpcResponse> retryer = RetryerBuilder.<RpcResponse>newBuilder()
                .retryIfExceptionOfType(Exception.class)//重试条件，遇到Exception时重试
                .withWaitStrategy(WaitStrategies.fixedWait(3L, TimeUnit.SECONDS))//重试时间间间隔
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))//重试停止策略
                .withRetryListener(new RetryListener() {//监听重试
                    @Override
                    public <V> void onRetry(Attempt<V> attempt) {
                        log.info("重试次数 {}", attempt.getAttemptNumber());
                    }
                })
                .build();

        return retryer.call(callable);
    }
}
