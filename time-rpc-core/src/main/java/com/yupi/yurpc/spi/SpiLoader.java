package com.yupi.yurpc.spi;

import cn.hutool.core.io.resource.ResourceUtil;
import com.yupi.yurpc.serializer.Serializer;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


//SPI 加载器
// 自定义实现，支持键值对映射
@Slf4j
public class SpiLoader {

    //存储已加载的类
    private static final Map<String, Map<String, Class<?>>> loaderMap = new ConcurrentHashMap<>();

    //对象实例缓存（避免重复 new）
    private static final Map<String, Object> instanceCache = new ConcurrentHashMap<>();

    //系统 SPI 目录
    private static final String RPC_SYSTEM_SPI_DIR = "META-INF/rpc/system/";

    //用户自定义 SPI 目录
    private static final String RPC_CUSTOM_SPI_DIR = "META-INF/rpc/custom/";

    //扫描路径
    private static final String[] SCAN_DIRS = new String[]{RPC_SYSTEM_SPI_DIR, RPC_CUSTOM_SPI_DIR};

    //动态加载的类列表，Class<?>可以匹配任何类型的.class对象
    private static final List<Class<?>> LOAD_CLASS_LIST = Arrays.asList(Serializer.class);



    //获取接口实例，将对象实例缓存，key这里指的是SPI配置中的key
    //示例 jdk=com.yupi.yurpc.serializer.JdkSerializer key是jdk
    public static <T> T getInstance(Class<?> tClass, String key) {
        //获取加载过的类对象
        String tClassName = tClass.getName();
        Map<String, Class<?>> keyClassMap = loaderMap.get(tClassName);
        if (keyClassMap == null) {
            throw new RuntimeException(String.format("SpiLoader 未加载 %s 类型", tClassName));
        }
        if (!keyClassMap.containsKey(key)) {
            throw new RuntimeException(String.format("SpiLoader 的 %s 不存在 key=%s 的类型", tClassName, key));
        }

        // 获取要加载的类
        Class<?> implClass = keyClassMap.get(key);
        String implClassName = implClass.getName();
        // 从实例缓存中加载指定类型的实例，没有就将示例加入缓存
        if (!instanceCache.containsKey(implClassName)) {
            try {
                instanceCache.put(implClassName, implClass.newInstance());
            } catch (InstantiationException | IllegalAccessException e) {
                String errorMsg = String.format("%s 类实例化失败", implClassName);
                throw new RuntimeException(errorMsg, e);
            }
        }
        return (T) instanceCache.get(implClassName);
    }


    //加载相对应的类型
    public static Map<String, Class<?>> load(Class<?> loadClass) {
        log.info("加载类型为 {} 的 SPI", loadClass.getName());

        // 扫描路径，用户自定义的SPI 优先级高于系统 SPI
        Map<String, Class<?>> keyClassMap = new HashMap<>();
        for (String scanDir : SCAN_DIRS) {
            // 获取资源文件，示例scanDir+loadClass.getName()->META-INF/rpc/system/jdk
            List<URL> resources = ResourceUtil.getResources(scanDir + loadClass.getName());
            // 读取每个资源文件
            for (URL resource : resources) {
                try {
                    InputStreamReader inputStreamReader = new InputStreamReader(resource.openStream());
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                    String line;
                    //按行读取
                    while ((line = bufferedReader.readLine()) != null) {
                        //以=作分隔符,获取key和value,在存储到map中
                        String[] strArray = line.split("=");
                        if (strArray.length > 1) {
                            String key = strArray[0];
                            String className = strArray[1];
                            keyClassMap.put(key, Class.forName(className));
                        }
                    }
                } catch (Exception e) {
                    log.error("spi resource load error", e);
                }
            }
        }
        loaderMap.put(loadClass.getName(), keyClassMap);
        return keyClassMap;
    }

//    //加载所有类型
//    public static void loadAll() {
//        log.info("加载所有 SPI");
//        for (Class<?> aClass : LOAD_CLASS_LIST) {
//            load(aClass);
//        }
//    }
//
//    public static void main(String[] args) throws IOException, ClassNotFoundException {
//        loadAll();
//        System.out.println(loaderMap);
//        Serializer serializer = getInstance(Serializer.class, "jdk");
//        System.out.println(serializer);
//    }

}
