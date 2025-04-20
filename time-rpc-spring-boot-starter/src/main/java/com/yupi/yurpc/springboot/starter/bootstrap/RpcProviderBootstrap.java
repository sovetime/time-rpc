package com.yupi.yurpc.springboot.starter.bootstrap;

import com.yupi.yurpc.RpcApplication;
import com.yupi.yurpc.config.RegistryConfig;
import com.yupi.yurpc.config.RpcConfig;
import com.yupi.yurpc.model.ServiceMetaInfo;
import com.yupi.yurpc.registry.LocalRegistry;
import com.yupi.yurpc.registry.Registry;
import com.yupi.yurpc.registry.RegistryFactory;
import com.yupi.yurpc.springboot.starter.annotation.RpcService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;


//Rpc 服务提供者启动
//获取到所有包含@RpcService 注解的类，并且通过注解的属性和反射机制，获取到要注册的服务信息，并且完成服务注册
//利用 Spring 的特性监听 Bean 的加载，实现BeanPostProcessor 接口,实现postProcessAfterInitialization 方法获取到包含@RpcService 注解的类
//Spring 的BeanPostProcessor允许在 Spring Bean 初始化前后执行自定义逻辑
@Slf4j
public class RpcProviderBootstrap implements BeanPostProcessor {

    //Bean 初始化后执行，注册服务
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        //获取当前bean的类
        Class<?> beanClass = bean.getClass();
        //获取当前bean的所有（属性）字段
        RpcService rpcService = beanClass.getAnnotation(RpcService.class);
        if (rpcService != null) {
            // 需要注册服务
            // 1. 获取服务基本信息
            Class<?> interfaceClass = rpcService.interfaceClass();
            // 默认值处理
            if (interfaceClass == void.class) {
                interfaceClass = beanClass.getInterfaces()[0];
            }
            String serviceName = interfaceClass.getName();
            String serviceVersion = rpcService.serviceVersion();
            // 2. 注册服务
            // 本地注册
            LocalRegistry.register(serviceName, beanClass);

            // 全局配置
            final RpcConfig rpcConfig = RpcApplication.getRpcConfig();
            //注册中心配置
            RegistryConfig registryConfig = rpcConfig.getRegistryConfig();
            //获取注册中心实例
            Registry registry = RegistryFactory.getInstance(registryConfig.getRegistry());

            //服务注册
            ServiceMetaInfo serviceMetaInfo = new ServiceMetaInfo();
            serviceMetaInfo.setServiceName(serviceName);
            serviceMetaInfo.setServiceVersion(serviceVersion);
            serviceMetaInfo.setServiceHost(rpcConfig.getServerHost());
            serviceMetaInfo.setServicePort(rpcConfig.getServerPort());
            try {
                registry.register(serviceMetaInfo);
            } catch (Exception e) {
                throw new RuntimeException(serviceName + " 服务注册失败", e);
            }
        }

        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }
}
