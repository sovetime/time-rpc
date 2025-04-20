package com.yupi.yurpc.registry;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.cron.CronUtil;
import cn.hutool.cron.task.Task;
import cn.hutool.json.JSONUtil;
import com.yupi.yurpc.config.RegistryConfig;
import com.yupi.yurpc.model.ServiceMetaInfo;
import io.etcd.jetcd.*;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.PutOption;
import io.etcd.jetcd.watch.WatchEvent;
import lombok.SneakyThrows;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


//Etcd 注册中心
public class EtcdRegistry implements Registry {

    private Client client;

    private KV kvClient;

    //本机注册的节点 key 集合（用于维护续期）
    private final Set<String> localRegisterNodeKeySet = new HashSet<>();

    //注册中心服务缓存
    @Deprecated//标记已废弃
    private final RegistryServiceCache registryServiceCache = new RegistryServiceCache();

    //注册中心服务多级缓存
    private final RegistryServiceMultiCache registryServiceMultiCache = new RegistryServiceMultiCache();

    //正在监听的 key 集合
    private final Set<String> watchingKeySet = new ConcurrentHashSet<>();

    //根节点
    private static final String ETCD_ROOT_PATH = "/rpc/";

    @Override
    public void init(RegistryConfig registryConfig) {
        //构建 client
        client = Client.builder()
                .endpoints(registryConfig.getAddress())//连接到注册中心地址
                .connectTimeout(Duration.ofMillis(registryConfig.getTimeout()))//设置连接超时时间
                .build();

        //创建一个 KV 客户端
        kvClient = client.getKVClient();
        //开启心跳
        heartBeat();
    }

    @Override
    public void register(ServiceMetaInfo serviceMetaInfo) throws Exception {
        // 创建 Lease客户端
        Lease leaseClient = client.getLeaseClient();

        // 创建一个 30 秒的租约
        long leaseId = leaseClient.grant(30).get().getID();

        // 构造节点 key，/rpc/service/version/host:port
        String registerKey = ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
        //将这些字符串转换为 ByteSequence类型，键值对存储需要字节序列作为输入
        ByteSequence key = ByteSequence.from(registerKey, StandardCharsets.UTF_8);
        ByteSequence value = ByteSequence.from(JSONUtil.toJsonStr(serviceMetaInfo), StandardCharsets.UTF_8);

        // 将键值对与租约关联起来，并设置过期时间
        PutOption putOption = PutOption.builder().withLeaseId(leaseId).build();
        kvClient.put(key, value, putOption).get();
        // 添加节点key到注册节点集合
        localRegisterNodeKeySet.add(registerKey);
    }

    @SneakyThrows
    @Override
    public void unRegister(ServiceMetaInfo serviceMetaInfo) {
        //构造节点 key
        String registerKey = ETCD_ROOT_PATH + serviceMetaInfo.getServiceNodeKey();
        kvClient.delete(ByteSequence.from(registerKey, StandardCharsets.UTF_8)).get();
        //从节点集合中移除
        localRegisterNodeKeySet.remove(registerKey);
    }

    //服务发现，因为是根据前缀查询的keyValues，解析服务是会出现多个服务情况的这里采用List<ServiceMetaInfo>维护
    @Override
    public List<ServiceMetaInfo> serviceDiscovery(String serviceKey) {
        // 优先从缓存获取服务
        List<ServiceMetaInfo> cachedServiceMetaInfoList = registryServiceMultiCache.readCache(serviceKey);
        if (cachedServiceMetaInfoList != null) {
            return cachedServiceMetaInfoList;
        }

        String searchPrefix = ETCD_ROOT_PATH + serviceKey;
        try {
            //开启前缀查询
            GetOption getOption = GetOption.builder().isPrefix(true).build();
            //查询匹配的key
            List<KeyValue> keyValues = kvClient.get(ByteSequence.from(searchPrefix, StandardCharsets.UTF_8), getOption)
                    .get()
                    .getKvs();//方法返回一个List<KeyValue>列表

            //解析服务信息
            List<ServiceMetaInfo> serviceMetaInfoList = keyValues.stream().map(keyValue -> {
                    //获取key
                    String key = keyValue.getKey().toString(StandardCharsets.UTF_8);
                    //监听key
                    watch(key);
                    //获取value
                    String value = keyValue.getValue().toString(StandardCharsets.UTF_8);
                    return JSONUtil.toBean(value, ServiceMetaInfo.class);
            }).collect(Collectors.toList());

            // 写入服务缓存
            registryServiceMultiCache.writeCache(serviceKey,serviceMetaInfoList);
            return serviceMetaInfoList;
        } catch (Exception e) {
            throw new RuntimeException("获取服务列表失败", e);
        }
    }

    //心跳，对节点进行续约，key是单独的，直接获取keyValues的第一个元素就可以了
    @Override
    public void heartBeat() {
        // 10 秒续签一次
        CronUtil.schedule("*/10 * * * * *", new Task() {
            @Override
            public void execute() {
                // 遍历本节点所有的 key
                for (String key : localRegisterNodeKeySet) {
                    try {
                        // 获取 key 的值
                        List<KeyValue> keyValues = kvClient.get(ByteSequence.from(key, StandardCharsets.UTF_8))
                                .get()//阻塞当前线程，直到获取到响应结果，如果响应结果为空，则抛出异常
                                .getKvs();//方法返回一个List<KeyValue>列表

                        // 该节点已过期（需要重启节点才能重新注册），不走后续延期逻辑
                        if (CollUtil.isEmpty(keyValues)) {
                            continue;
                        }

                        KeyValue keyValue = keyValues.get(0);
                        //获取键值对中的值将其转换成字符串
                        String value = keyValue.getValue().toString(StandardCharsets.UTF_8);
                        //将字符串反序列化为ServiceMetaInfo对象
                        ServiceMetaInfo serviceMetaInfo = JSONUtil.toBean(value, ServiceMetaInfo.class);
                        register(serviceMetaInfo);
                    } catch (Exception e) {
                        throw new RuntimeException(key + "续签失败", e);
                    }
                }
            }
        });

        // 支持秒级别定时任务
        CronUtil.setMatchSecond(true);
        CronUtil.start();
    }

    //监听（消费端）
    @Override
    public void watch(String serviceNodeKey) {
        // 创建 Watch 客户端
        Watch watchClient = client.getWatchClient();
        // 之前未被监听，开启监听
        boolean newWatch = watchingKeySet.add(serviceNodeKey);
        if (newWatch) {
            watchClient.watch(ByteSequence.from(serviceNodeKey, StandardCharsets.UTF_8), response -> {
                for (WatchEvent event : response.getEvents()) {
                    //判断事件的类型event.getEventType()
                    switch (event.getEventType()) {
                        // key 删除时触发
                        case DELETE:
                            // 清理注册服务缓存
                            registryServiceMultiCache.clearCache(serviceNodeKey);
                            break;
                        case PUT:
                        default:
                            break;
                    }
                }
            });
        }
    }

    //服务销毁
    @Override
    public void destroy() {
        System.out.println("当前节点下线");
        // 下线节点
        // 遍历本节点所有的 key
        for (String key : localRegisterNodeKeySet) {
            try {
                kvClient.delete(ByteSequence.from(key, StandardCharsets.UTF_8)).get();
            } catch (Exception e) {
                throw new RuntimeException(key + "节点下线失败");
            }
        }

        // 释放资源
        if (kvClient != null) {
            kvClient.close();
        }
        if (client != null) {
            client.close();
        }
    }
}
