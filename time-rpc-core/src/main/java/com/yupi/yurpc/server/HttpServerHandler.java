package com.yupi.yurpc.server;

import com.yupi.yurpc.RpcApplication;
import com.yupi.yurpc.model.RpcRequest;
import com.yupi.yurpc.model.RpcResponse;
import com.yupi.yurpc.registry.LocalRegistry;
import com.yupi.yurpc.serializer.Serializer;
import com.yupi.yurpc.serializer.SerializerFactory;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;

import java.io.IOException;
import java.lang.reflect.Method;


//HTTP请求处理器
//不同的web服务器对应的请求处理器实现方式也不同，比如 Vert.x中是通过实现 Handler<HttpServerRequest> 接口来自定义请求处理器的
//实现了Handler<>接口，数据在到达的时会自动调用handle方法
public class HttpServerHandler implements Handler<HttpServerRequest> {

    @Override
    public void handle(HttpServerRequest request) {
        // 指定序列化器，动态获取序列化器
        final Serializer serializer = SerializerFactory.getInstance(RpcApplication.getRpcConfig().getSerializer());

        // 记录日志
        System.out.println("Received request: " + request.method() + " " + request.uri());

        //serializer是动态获取的，但是在lambda中不能直接使用非final变量，因此这里赋值给一个局部变量，确保在整个作用域内不会被修改
        Serializer finalSerializer = serializer;
        // 异步处理 HTTP 请求
        //类似rabbitmq的监听器，监听到请求后，执行对应的业务逻辑
        request.bodyHandler(body -> {
            //将请求体内容转换为字节数组
            byte[] bytes = body.getBytes();

            RpcRequest rpcRequest = null;
            try {
                //反序列化获取HTTP请求的内容
                rpcRequest = finalSerializer.deserialize(bytes, RpcRequest.class);
            } catch (Exception e) {
                e.printStackTrace();
            }

            // 构造响应结果对象
            RpcResponse rpcResponse = new RpcResponse();
            // 如果请求为 null，直接返回
            if (rpcRequest == null) {
                rpcResponse.setMessage("rpcRequest is null");
                // 响应处理
                doResponse(request, rpcResponse, finalSerializer);
                return;
            }

            try {
                // 获取要调用的服务实现类，通过反射调用
                Class<?> implClass = LocalRegistry.get(rpcRequest.getServiceName());
                //通过反射获取方法
                Method method = implClass.getMethod(rpcRequest.getMethodName(), rpcRequest.getParameterTypes());
                //调用implClass的实例化对象，调用方法
                Object result = method.invoke(implClass.newInstance(), rpcRequest.getArgs());
                //获取响应数据
                rpcResponse.setData(result);
                //获取方法的返回类型
                rpcResponse.setDataType(method.getReturnType());
                rpcResponse.setMessage("ok");
            } catch (Exception e) {
                e.printStackTrace();
                rpcResponse.setMessage(e.getMessage());
                rpcResponse.setException(e);
            }
            // 响应
            doResponse(request, rpcResponse, finalSerializer);
        });
    }

    //响应处理
    void doResponse(HttpServerRequest request, RpcResponse rpcResponse, Serializer serializer) {
        //获取对当前请求的响应对象，并设置接收数据格式为JSON,简化写法
        HttpServerResponse httpServerResponse = request.response()
                .putHeader("content-type", "application/json");

        try {
            //将响应数据（通过序列化器）序列化为字节数组
            byte[] serialized = serializer.serialize(rpcResponse);
            //将字节数组写入响应体中
            httpServerResponse.end(Buffer.buffer(serialized));
        } catch (IOException e) {
            e.printStackTrace();
            httpServerResponse.end(Buffer.buffer());
        }
    }
}
