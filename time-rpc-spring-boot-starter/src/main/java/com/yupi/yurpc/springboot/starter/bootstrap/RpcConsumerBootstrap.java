package com.yupi.yurpc.springboot.starter.bootstrap;

import com.yupi.yurpc.proxy.ServiceProxyFactory;
import com.yupi.yurpc.springboot.starter.annotation.RpcReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import java.lang.reflect.Field;


//Rpc 服务消费者启动
//为 RPC 服务的消费者（即调用方）生成代理对象，并将其注入到 Spring Bean 中
@Slf4j
public class RpcConsumerBootstrap implements BeanPostProcessor {

    //Bean 初始化后执行，注入服务
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        //获取当前bean的类
        Class<?> beanClass = bean.getClass();
        //获取当前bean的所有（属性）字段
        Field[] declaredFields = beanClass.getDeclaredFields();
        //遍历所有字段
        for (Field field : declaredFields) {
            //判断当前字段是否被 RpcReference 注解标注
            RpcReference rpcReference = field.getAnnotation(RpcReference.class);
            if (rpcReference != null) {
                // 为属性生成代理对象
                Class<?> interfaceClass = rpcReference.interfaceClass();
                if (interfaceClass == void.class) {
                    interfaceClass = field.getType();
                }
                field.setAccessible(true);
                Object proxyObject = ServiceProxyFactory.getProxy(interfaceClass);
                try {
                    field.set(bean, proxyObject);
                    field.setAccessible(false);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("为字段注入代理对象失败", e);
                }
            }
        }
        return BeanPostProcessor.super.postProcessAfterInitialization(bean, beanName);
    }

}
