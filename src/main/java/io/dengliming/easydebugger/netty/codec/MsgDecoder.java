package io.dengliming.easydebugger.netty.codec;

import io.dengliming.easydebugger.utils.T;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class MsgDecoder extends MessageToMessageDecoder<ByteBuf> {

    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf byteBuf, List<Object> list) {
        byte[] req = new byte[byteBuf.readableBytes()];
        byteBuf.readBytes(req);
        String str = new String(req, StandardCharsets.UTF_8);
        // 判断是否乱码，如果乱码改用16进制
        // TODO 这里暂时没有更好方法同时兼容16进制和字符串
        if (T.isMessyCode(str)) {
            list.add(T.bytesToHex(req));
        } else {
            list.add(str);
        }
    }

}
