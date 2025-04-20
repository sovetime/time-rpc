package com.yupi.yurpc.serializer;

import com.yupi.yurpc.spi.SpiLoader;


//序列化器工厂（工厂模式，用于获取序列化器对象）
public class SerializerFactory {

    static {
        SpiLoader.load(Serializer.class);
    }

    //在没有找到键对应序列化器时候使用默认的序列化器
    private static final Serializer DEFAULT_SERIALIZER = new JdkSerializer();

    // 获取实例
    public static Serializer getInstance(String key) {
        return SpiLoader.getInstance(Serializer.class, key);
    }

}
