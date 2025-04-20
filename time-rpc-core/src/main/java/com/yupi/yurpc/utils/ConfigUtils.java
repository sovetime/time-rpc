package com.yupi.yurpc.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.setting.dialect.Props;


//用于读取配置文件并返回对象
public class ConfigUtils {

    //加载配置对象
    public static <T> T loadConfig(Class<T> tClass, String prefix) {
        return loadConfig(tClass, prefix, "");
    }

    //加载配置对象，可以支持application-{environment}.properties配置的读取
    public static <T> T loadConfig(Class<T> tClass, String prefix, String environment) {
        StringBuilder configFileBuilder = new StringBuilder("application");
        //环境变量存在时，添加环境变量
        if (StrUtil.isNotBlank(environment)) {
            configFileBuilder.append("-").append(environment);
        }
        configFileBuilder.append(".properties");

        //读取配置文件
        Props props = new Props(configFileBuilder.toString());
        return props.toBean(tClass, prefix);
    }
}
