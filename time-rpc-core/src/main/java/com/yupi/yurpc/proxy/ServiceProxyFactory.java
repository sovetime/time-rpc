package com.yupi.yurpc.proxy;

import com.yupi.yurpc.RpcApplication;

import java.lang.reflect.Proxy;


//服务代理工厂（工厂模式，用于创建代理对象）
//工厂模式：封装对象的创建逻辑
public class ServiceProxyFactory {

    //根据服务类获取代理对象
    public static <T> T getProxy(Class<T> serviceClass) {
        //判断是否开启 Mock
        if (RpcApplication.getRpcConfig().isMock()) {
            return getMockProxy(serviceClass);
        }

        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class[]{serviceClass},
                new ServiceProxy());
    }

    //根据服务类获取 Mock 代理对象
    public static <T> T getMockProxy(Class<T> serviceClass) {
        return (T) Proxy.newProxyInstance(
                serviceClass.getClassLoader(),
                new Class[]{serviceClass},
                new MockServiceProxy());
    }
}
