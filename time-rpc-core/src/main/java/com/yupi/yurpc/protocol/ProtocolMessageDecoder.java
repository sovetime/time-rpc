package com.yupi.yurpc.protocol;

import cn.hutool.core.bean.BeanUtil;
import com.yupi.yurpc.model.RpcRequest;
import com.yupi.yurpc.model.RpcResponse;
import com.yupi.yurpc.serializer.Serializer;
import com.yupi.yurpc.serializer.SerializerFactory;
import io.vertx.core.buffer.Buffer;

import java.io.IOException;


//协议消息解码器
public class ProtocolMessageDecoder {

    //解码
    public static ProtocolMessage<?> decode(Buffer buffer) throws IOException {
        //读取消息的header
        ProtocolMessage.Header header = new ProtocolMessage.Header();
        //获取魔数，并进行校验
        byte magic = buffer.getByte(0);
        if (magic != ProtocolConstant.PROTOCOL_MAGIC) {
            throw new RuntimeException("消息 magic 非法");
        }

        //参数的获取和自定义协议消息空间是一样的，读取指定长度的数据来解决buffer和对象转换的问题
        header.setMagic(magic);
        header.setVersion(buffer.getByte(1));
        header.setSerializer(buffer.getByte(2));
        header.setType(buffer.getByte(3));
        header.setStatus(buffer.getByte(4));
        header.setRequestId(buffer.getLong(5));
        header.setBodyLength(buffer.getInt(13));

        //读取指定长度的数据来解决buffer和对象转换的问题
        byte[] bodyBytes = buffer.getBytes(17, 17 + header.getBodyLength());
        //获取序列化协议
        ProtocolMessageSerializerEnum serializerEnum = ProtocolMessageSerializerEnum.getEnumByKey(header.getSerializer());
        if (serializerEnum == null) {
            throw new RuntimeException("序列化消息的协议不存在");
        }
        //根据序列化协议获取接口实例
        Serializer serializer = SerializerFactory.getInstance(serializerEnum.getValue());

        //获取消息类型
        ProtocolMessageTypeEnum messageTypeEnum = ProtocolMessageTypeEnum.getEnumByKey(header.getType());
        if (messageTypeEnum == null) {
            throw new RuntimeException("序列化消息的类型不存在");
        }
        //对不同类型的消息做出不同的处理
        switch (messageTypeEnum) {
            case REQUEST:
                RpcRequest request = serializer.deserialize(bodyBytes, RpcRequest.class);
                return new ProtocolMessage<>(header, request);
            case RESPONSE:
                RpcResponse response = serializer.deserialize(bodyBytes, RpcResponse.class);
                return new ProtocolMessage<>(header, response);
            case HEART_BEAT:
            case OTHERS:
            default:
                throw new RuntimeException("暂不支持该消息类型");
        }
    }

}
