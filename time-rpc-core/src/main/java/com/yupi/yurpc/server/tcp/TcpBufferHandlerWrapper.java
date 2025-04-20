package com.yupi.yurpc.server.tcp;

import com.yupi.yurpc.protocol.ProtocolConstant;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.parsetools.RecordParser;


//TCP消息处理器包装
//装饰者模式，使用 recordParser 对原有的 buffer 处理能力进行增强
//主要是为了结局tcp半包、粘包问题
public class TcpBufferHandlerWrapper implements Handler<Buffer> {

    //解析器，用于解决半包、粘包问题
    private final RecordParser recordParser;

    // 构造方法，传入buffer处理器
    public TcpBufferHandlerWrapper(Handler<Buffer> bufferHandler) {
        recordParser = initRecordParser(bufferHandler);
    }

    //初始化解析器
    private RecordParser initRecordParser(Handler<Buffer> bufferHandler) {
        //初始化解析器，设置为固定长度模式，先读取消息头
        RecordParser parser = RecordParser.newFixed(ProtocolConstant.MESSAGE_HEADER_LENGTH);

        //设置解析器的输出处理器
        parser.setOutput(new Handler<Buffer>() {
            // 用于记录消息体长度，-1表示尚未读取头部
            int size = -1;
            //创建一个空的缓冲区
            Buffer resultBuffer = Buffer.buffer();

            @Override
            public void handle(Buffer buffer) {
                //判断读取的位置
                if (-1 == size) {
                    //读取body长度,在第13字节的位置
                    size = buffer.getInt(13);
                    //调整解析器模式，准备读取指定长度的消息体
                    parser.fixedSizeMode(size);
                    //写入头信息到结果
                    resultBuffer.appendBuffer(buffer);
                } else {
                    //写入体信息到结果
                    resultBuffer.appendBuffer(buffer);
                    // 已拼接为完整 Buffer，执行处理
                    bufferHandler.handle(resultBuffer);
                    //重置
                    parser.fixedSizeMode(ProtocolConstant.MESSAGE_HEADER_LENGTH);
                    size = -1;
                    resultBuffer = Buffer.buffer();
                }
            }
        });
        return parser;
    }

    @Override
    public void handle(Buffer buffer) {
        recordParser.handle(buffer);
    }
}
