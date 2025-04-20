package com.yupi.yurpc.fault.tolerant;

import com.yupi.yurpc.model.RpcResponse;

import java.util.Map;


//容错策略
public interface TolerantStrategy {

    //容错
    RpcResponse doTolerant(Map<String, Object> context, Exception e);
}
